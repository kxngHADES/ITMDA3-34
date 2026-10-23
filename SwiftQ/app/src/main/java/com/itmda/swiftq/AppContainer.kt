package com.itmda.swiftq

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itmda.swiftq.data.AppSettings
import com.itmda.swiftq.data.ConnectivityMonitor
import com.itmda.swiftq.data.local.RoomTransactor
import com.itmda.swiftq.data.local.SwiftQDatabase
import com.itmda.swiftq.data.remote.ApiFactory
import com.itmda.swiftq.data.remote.FirebaseAnonymousAuth
import com.itmda.swiftq.data.remote.PatientAuthApi
import com.itmda.swiftq.data.remote.PatientSessionAuth
import com.itmda.swiftq.data.remote.PrefsPatientSessionStore
import com.itmda.swiftq.data.repository.AccountRepository
import com.itmda.swiftq.data.repository.ClinicRepository
import com.itmda.swiftq.data.repository.DeviceIdProvider
import com.itmda.swiftq.data.repository.JoinRepository
import com.itmda.swiftq.data.repository.QueueRepository
import com.itmda.swiftq.data.repository.TicketCleaner
import com.itmda.swiftq.data.repository.TicketRepository
import com.itmda.swiftq.feedback.FeedbackPlayer
import com.itmda.swiftq.push.NotificationChannels
import com.itmda.swiftq.push.PushTokenRegistrar
import com.itmda.swiftq.security.KeystoreCipher
import com.itmda.swiftq.sync.OutboxProcessor
import com.itmda.swiftq.sync.OutboxTransport
import com.itmda.swiftq.sync.RestJoinTransport
import com.itmda.swiftq.sync.SyncPushTransport
import com.itmda.swiftq.sync.SyncScheduler
import com.itmda.swiftq.sync.UuidV4KeyFactory
import com.itmda.swiftq.sync.WorkManagerSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Manual dependency wiring (P1 has no DI framework). One instance lives in [SwiftQApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(appContext)
    val connectivity = ConnectivityMonitor(appContext)
    val feedback = FeedbackPlayer(appContext, settings)
    val cipher = KeystoreCipher()
    val keys = UuidV4KeyFactory()
    val database = SwiftQDatabase.create(appContext)
    private val transactor = RoomTransactor(database)

    private val tokens = FirebaseAnonymousAuth(
        context = appContext,
        cipher = cipher,
        httpClient = ApiFactory.httpClient(tokens = null),
        apiKey = BuildConfig.FIREBASE_WEB_API_KEY,
        authBaseUrl = BuildConfig.FIREBASE_AUTH_BASE_URL,
        tokenBaseUrl = BuildConfig.FIREBASE_TOKEN_BASE_URL,
    )
    private val patientSession = PrefsPatientSessionStore(appContext, cipher)
    private val patientAuthApi = PatientAuthApi.create(BuildConfig.API_BASE_URL, ApiFactory.httpClient(tokens = null))
    // The patient token while signed in, otherwise the anonymous one. AuthInterceptor and the authenticator are unchanged.
    private val sessionAuth = PatientSessionAuth(patientSession, patientAuthApi, anonymous = tokens)
    private val api = ApiFactory.create(BuildConfig.API_BASE_URL, ApiFactory.httpClient(sessionAuth))

    val deviceId = DeviceIdProvider(database.syncStateDao(), keys)
    val clinicRepository = ClinicRepository(database.clinicDao(), database.servicePointDao(), transactor, api)
    val ticketRepository = TicketRepository(database.ticketDao(), database.outboxDao(), transactor, api)
    val queueRepository = QueueRepository(api)
    val ticketCleaner = TicketCleaner(database.ticketDao(), database.outboxDao(), database.syncStateDao(), transactor)
    val joinRepository = JoinRepository(database.ticketDao(), database.outboxDao(), transactor, cipher, keys)

    private val transport: OutboxTransport =
        if (BuildConfig.SYNC_PUSH_ENABLED) SyncPushTransport(api, cipher) { deviceId.get() } else RestJoinTransport(api, cipher)
    val outboxProcessor = OutboxProcessor(database.outboxDao(), ticketRepository, transport)

    /** True when this build has Firebase details in `.env`; without them push is simply off. */
    private val pushConfigured = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
        BuildConfig.FIREBASE_SENDER_ID.isNotBlank() && BuildConfig.FIREBASE_WEB_API_KEY.isNotBlank()

    init {
        NotificationChannels.ensure(appContext)
        // No google-services.json (sign-in is REST), so FirebaseApp is started by hand. Must happen before the messaging service runs.
        if (pushConfigured && FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext, FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_WEB_API_KEY).setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
        }
    }

    val pushRegistrar = PushTokenRegistrar(
        api = api,
        syncState = database.syncStateDao(),
        deviceId = { deviceId.get() },
        alertsEnabled = { settings.alertsEnabled },
        currentToken = { if (pushConfigured) fcmToken() else null },
    )

    val accountRepository = AccountRepository(
        authApi = patientAuthApi,
        api = api,
        sessionStore = patientSession,
        tickets = database.ticketDao(),
        beforeIdentityChange = { pushRegistrar.unregister() },
        afterIdentityChange = { pushRegistrar.reRegister() },
    )

    private suspend fun fcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            // task.result throws when the task failed, so only read it on success.
            if (!task.isSuccessful) android.util.Log.w("SwiftQPush", "FCM token request failed", task.exception)
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    /** A push just said something changed: re-read the active ticket now instead of at the next poll. */
    suspend fun refreshActiveTicket() {
        database.ticketDao().findActive()?.let { ticketRepository.refresh(it.localRef) }
    }

    val scheduler: SyncScheduler = WorkManagerSyncScheduler(appContext)

    /**
     * Fire-and-forget flush, used right after a join. Runs in-process for an instant confirmation, and also queues
     * a network-constrained background sync so the join still goes out if the app is killed or signal is missing.
     */
    fun flushNow() {
        scheduler.requestSync()
        scope.launch {
            outboxProcessor.flush()
            scheduler.scheduleRetry(outboxProcessor.nextDueAt())
        }
    }

    fun start() {
        scope.launch { outboxProcessor.recoverAfterRestart(); outboxProcessor.flush(); runCatching { ticketCleaner.runIfDue() }; scheduler.scheduleRetry(outboxProcessor.nextDueAt()) }
        scheduler.ensurePeriodic()
        scope.launch { pushRegistrar.sync() }

        // Background and offline-to-online recovery is WorkManager's job (network-constrained work). Coming to the
        // foreground still flushes right away so a waiting patient does not sit out a retry delay.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { flushNow(); scope.launch { pushRegistrar.sync() } }
        })
    }
}
