package com.itmda.swiftq.ui.join

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.data.local.ClinicEntity
import com.itmda.swiftq.data.local.ServicePointEntity
import com.itmda.swiftq.data.repository.JoinValidation
import com.itmda.swiftq.data.repository.NotificationChoice
import com.itmda.swiftq.data.repository.PriorityChoice
import com.itmda.swiftq.data.repository.RefreshResult
import com.itmda.swiftq.ui.components.FlowScaffold
import com.itmda.swiftq.ui.components.InfoCard
import com.itmda.swiftq.ui.components.NoticeCard
import com.itmda.swiftq.ui.components.PrimaryButton
import com.itmda.swiftq.ui.components.SettingsChoiceTile
import com.itmda.swiftq.ui.components.StateMessage
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme

/** Figma 04 · Select clinic. Closed or inactive clinics are listed but cannot be picked. */
@Composable
fun ChooseClinicScreen(
    state: ClinicListState,
    onClinic: (ClinicEntity) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = state.clinics.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val offline = state.refresh == RefreshResult.Offline
    FlowScaffold(title = stringResource(R.string.clinic_choose_title), onBack = onBack, modifier = modifier) {
        if (state.clinics.isNotEmpty()) {
            SearchField(query = query, onChange = { query = it })
            if (offline) NoticeCard(R.drawable.ic_cloud_off, stringResource(R.string.join_offline_cached), Modifier.padding(top = 12.dp))
        }
        when {
            state.clinics.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (shown.isEmpty()) Text(stringResource(R.string.clinic_no_match), style = SwiftQTextStyles.Body, color = SwiftQTheme.colors.secondary)
                shown.forEach { clinic -> ClinicRow(clinic, onClinic) }
            }
            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = SwiftQTheme.colors.blue)
                    Text(stringResource(R.string.clinic_loading), style = SwiftQTextStyles.Body, color = SwiftQTheme.colors.secondary)
                }
            }
            state.refresh == RefreshResult.Offline -> StateMessage(R.drawable.ic_cloud_off, stringResource(R.string.clinic_offline_empty_title),
                stringResource(R.string.clinic_offline_empty_body), actionLabel = stringResource(R.string.join_try_again), onAction = onRetry)
            state.refresh == RefreshResult.Updated -> StateMessage(R.drawable.ic_info, stringResource(R.string.clinic_empty_title),
                stringResource(R.string.clinic_empty_body), actionLabel = stringResource(R.string.join_try_again), onAction = onRetry)
            else -> StateMessage(R.drawable.ic_info, stringResource(R.string.clinic_error_title),
                stringResource(R.string.clinic_error_body), actionLabel = stringResource(R.string.join_try_again), onAction = onRetry)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val searchCd = stringResource(R.string.clinic_search_cd)
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.clinic_search_hint), style = SwiftQTextStyles.Body) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        textStyle = SwiftQTextStyles.Body,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = searchCd },
    )
}

@Composable
private fun ClinicRow(clinic: ClinicEntity, onClinic: (ClinicEntity) -> Unit) {
    val joinable = clinic.isOpen && clinic.isActive
    val reason = when {
        !clinic.isActive -> stringResource(R.string.clinic_inactive)
        !clinic.isOpen -> stringResource(R.string.clinic_closed_now)
        else -> stringResource(R.string.clinic_open_now)
    }
    InfoCard(
        iconRes = R.drawable.ic_tab_home,
        title = clinic.name,
        subtitle = reason,
        enabled = joinable,
        onClick = { onClinic(clinic) },
        trailingChevron = joinable,
        contentDescription = stringResource(R.string.clinic_row_cd, clinic.name, reason),
    )
}

/** The step the Figma file does not show: the API needs a service point because each one owns one queue. */
@Composable
fun ChooseServiceScreen(
    clinicName: String,
    state: ServiceListState,
    onService: (ServicePointEntity) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowScaffold(
        title = stringResource(R.string.service_choose_title),
        subtitle = stringResource(R.string.service_choose_subtitle, clinicName),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (state.refresh == RefreshResult.Offline && state.servicePoints.isNotEmpty()) {
            NoticeCard(R.drawable.ic_cloud_off, stringResource(R.string.join_offline_cached), Modifier.padding(bottom = 12.dp))
        }
        when {
            state.servicePoints.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.servicePoints.forEach { sp ->
                    // queue_id == null means no queue is open today: shown, but cannot be selected.
                    val open = sp.queueId != null
                    val sub = if (open) stringResource(R.string.service_open) else stringResource(R.string.service_not_open)
                    InfoCard(
                        iconRes = R.drawable.ic_ticket,
                        title = sp.name,
                        subtitle = sub,
                        enabled = open,
                        onClick = { onService(sp) },
                        trailingChevron = open,
                        contentDescription = stringResource(R.string.clinic_row_cd, sp.name, sub),
                    )
                }
            }
            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SwiftQTheme.colors.blue)
            }
            state.refresh == RefreshResult.Updated -> StateMessage(R.drawable.ic_info, stringResource(R.string.service_empty_title), stringResource(R.string.service_empty_body))
            else -> StateMessage(R.drawable.ic_cloud_off, stringResource(R.string.clinic_error_title), stringResource(R.string.service_error_body),
                actionLabel = stringResource(R.string.join_try_again), onAction = onRetry)
        }
    }
}

/** Figma 05 · Priority. Names appear here only because the patient has to choose; later screens show a neutral icon. */
@Composable
fun PriorityRequestScreen(
    onPick: (String) -> Unit,
    onNone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = mapOf(
        "pregnant" to R.string.priority_pregnant_label,
        "disability" to R.string.priority_disability_label,
        "elderly" to R.string.priority_elderly_label,
        "urgent" to R.string.priority_urgent_label,
    )
    val icons = mapOf(
        "pregnant" to R.drawable.ic_priority_pregnant,
        "disability" to R.drawable.ic_priority_disability,
        "elderly" to R.drawable.ic_priority_elderly,
        "urgent" to R.drawable.ic_priority_urgent,
    )
    FlowScaffold(
        title = stringResource(R.string.priority_request_title),
        subtitle = stringResource(R.string.priority_request_subtitle),
        onBack = onBack,
        modifier = modifier,
        bottom = { PrimaryButton(label = stringResource(R.string.priority_none_button), onClick = onNone, modifier = Modifier.fillMaxWidth()) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            PriorityChoice.ALL.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        val label = stringResource(labels.getValue(key))
                        SettingsChoiceTile(
                            selected = false,
                            onClick = { onPick(key) },
                            cornerRadius = 20.dp,
                            modifier = Modifier.weight(1f).heightIn(min = 156.dp),
                        ) {
                            Box(Modifier.size(72.dp).background(SwiftQTheme.colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(icons.getValue(key)), contentDescription = null, tint = SwiftQTheme.colors.blue, modifier = Modifier.size(44.dp))
                            }
                            Text(label, style = SwiftQTextStyles.Headline, color = SwiftQTheme.colors.label, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.priority_request_note),
            style = SwiftQTextStyles.Footnote,
            color = SwiftQTheme.colors.secondary,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Figma 06 · Check and join. Name, phone and the alert method are all optional. */
@Composable
fun CheckAndJoinScreen(
    clinicName: String,
    serviceName: String,
    priorityRequested: Boolean,
    form: DetailsForm,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onEmail: (String) -> Unit,
    onChannel: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowScaffold(
        title = stringResource(R.string.confirm_check_title),
        onBack = onBack,
        modifier = modifier,
        bottom = {
            PrimaryButton(
                label = stringResource(if (form.submitting) R.string.confirm_joining else R.string.confirm_join_button),
                onClick = onJoin,
                enabled = !form.submitting, // double-tap guard #1; the ViewModel and repository guard again
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(R.drawable.ic_tab_home, clinicName, serviceName)
            // Priority is shown with a neutral icon only, never the category name.
            if (priorityRequested) {
                InfoCard(R.drawable.ic_priority, stringResource(R.string.confirm_priority_card_title), stringResource(R.string.confirm_priority_card_body))
            }
        }

        Text(
            text = stringResource(R.string.confirm_notify_label),
            style = SwiftQTextStyles.Headline,
            color = SwiftQTheme.colors.label,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        val channels = listOf(
            NotificationChoice.PUSH to (R.string.confirm_channel_app to R.drawable.ic_bell),
            NotificationChoice.SMS to (R.string.confirm_channel_sms to R.drawable.ic_chat),
            NotificationChoice.EMAIL to (R.string.confirm_channel_email to R.drawable.ic_mail),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            channels.forEach { (key, resources) ->
                val label = stringResource(resources.first)
                SettingsChoiceTile(
                    selected = form.channel == key,
                    onClick = { onChannel(key) },
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) {
                    Icon(painterResource(resources.second), contentDescription = null, tint = SwiftQTheme.colors.blueText)
                    Text(label, style = SwiftQTextStyles.Subhead, color = SwiftQTheme.colors.label, maxLines = 1)
                }
            }
        }
        if (NotificationChoice.needsPhone(form.channel)) {
            val error = when {
                form.phoneInvalid -> stringResource(R.string.confirm_phone_invalid)
                form.phoneRequired -> stringResource(R.string.confirm_phone_required)
                else -> null
            }
            Field(
                value = form.phone, onChange = onPhone, label = stringResource(R.string.confirm_phone_label),
                helper = error ?: stringResource(R.string.confirm_phone_helper), isError = error != null,
                keyboard = KeyboardType.Phone, placeholder = stringResource(R.string.confirm_phone_hint),
            )
        }
        if (NotificationChoice.needsEmail(form.channel)) {
            val error = when {
                form.emailInvalid -> stringResource(R.string.confirm_email_invalid)
                form.emailRequired -> stringResource(R.string.confirm_email_required)
                else -> null
            }
            Field(
                value = form.email, onChange = onEmail, label = stringResource(R.string.confirm_email_label),
                helper = error ?: stringResource(R.string.confirm_email_helper), isError = error != null,
                keyboard = KeyboardType.Email, placeholder = stringResource(R.string.confirm_email_hint),
            )
        }
        val nameError = when (form.nameError) {
            JoinValidation.NameError.TooLong -> stringResource(R.string.confirm_name_too_long)
            JoinValidation.NameError.BadCharacters -> stringResource(R.string.confirm_name_bad_chars)
            null -> null
        }
        Field(
            value = form.name, onChange = onName, label = stringResource(R.string.confirm_name_label),
            helper = nameError ?: stringResource(R.string.confirm_name_helper), isError = nameError != null,
            keyboard = KeyboardType.Text,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    helper: String,
    isError: Boolean,
    keyboard: KeyboardType,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        isError = isError,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = { Text(helper) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        textStyle = SwiftQTextStyles.Body,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 16.dp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SwiftQTheme.colors.blue,
    unfocusedBorderColor = SwiftQTheme.colors.separator,
    focusedTextColor = SwiftQTheme.colors.label,
    unfocusedTextColor = SwiftQTheme.colors.label,
    focusedLabelColor = SwiftQTheme.colors.blueText,
    unfocusedLabelColor = SwiftQTheme.colors.secondary,
    focusedPlaceholderColor = SwiftQTheme.colors.tertiary,
    unfocusedPlaceholderColor = SwiftQTheme.colors.tertiary,
    focusedSupportingTextColor = SwiftQTheme.colors.secondary,
    unfocusedSupportingTextColor = SwiftQTheme.colors.secondary,
    focusedLeadingIconColor = SwiftQTheme.colors.tertiary,
    unfocusedLeadingIconColor = SwiftQTheme.colors.tertiary,
)
