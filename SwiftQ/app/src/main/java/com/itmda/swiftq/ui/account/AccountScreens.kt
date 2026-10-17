package com.itmda.swiftq.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.data.remote.PatientProfileDto
import com.itmda.swiftq.data.repository.AccountValidation
import com.itmda.swiftq.data.repository.NotificationChoice
import com.itmda.swiftq.data.repository.PriorityChoice
import com.itmda.swiftq.ui.components.FlowScaffold
import com.itmda.swiftq.ui.components.InfoCard
import com.itmda.swiftq.ui.components.NoticeCard
import com.itmda.swiftq.ui.components.PrimaryButton
import com.itmda.swiftq.ui.components.SwiftQTab
import com.itmda.swiftq.ui.components.SwiftQTabBar
import com.itmda.swiftq.ui.screens.defaultLanguageOptions
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Test tags, shared with the UI tests. */
object AccountTags {
    const val EMAIL = "account_email"
    const val PASSWORD = "account_password"
    const val NAME = "account_name"
    const val PHONE = "account_phone"
    const val NEXT = "account_next"
    const val SUBMIT = "account_submit"
}

// --- Figma 08 · Profile (no account) ------------------------------------------------------------

@Composable
fun ProfileNoAccountScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Box(Modifier.size(112.dp).background(colors.blue50, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_tab_user), contentDescription = null, tint = colors.blue, modifier = Modifier.size(56.dp))
            }
            Text(stringResource(R.string.account_save_title), style = SwiftQTextStyles.LargeTitle, color = colors.label,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(stringResource(R.string.account_save_subtitle), style = SwiftQTextStyles.Body, color = colors.secondary, modifier = Modifier.padding(top = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 32.dp)) {
                InfoCard(R.drawable.ic_chat, stringResource(R.string.account_benefit_contact), null)
                InfoCard(R.drawable.ic_check_circle, stringResource(R.string.account_benefit_priority), null)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
            PrimaryButton(stringResource(R.string.account_create_button), onCreateAccount, Modifier.fillMaxWidth().height(64.dp))
            TextAction(stringResource(R.string.account_have_one), onSignIn)
            Text(stringResource(R.string.account_optional), style = SwiftQTextStyles.Footnote, color = colors.tertiary, modifier = Modifier.padding(bottom = 16.dp))
        }
        SwiftQTabBar(selectedTab = SwiftQTab.Profile, onTabSelected = { tab ->
            when (tab) { SwiftQTab.Home -> onHome(); SwiftQTab.Settings -> onSettings(); SwiftQTab.Profile -> Unit }
        })
    }
}

// --- Figma 10 · Profile (signed in) -------------------------------------------------------------

@Composable
fun ProfileSignedInScreen(
    profile: PatientProfileDto,
    onSignOut: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Box(Modifier.size(64.dp).background(colors.blue50, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_tab_user), contentDescription = null, tint = colors.blue, modifier = Modifier.size(32.dp))
                }
                Column {
                    Text(profile.displayName, style = SwiftQTextStyles.Title1, color = colors.label, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_signed_in, profile.email), style = SwiftQTextStyles.Subhead, color = colors.secondary)
                }
            }
            SectionLabel(stringResource(R.string.profile_alerts))
            InfoCard(channelIcon(profile.notificationChannel), channelLabel(profile.notificationChannel),
                profile.phoneLast4?.let { stringResource(R.string.profile_phone_ending, it) })
            SectionLabel(stringResource(R.string.profile_priority))
            InfoCard(R.drawable.ic_priority, priorityText(profile), null)
            SectionLabel(stringResource(R.string.profile_language))
            InfoCard(R.drawable.ic_chat, languageName(profile.language), null)
            Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
            Text(
                stringResource(R.string.profile_sign_out),
                style = SwiftQTextStyles.Headline,
                color = androidx.compose.ui.graphics.Color(0xFFD70015),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onSignOut).padding(vertical = 13.dp),
            )
        }
        SwiftQTabBar(selectedTab = SwiftQTab.Profile, onTabSelected = { tab ->
            when (tab) { SwiftQTab.Home -> onHome(); SwiftQTab.Settings -> onSettings(); SwiftQTab.Profile -> Unit }
        })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = SwiftQTextStyles.Footnote, color = SwiftQTheme.colors.secondary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** The priority states table from the integration guide. */
@Composable
private fun priorityText(profile: PatientProfileDto): String {
    val p = profile.priority
    if (p.category == PriorityChoice.NONE) return stringResource(R.string.profile_priority_none)
    val name = stringResource(priorityLabel(p.category))
    return when {
        p.active && p.validUntil == null -> stringResource(R.string.profile_priority_confirmed, name)
        p.active -> stringResource(R.string.profile_priority_until, name, formatDate(p.validUntil!!))
        p.verified -> stringResource(R.string.profile_priority_expired)
        else -> stringResource(R.string.profile_priority_waiting, name)
    }
}

private fun formatDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(iso.take(10))

private fun languageName(code: String) = defaultLanguageOptions.firstOrNull { it.code == code }?.displayName ?: code

private fun priorityLabel(category: String) = when (category) {
    "pregnant" -> R.string.priority_pregnant_label
    "disability" -> R.string.priority_disability_label
    "elderly" -> R.string.priority_elderly_label
    "urgent" -> R.string.priority_urgent_label
    else -> R.string.profile_priority_none
}

private fun channelIcon(channel: String) = when (channel) {
    NotificationChoice.PUSH -> R.drawable.ic_bell
    NotificationChoice.EMAIL -> R.drawable.ic_mail
    NotificationChoice.NONE -> R.drawable.ic_x_circle
    else -> R.drawable.ic_chat
}

@Composable
private fun channelLabel(channel: String) = stringResource(
    when (channel) {
        NotificationChoice.PUSH -> R.string.register_alert_push
        NotificationChoice.SMS -> R.string.register_alert_sms
        NotificationChoice.EMAIL -> R.string.register_alert_email
        else -> R.string.register_alert_none
    },
)

// --- Figma 09a to 09g · Create account ----------------------------------------------------------

@Composable
fun RegisterScreen(
    ui: RegisterUi,
    languageCode: String,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onName: (String) -> Unit,
    onChannel: (String) -> Unit,
    onPhone: (String) -> Unit,
    onPriority: (String) -> Unit,
    onNext: () -> Unit,
    onEdit: (RegisterStep) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step: (@Composable () -> Unit)? = if (ui.step == RegisterStep.Review) null else { { StepProgress(ui.stepNumber, RegisterUi.STEP_COUNT) } }
    when (ui.step) {
        RegisterStep.Email -> FlowScaffold(
            title = stringResource(R.string.register_email_title),
            subtitle = stringResource(R.string.register_email_subtitle),
            onBack = onBack, aboveTitle = step, modifier = modifier,
            bottom = {
                if (ui.emailInUse != null) {
                    NextButton(stringResource(R.string.register_sign_in_instead), onSignIn, tag = null)
                    TextAction(stringResource(R.string.register_use_other_email)) { onEmail("") }
                } else {
                    NextButton(stringResource(R.string.action_next), onNext)
                    TextAction(stringResource(R.string.register_have_account), onSignIn)
                }
            },
        ) {
            val error = when {
                ui.emailInUse != null -> ui.emailInUse.ifBlank { stringResource(R.string.register_email_in_use) }
                !ui.showErrors -> null
                ui.emailError == AccountValidation.EmailError.Empty -> stringResource(R.string.register_email_empty)
                ui.emailError == AccountValidation.EmailError.Invalid -> stringResource(R.string.register_email_invalid)
                else -> null
            }
            AccountField(ui.email, onEmail, stringResource(R.string.register_email_label), error ?: stringResource(R.string.register_email_helper),
                error != null, KeyboardType.Email, AccountTags.EMAIL, placeholder = stringResource(R.string.register_email_hint), onDone = onNext)
            if (ui.emailInUse != null) NoticeCard(R.drawable.ic_info, stringResource(R.string.register_email_in_use_notice), Modifier.padding(top = 16.dp))
        }

        RegisterStep.Password -> FlowScaffold(
            title = stringResource(R.string.register_password_title),
            subtitle = stringResource(R.string.register_password_subtitle),
            onBack = onBack, aboveTitle = step, modifier = modifier,
            bottom = { NextButton(stringResource(R.string.action_next), onNext) },
        ) {
            var shown by rememberSaveable { mutableStateOf(false) }
            val error = when {
                !ui.showErrors -> null
                ui.passwordError == AccountValidation.PasswordError.TooShort -> stringResource(R.string.register_password_too_short)
                ui.passwordError == AccountValidation.PasswordError.TooLong -> stringResource(R.string.register_password_too_long)
                else -> null
            }
            AccountField(ui.password, onPassword, stringResource(R.string.register_password_label), error, error != null,
                KeyboardType.Password, AccountTags.PASSWORD, password = !shown, onDone = onNext)
            ShowPassword(shown) { shown = !shown }
            PasswordRule(ui.passwordError == null)
        }

        RegisterStep.Name -> FlowScaffold(
            title = stringResource(R.string.register_name_title),
            subtitle = stringResource(R.string.register_name_subtitle),
            onBack = onBack, aboveTitle = step, modifier = modifier,
            bottom = { NextButton(stringResource(R.string.action_next), onNext) },
        ) {
            val error = when {
                !ui.showErrors -> null
                ui.nameError == AccountValidation.NameError.Empty -> stringResource(R.string.register_name_empty)
                ui.nameError == AccountValidation.NameError.TooLong -> stringResource(R.string.confirm_name_too_long)
                ui.nameError == AccountValidation.NameError.BadCharacters -> stringResource(R.string.confirm_name_bad_chars)
                else -> null
            }
            AccountField(ui.name, onName, stringResource(R.string.register_name_label), error ?: stringResource(R.string.register_name_helper),
                error != null, KeyboardType.Text, AccountTags.NAME, onDone = onNext)
        }

        RegisterStep.Alerts -> FlowScaffold(
            title = stringResource(R.string.register_alerts_title),
            subtitle = stringResource(R.string.register_alerts_subtitle),
            onBack = onBack, aboveTitle = step, modifier = modifier,
            bottom = { NextButton(stringResource(R.string.action_next), onNext) },
        ) {
            // App notification, SMS (needs the phone number) or email. No WhatsApp.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
                ChoiceRow(R.drawable.ic_bell, stringResource(R.string.register_alert_push), stringResource(R.string.register_alert_push_sub),
                    ui.channel == NotificationChoice.PUSH) { onChannel(NotificationChoice.PUSH) }
                ChoiceRow(R.drawable.ic_chat, stringResource(R.string.register_alert_sms), stringResource(R.string.register_alert_sms_sub),
                    ui.channel == NotificationChoice.SMS) { onChannel(NotificationChoice.SMS) }
                if (ui.channel == NotificationChoice.SMS) {
                    val error = when {
                        !ui.showErrors -> null
                        ui.phoneError == AccountValidation.PhoneError.Empty -> stringResource(R.string.register_phone_empty)
                        ui.phoneError == AccountValidation.PhoneError.Invalid -> stringResource(R.string.confirm_phone_invalid)
                        else -> null
                    }
                    AccountField(ui.phone, onPhone, stringResource(R.string.register_phone_label), error ?: stringResource(R.string.register_phone_helper),
                        error != null, KeyboardType.Phone, AccountTags.PHONE, placeholder = stringResource(R.string.confirm_phone_hint), onDone = onNext)
                }
                ChoiceRow(R.drawable.ic_mail, stringResource(R.string.register_alert_email), stringResource(R.string.register_alert_email_sub, ui.email.trim()),
                    ui.channel == NotificationChoice.EMAIL) { onChannel(NotificationChoice.EMAIL) }
            }
        }

        RegisterStep.Priority -> FlowScaffold(
            title = stringResource(R.string.register_priority_title),
            subtitle = stringResource(R.string.register_priority_subtitle),
            onBack = onBack, aboveTitle = step, modifier = modifier,
            bottom = { NextButton(stringResource(R.string.action_next), onNext) },
        ) {
            val icons = mapOf("pregnant" to R.drawable.ic_priority_pregnant, "disability" to R.drawable.ic_priority_disability, "elderly" to R.drawable.ic_priority_elderly)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
                ChoiceRow(R.drawable.ic_check_circle, stringResource(R.string.register_priority_none), stringResource(R.string.register_priority_none_sub),
                    ui.priority == PriorityChoice.NONE) { onPriority(PriorityChoice.NONE) }
                // "urgent" is not offered: the API refuses it on an account.
                AccountValidation.REGISTRATION_PRIORITIES.filter { it != PriorityChoice.NONE }.forEach { key ->
                    ChoiceRow(icons.getValue(key), stringResource(priorityLabel(key)), stringResource(R.string.register_priority_check_sub),
                        ui.priority == key) { onPriority(key) }
                }
            }
            Text(stringResource(R.string.register_priority_note), style = SwiftQTextStyles.Footnote, color = SwiftQTheme.colors.secondary, modifier = Modifier.padding(top = 16.dp))
        }

        RegisterStep.Review -> FlowScaffold(
            title = stringResource(R.string.register_review_title),
            subtitle = stringResource(R.string.register_review_subtitle),
            onBack = onBack, modifier = modifier,
            bottom = {
                ProblemNotice(ui.problem)
                val label = when {
                    ui.submitting -> stringResource(R.string.register_creating)
                    ui.problem is FormProblem.RateLimited -> stringResource(R.string.account_try_in, ui.problem.secondsLeft)
                    else -> stringResource(R.string.account_create_button)
                }
                NextButton(label, onSubmit, enabled = !ui.submitting && !ui.rateLimited, tag = AccountTags.SUBMIT)
            },
        ) {
            val alerts = when (ui.channel) {
                NotificationChoice.SMS -> stringResource(R.string.register_sms_value, ui.phone.trim())
                else -> channelLabel(ui.channel)
            }
            val rows = listOf(
                Triple(R.string.register_review_email, ui.email.trim(), RegisterStep.Email),
                Triple(R.string.register_review_name, ui.name.trim(), RegisterStep.Name),
                Triple(R.string.register_review_alerts, alerts, RegisterStep.Alerts),
                Triple(R.string.register_review_priority, stringResource(priorityLabel(ui.priority)), RegisterStep.Priority),
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SwiftQTheme.colors.grouped)) {
                rows.forEach { (labelRes, value, target) ->
                    ReviewRow(stringResource(labelRes), value, onChange = { onEdit(target) })
                    HorizontalDivider(thickness = 1.dp, color = SwiftQTheme.colors.separator)
                }
                ReviewRow(stringResource(R.string.register_review_language), languageName(languageCode), onChange = null)
            }
            Text(stringResource(R.string.register_review_note), style = SwiftQTextStyles.Footnote, color = SwiftQTheme.colors.secondary, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

// --- Figma 22 · Sign in -------------------------------------------------------------------------

@Composable
fun SignInScreen(
    ui: SignInUi,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    FlowScaffold(
        title = stringResource(R.string.sign_in_title),
        subtitle = stringResource(R.string.sign_in_subtitle),
        onBack = onBack, modifier = modifier,
        bottom = {
            ProblemNotice(ui.problem)
            val label = when {
                ui.submitting -> stringResource(R.string.sign_in_signing)
                ui.problem is FormProblem.RateLimited -> stringResource(R.string.account_try_in, ui.problem.secondsLeft)
                else -> stringResource(R.string.sign_in_button)
            }
            NextButton(label, onSubmit, enabled = !ui.submitting && !ui.rateLimited, tag = AccountTags.SUBMIT)
            TextAction(stringResource(R.string.sign_in_create), onCreateAccount)
        },
    ) {
        val emailError = when {
            !ui.showErrors -> null
            ui.emailError == AccountValidation.EmailError.Empty -> stringResource(R.string.register_email_empty)
            ui.emailError == AccountValidation.EmailError.Invalid -> stringResource(R.string.register_email_invalid)
            else -> null
        }
        AccountField(ui.email, onEmail, stringResource(R.string.register_email_label), emailError, emailError != null,
            KeyboardType.Email, AccountTags.EMAIL, placeholder = stringResource(R.string.register_email_hint), imeAction = ImeAction.Next, topPadding = 0)
        val passwordError = ui.wrongCredentials ?: if (ui.showErrors && ui.passwordMissing) stringResource(R.string.sign_in_password_empty) else null
        AccountField(ui.password, onPassword, stringResource(R.string.register_password_label), passwordError, passwordError != null,
            KeyboardType.Password, AccountTags.PASSWORD, password = !shown, onDone = onSubmit)
        ShowPassword(shown) { shown = !shown }
    }
}

// --- Pieces -------------------------------------------------------------------------------------

@Composable
private fun StepProgress(step: Int, total: Int) {
    val colors = SwiftQTheme.colors
    val label = stringResource(R.string.register_step, step, total)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
        Text(label, style = SwiftQTextStyles.Footnote, color = colors.blueText)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < step) colors.blue else colors.separator))
            }
        }
    }
}

@Composable
private fun ColumnScope.NextButton(label: String, onClick: () -> Unit, enabled: Boolean = true, tag: String? = AccountTags.NEXT) {
    PrimaryButton(label = label, onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier))
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        label, style = SwiftQTextStyles.Headline, color = SwiftQTheme.colors.blueText, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp),
    )
}

@Composable
private fun ProblemNotice(problem: FormProblem?) {
    val text = when (problem) {
        null -> return
        is FormProblem.Server -> problem.message
        FormProblem.Offline -> stringResource(R.string.account_offline)
        FormProblem.OpenTicket -> stringResource(R.string.account_open_ticket)
        is FormProblem.RateLimited -> stringResource(R.string.account_rate_limited, problem.secondsLeft)
    }
    NoticeCard(R.drawable.ic_info, text, Modifier.padding(bottom = 12.dp))
}

@Composable
private fun AccountField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    helper: String?,
    isError: Boolean,
    keyboard: KeyboardType,
    tag: String,
    placeholder: String? = null,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    topPadding: Int = 0,
) {
    val colors = SwiftQTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        isError = isError,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = helper?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.blue, unfocusedBorderColor = colors.separator,
            focusedTextColor = colors.label, unfocusedTextColor = colors.label,
            focusedLabelColor = colors.blueText, unfocusedLabelColor = colors.secondary,
            focusedPlaceholderColor = colors.tertiary, unfocusedPlaceholderColor = colors.tertiary,
            focusedSupportingTextColor = colors.secondary, unfocusedSupportingTextColor = colors.secondary,
        ),
        textStyle = SwiftQTextStyles.Body,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = topPadding.dp).testTag(tag),
    )
}

@Composable
private fun ShowPassword(shown: Boolean, onToggle: () -> Unit) {
    val colors = SwiftQTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch, onClick = onToggle).padding(end = 8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_eye), contentDescription = null, tint = colors.blue, modifier = Modifier.size(22.dp))
        Text(stringResource(if (shown) R.string.password_hide else R.string.password_show), style = SwiftQTextStyles.Subhead, color = colors.blueText)
    }
}

@Composable
private fun PasswordRule(ok: Boolean) {
    val colors = SwiftQTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Icon(painterResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_info), contentDescription = null,
            tint = if (ok) androidx.compose.ui.graphics.Color(0xFF248A3D) else colors.tertiary, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.register_password_rule), style = SwiftQTextStyles.Subhead, color = if (ok) colors.label else colors.secondary)
    }
}

/** Figma "Choice row": icon badge, title, subtitle and a radio. Selected uses blue/50 and a 2 dp blue border. */
@Composable
private fun ChoiceRow(iconRes: Int, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SwiftQTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(if (selected) colors.blue50 else colors.grouped)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, colors.blue), shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.size(48.dp).background(colors.surface, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), contentDescription = null, tint = colors.blue, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = SwiftQTextStyles.Headline, color = if (selected) colors.blueText else colors.label)
            Text(subtitle, style = SwiftQTextStyles.Subhead, color = colors.secondary)
        }
        Icon(painterResource(if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected), contentDescription = null,
            tint = if (selected) colors.blue else colors.separator, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ReviewRow(label: String, value: String, onChange: (() -> Unit)?) {
    val changeCd = stringResource(R.string.register_change_cd, label)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .then(if (onChange != null) Modifier.clickable(role = Role.Button, onClick = onChange).semantics { contentDescription = "$changeCd: $value" } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = SwiftQTextStyles.Footnote, color = SwiftQTheme.colors.secondary)
            Text(value, style = SwiftQTextStyles.Headline, color = SwiftQTheme.colors.label)
        }
        if (onChange != null) Text(stringResource(R.string.register_change), style = SwiftQTextStyles.Subhead, color = SwiftQTheme.colors.blueText)
    }
}

@Preview(name = "09a · Create account · Email", widthDp = 393, heightDp = 852)
@Composable
private fun RegisterEmailPreview() {
    SwiftQTheme {
        RegisterScreen(RegisterUi(email = "thandi@example.com"), "en", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "22 · Sign in", widthDp = 393, heightDp = 852)
@Composable
private fun SignInPreview() {
    SwiftQTheme { SignInScreen(SignInUi(email = "thandi@example.com"), {}, {}, {}, {}, {}) }
}
