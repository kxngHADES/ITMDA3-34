package com.example.swiftq

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.swiftq.data.local.RoomTransactor
import com.example.swiftq.data.local.SwiftQDatabase
import com.example.swiftq.data.remote.ApiFactory
import com.example.swiftq.data.remote.FirebaseAnonymousAuth
import com.example.swiftq.data.repository.ClinicRepository
import com.example.swiftq.data.repository.DeviceIdProvider
import com.example.swiftq.data.repository.JoinRepository
import com.example.swiftq.data.repository.QueueRepository
import com.example.swiftq.data.repository.TicketRepository
import com.example.swiftq.security.KeystoreCipher
import com.example.swiftq.sync.OutboxProcessor
import com.example.swiftq.sync.OutboxTransport
import com.example.swiftq.sync.RestJoinTransport
import com.example.swiftq.sync.SyncPushTransport
import com.example.swiftq.sync.UuidV4KeyFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency wiring (P1 has no DI framework). One instance lives in [SwiftQApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    private val api = ApiFactory.create(BuildConfig.API_BASE_URL, ApiFactory.httpClient(tokens))

    val deviceId = DeviceIdProvider(database.syncStateDao(), keys)
    val clinicRepository = ClinicRepository(database.clinicDao(), database.servicePointDao(), transactor, api)
    val ticketRepository = TicketRepository(database.ticketDao(), database.outboxDao(), transactor, api)
    val queueRepository = QueueRepository(api)
    val joinRepository = JoinRepository(database.ticketDao(), database.outboxDao(), transactor, cipher, keys)

    private val transport: OutboxTransport =
        if (BuildConfig.SYNC_PUSH_ENABLED) SyncPushTransport(api, cipher) { deviceId.get() } else RestJoinTransport(api, cipher)
    val outboxProcessor = OutboxProcessor(database.outboxDao(), ticketRepository, transport)

    /** Fire-and-forget flush, used right after a join. */
    fun flushNow() {
        scope.launch { outboxProcessor.flush() }
    }

    fun start() {
        scope.launch { outboxProcessor.recoverAfterRestart(); outboxProcessor.flush() }

        // P4: WorkManager replaces both triggers below. Until then, flush when the app comes to the
        // foreground and when a network becomes available, so a patient who was offline gets confirmed on reconnect.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = flushNow()
        })
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = flushNow()
        })
    }
}
