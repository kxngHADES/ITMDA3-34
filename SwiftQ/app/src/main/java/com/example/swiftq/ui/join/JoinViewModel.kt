package com.example.swiftq.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftq.data.local.ClinicEntity
import com.example.swiftq.data.local.ServicePointEntity
import com.example.swiftq.data.repository.ClinicRepository
import com.example.swiftq.data.repository.JoinInput
import com.example.swiftq.data.repository.JoinRepository
import com.example.swiftq.data.repository.JoinResult
import com.example.swiftq.data.repository.JoinValidation
import com.example.swiftq.data.repository.NotificationChoice
import com.example.swiftq.data.repository.PriorityChoice
import com.example.swiftq.data.repository.RefreshResult
import com.example.swiftq.data.repository.TicketRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the clinic list shows. Built from Room (the source) plus the outcome of the last refresh. */
data class ClinicListState(
    val clinics: List<ClinicEntity> = emptyList(),
    val loading: Boolean = true,
    val refresh: RefreshResult? = null,
)

data class ServiceListState(
    val servicePoints: List<ServicePointEntity> = emptyList(),
    val loading: Boolean = true,
    val refresh: RefreshResult? = null,
)

data class DetailsForm(
    val priority: String = PriorityChoice.NONE,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val channel: String = NotificationChoice.PUSH,
    val nameError: JoinValidation.NameError? = null,
    val phoneInvalid: Boolean = false,
    val phoneRequired: Boolean = false,
    val emailInvalid: Boolean = false,
    val emailRequired: Boolean = false,
    val submitting: Boolean = false,
)

sealed interface JoinEvent {
    /** Join created (or a duplicate was found): go to the "You're in" screen / status. */
    data class Joined(val localRef: String) : JoinEvent
    data class OpenExisting(val localRef: String) : JoinEvent
}

/**
 * One instance per join flow, shared by every step through the nested nav graph, so the choices
 * survive moving between Select clinic, Select service, Priority and Confirm.
 */
class JoinViewModel(
    private val clinics: ClinicRepository,
    private val joins: JoinRepository,
    private val tickets: TicketRepository,
    private val languageCode: () -> String,
    private val triggerFlush: () -> Unit,
) : ViewModel() {

    // --- Step 1: clinics ------------------------------------------------------------------------------
    private val clinicRefresh = MutableStateFlow<RefreshResult?>(null)
    private val clinicLoading = MutableStateFlow(true)

    val clinicList: StateFlow<ClinicListState> = combine(
        clinics.observeClinics(), clinicLoading, clinicRefresh,
    ) { list, loading, result -> ClinicListState(list, loading, result) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClinicListState())

    private val selectedClinicId = MutableStateFlow<String?>(null)
    val selectedClinic: StateFlow<ClinicEntity?> = combine(selectedClinicId, clinics.observeClinics()) { id, list ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val events = Channel<JoinEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init { refreshClinics() }

    fun refreshClinics() {
        viewModelScope.launch {
            clinicLoading.value = true
            clinicRefresh.value = clinics.refreshClinics()
            clinicLoading.value = false
        }
    }

    /** Returns true when the clinic was accepted and the flow should move on to the service step. */
    suspend fun selectClinic(clinic: ClinicEntity): Boolean {
        // Already in a queue here? Go straight to that ticket instead of offering Join (mirrors T-03.2).
        tickets.findActive()?.takeIf { it.clinicId == clinic.id }?.let {
            events.send(JoinEvent.OpenExisting(it.localRef))
            return false
        }
        selectedClinicId.value = clinic.id
        refreshServicePoints(clinic.id)
        return true
    }

    // --- Step 2: service points -----------------------------------------------------------------------
    private val serviceRefresh = MutableStateFlow<RefreshResult?>(null)
    private val serviceLoading = MutableStateFlow(true)

    val serviceList: StateFlow<ServiceListState> = selectedClinicId.flatMapLatest { id ->
        if (id == null) flowOf(ServiceListState()) else combine(
            clinics.observeServicePoints(id), serviceLoading, serviceRefresh,
        ) { list, loading, result -> ServiceListState(list.filter { it.isActive }, loading, result) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceListState())

    private val selectedServiceId = MutableStateFlow<String?>(null)
    val selectedService: StateFlow<ServicePointEntity?> = combine(selectedServiceId, serviceList) { id, state ->
        state.servicePoints.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshServicePoints(clinicId: String? = selectedClinicId.value) {
        val id = clinicId ?: return
        viewModelScope.launch {
            serviceLoading.value = true
            serviceRefresh.value = clinics.refreshServicePoints(id)
            serviceLoading.value = false
        }
    }

    fun selectService(servicePoint: ServicePointEntity) {
        // A service point with no queue open today cannot be joined.
        if (servicePoint.queueId == null || !servicePoint.isActive) return
        selectedServiceId.value = servicePoint.id
    }

    // --- Steps 3-4: priority and details --------------------------------------------------------------
    private val _form = MutableStateFlow(DetailsForm())
    val form: StateFlow<DetailsForm> = _form

    fun setPriority(value: String) = _form.update { it.copy(priority = value) }
    fun setName(value: String) = _form.update { it.copy(name = value, nameError = null) }
    fun setPhone(value: String) = _form.update { it.copy(phone = value, phoneInvalid = false, phoneRequired = false) }
    fun setEmail(value: String) = _form.update { it.copy(email = value, emailInvalid = false, emailRequired = false) }
    fun setChannel(value: String) = _form.update { it.copy(channel = value, phoneRequired = false, emailRequired = false) }

    // Double-tap guard #1 (cheap, synchronous). Guard #2 is JoinRepository's mutex + duplicate check.
    private val inFlight = AtomicBoolean(false)

    fun join() {
        val clinic = selectedClinic.value ?: return
        val service = selectedService.value ?: return
        val queueId = service.queueId ?: return
        if (!inFlight.compareAndSet(false, true)) return
        _form.update { it.copy(submitting = true) }
        val f = _form.value
        viewModelScope.launch {
            try {
                val result = joins.join(
                    JoinInput(
                        clinicId = clinic.id,
                        servicePointId = service.id,
                        queueId = queueId,
                        displayName = f.name,
                        phone = f.phone,
                        email = f.email,
                        notificationChannel = f.channel,
                        priorityRequest = f.priority,
                        language = languageCode(),
                    ),
                )
                when (result) {
                    is JoinResult.Created -> {
                        triggerFlush() // the "immediate" first attempt (Table 12)
                        events.send(JoinEvent.Joined(result.localRef))
                    }
                    is JoinResult.AlreadyInQueue -> events.send(JoinEvent.OpenExisting(result.localRef))
                    is JoinResult.Invalid -> {
                        _form.update {
                            it.copy(nameError = result.nameError, phoneInvalid = result.phoneInvalid,
                                phoneRequired = result.phoneRequired, emailInvalid = result.emailInvalid,
                                emailRequired = result.emailRequired, submitting = false)
                        }
                        inFlight.set(false)
                    }
                }
            } catch (e: Exception) {
                // A local database failure: let the patient tap again rather than leaving the button dead.
                _form.update { it.copy(submitting = false) }
                inFlight.set(false)
            }
        }
    }
}
