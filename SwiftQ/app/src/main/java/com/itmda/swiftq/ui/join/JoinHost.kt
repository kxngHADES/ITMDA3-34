package com.itmda.swiftq.ui.join

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.itmda.swiftq.AppContainer
import com.itmda.swiftq.ui.status.JoinedScreen
import com.itmda.swiftq.ui.status.StatusViewModel
import com.itmda.swiftq.ui.status.TicketStatusScreen
import kotlinx.coroutines.launch

object JoinRoutes {
    /** Nested graph that holds the four join steps and the JoinViewModel they share. */
    const val GRAPH = "join_graph"
    const val CLINIC = "join/clinic"
    const val SERVICE = "join/service"
    const val PRIORITY = "join/priority"
    const val CONFIRM = "join/confirm"
    const val JOINED = "joined/{ref}"
    const val STATUS = "status/{ref}"
    fun joined(ref: String) = "joined/$ref"
    fun status(ref: String) = "status/$ref"
}

/**
 * Navigation for the whole join flow: select clinic → select service → priority → confirm → "You're in" → status.
 * After a join succeeds the graph is popped (inclusive), so Back from the status screen can never return to
 * Confirm and submit again. [startLocalRef] is set when the patient already holds a ticket: the flow then opens on its status.
 */
@Composable
fun JoinHost(
    container: AppContainer,
    startLocalRef: String?,
    languageCode: () -> String,
    onExit: () -> Unit,
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    NavHost(
        navController = nav,
        startDestination = if (startLocalRef != null) JoinRoutes.status(startLocalRef) else JoinRoutes.GRAPH,
    ) {
        navigation(startDestination = JoinRoutes.CLINIC, route = JoinRoutes.GRAPH) {
            composable(JoinRoutes.CLINIC) { entry ->
                val vm = sharedVm(nav, entry, container, languageCode)
                HandleJoinEvents(vm, nav)
                val state by vm.clinicList.collectAsStateWithLifecycle()
                ChooseClinicScreen(
                    state = state,
                    onClinic = { clinic -> scope.launch { if (vm.selectClinic(clinic)) nav.navigate(JoinRoutes.SERVICE) } },
                    onRetry = vm::refreshClinics,
                    onBack = onExit,
                )
            }
            composable(JoinRoutes.SERVICE) { entry ->
                val vm = sharedVm(nav, entry, container, languageCode)
                val clinic by vm.selectedClinic.collectAsStateWithLifecycle()
                val state by vm.serviceList.collectAsStateWithLifecycle()
                ChooseServiceScreen(
                    clinicName = clinic?.name.orEmpty(),
                    state = state,
                    onService = { sp -> vm.selectService(sp); nav.navigate(JoinRoutes.PRIORITY) },
                    onRetry = { vm.refreshServicePoints() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(JoinRoutes.PRIORITY) { entry ->
                val vm = sharedVm(nav, entry, container, languageCode)
                PriorityRequestScreen(
                    onPick = { vm.setPriority(it); nav.navigate(JoinRoutes.CONFIRM) },
                    onNone = { vm.setPriority("none"); nav.navigate(JoinRoutes.CONFIRM) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(JoinRoutes.CONFIRM) { entry ->
                val vm = sharedVm(nav, entry, container, languageCode)
                HandleJoinEvents(vm, nav)
                val clinic by vm.selectedClinic.collectAsStateWithLifecycle()
                val service by vm.selectedService.collectAsStateWithLifecycle()
                val form by vm.form.collectAsStateWithLifecycle()
                // Leaving Confirm while the join is being written would drop its result event (nothing below listens),
                // so Back waits until the join has been recorded.
                BackHandler(enabled = form.submitting) { }
                CheckAndJoinScreen(
                    clinicName = clinic?.name.orEmpty(),
                    serviceName = service?.name.orEmpty(),
                    priorityRequested = form.priority != "none",
                    form = form,
                    onName = vm::setName,
                    onPhone = vm::setPhone,
                    onEmail = vm::setEmail,
                    onChannel = vm::setChannel,
                    onJoin = vm::join,
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(JoinRoutes.JOINED, arguments = listOf(navArgument("ref") { type = NavType.StringType })) { entry ->
            val ref = entry.arguments?.getString("ref").orEmpty()
            val vm = statusVm(ref, container)
            LaunchedEffect(vm) { vm.keepFresh() }
            val ui by vm.ui.collectAsStateWithLifecycle()
            // The join steps are gone from the back stack; Back from here leaves the flow, never re-enters it.
            BackHandler(onBack = onExit)
            // "See my ticket" goes to Home, which now shows the ticket (Figma 03).
            JoinedScreen(ui = ui, onSeeTicket = onExit)
        }
        composable(JoinRoutes.STATUS, arguments = listOf(navArgument("ref") { type = NavType.StringType })) { entry ->
            val ref = entry.arguments?.getString("ref").orEmpty()
            val vm = statusVm(ref, container)
            LaunchedEffect(vm) { vm.keepFresh() }
            val ui by vm.ui.collectAsStateWithLifecycle()
            BackHandler(onBack = onExit)
            TicketStatusScreen(
                ui = ui,
                onChooseAnother = {
                    nav.navigate(JoinRoutes.GRAPH) { popUpTo(JoinRoutes.STATUS) { inclusive = true } }
                },
                onHome = onExit,
            )
        }
    }
}

@Composable
private fun sharedVm(
    nav: NavHostController,
    entry: NavBackStackEntry,
    container: AppContainer,
    languageCode: () -> String,
): JoinViewModel {
    // Scoped to the nested graph, so every step sees the same choices.
    val owner = remember(entry) { nav.getBackStackEntry(JoinRoutes.GRAPH) }
    return viewModel(owner, factory = viewModelFactory {
        initializer {
            JoinViewModel(container.clinicRepository, container.joinRepository, container.ticketRepository, languageCode, container::flushNow)
        }
    })
}

@Composable
private fun statusVm(ref: String, container: AppContainer): StatusViewModel =
    viewModel(key = "status-$ref", factory = viewModelFactory {
        initializer { StatusViewModel(ref, container.ticketRepository, container.clinicRepository, container.outboxProcessor) }
    })

@Composable
private fun HandleJoinEvents(vm: JoinViewModel, nav: NavHostController) {
    LaunchedEffect(vm) {
        vm.eventFlow.collect { event ->
            when (event) {
                // popUpTo(join_graph) { inclusive }: the join steps leave the back stack for good.
                is JoinEvent.Joined -> nav.navigate(JoinRoutes.joined(event.localRef)) { popUpTo(JoinRoutes.GRAPH) { inclusive = true } }
                is JoinEvent.OpenExisting -> nav.navigate(JoinRoutes.status(event.localRef)) { popUpTo(JoinRoutes.GRAPH) { inclusive = true } }
            }
        }
    }
}
