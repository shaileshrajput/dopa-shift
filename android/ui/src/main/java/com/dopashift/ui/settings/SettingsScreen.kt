package com.dopashift.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.service.LlmProviderType
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * Settings screen implementing Requirements:
 * - 19.1: Profile photo upload (JPEG/PNG 5MB)
 * - 19.7, 19.8: Password change via Keycloak
 * - 19.12, 19.13, 19.14: Accent color picker (12+ predefined + custom hex)
 * - 12.1: Locale selection (en, hi, mr)
 * - 15.1, 15.8, 15.9: LLM provider configuration
 * - 2.8: Interception bypass informational notice
 * - 5.13: Quiet hours config
 *
 * Uses Material3 components and DopaShiftTheme.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToInterceptionRules: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onNavigateToInterceptionRules = onNavigateToInterceptionRules,
        onDisplayNameChange = viewModel::updateDisplayName,
        onSaveDisplayName = viewModel::saveDisplayName,
        onCurrentPasswordChange = viewModel::updateCurrentPassword,
        onNewPasswordChange = viewModel::updateNewPassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onChangePassword = viewModel::changePassword,
        onDismissPasswordSuccess = viewModel::dismissPasswordSuccess,
        onSelectAccentColor = viewModel::selectAccentColor,
        onCustomHexInputChange = viewModel::updateCustomHexInput,
        onApplyCustomHex = viewModel::applyCustomHex,
        onSelectLocale = viewModel::selectLocale,
        onSelectLlmProvider = viewModel::selectLlmProvider,
        onLlmApiKeyChange = viewModel::updateLlmApiKey,
        onValidateLlm = viewModel::validateAndSaveLlmConfig,
        onRemoveLlm = viewModel::removeLlmConfig,
        onQuietHoursStartChange = viewModel::updateQuietHoursStart,
        onQuietHoursEndChange = viewModel::updateQuietHoursEnd,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onNavigateToInterceptionRules: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
    onDismissPasswordSuccess: () -> Unit,
    onSelectAccentColor: (String) -> Unit,
    onCustomHexInputChange: (String) -> Unit,
    onApplyCustomHex: () -> Unit,
    onSelectLocale: (SupportedLocale) -> Unit,
    onSelectLlmProvider: (LlmProviderType) -> Unit,
    onLlmApiKeyChange: (String) -> Unit,
    onValidateLlm: () -> Unit,
    onRemoveLlm: () -> Unit,
    onQuietHoursStartChange: (java.time.LocalTime?) -> Unit,
    onQuietHoursEndChange: (java.time.LocalTime?) -> Unit,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DopaShiftTheme.spacing.gutter)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === Profile Section (Requirement 19.1) ===
        ProfileSection(
            displayName = uiState.displayName,
            email = uiState.email,
            profilePhotoUrl = uiState.profilePhotoUrl,
            displayNameSaved = uiState.displayNameSaved,
            onDisplayNameChange = onDisplayNameChange,
            onSaveDisplayName = onSaveDisplayName
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === Manage App limit (Requirement 2 — App Selection & Time Picker) ===
        InterceptionRulesEntry(onClick = onNavigateToInterceptionRules)

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === Password Change (Requirement 19.7, 19.8) ===
        PasswordChangeSection(
            currentPassword = uiState.currentPassword,
            newPassword = uiState.newPassword,
            confirmPassword = uiState.confirmPassword,
            isInProgress = uiState.passwordChangeInProgress,
            error = uiState.passwordChangeError,
            success = uiState.passwordChangeSuccess,
            onCurrentPasswordChange = onCurrentPasswordChange,
            onNewPasswordChange = onNewPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onChangePassword = onChangePassword,
            onDismissSuccess = onDismissPasswordSuccess
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === Accent Color (Requirement 19.12, 19.13, 19.14) ===
        AccentColorSection(
            selectedColor = uiState.selectedAccentColor,
            customHexInput = uiState.customHexInput,
            customHexError = uiState.customHexError,
            onSelectColor = onSelectAccentColor,
            onCustomHexChange = onCustomHexInputChange,
            onApplyCustomHex = onApplyCustomHex
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === Locale (Requirement 12.1) ===
        LocaleSection(
            selectedLocale = uiState.selectedLocale,
            onSelectLocale = onSelectLocale
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === LLM Provider Configuration (Requirement 15.1, 15.8, 15.9) ===
        LlmConfigSection(
            providerType = uiState.llmProviderType,
            apiKey = uiState.llmApiKey,
            apiKeyMasked = uiState.llmApiKeyMasked,
            isConfigured = uiState.llmIsConfigured,
            isValidated = uiState.llmIsValidated,
            validationInProgress = uiState.llmValidationInProgress,
            validationError = uiState.llmValidationError,
            onSelectProvider = onSelectLlmProvider,
            onApiKeyChange = onLlmApiKeyChange,
            onValidate = onValidateLlm,
            onRemove = onRemoveLlm
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // === Quiet Hours (Requirement 5.13) ===
        QuietHoursSection(
            startTime = uiState.quietHoursStart,
            endTime = uiState.quietHoursEnd,
            onStartTimeChange = onQuietHoursStartChange,
            onEndTimeChange = onQuietHoursEndChange
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section))
    }
}

// =====================================================================================
// Profile Section (Requirement 19.1)
// =====================================================================================

@Composable
private fun ProfileSection(
    displayName: String,
    email: String,
    profilePhotoUrl: String?,
    displayNameSaved: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onSaveDisplayName: () -> Unit
) {
    SectionHeader(title = "Profile")
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo placeholder (Requirement 19.1: JPEG/PNG 5MB)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics { contentDescription = "Profile photo" },
            contentAlignment = Alignment.Center
        ) {
            if (profilePhotoUrl != null) {
                // In production, would use AsyncImage/Coil
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                // Default avatar (Requirement 19.6: initials-based)
                val initials = displayName.split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString("")
                    .ifEmpty { "?" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.gutter))

        Column(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { /* Would open photo picker (JPEG/PNG, 5MB max) */ }) {
                Text("Upload Photo")
            }
            Text(
                text = "JPEG or PNG, max 5 MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text("Display Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

    Text(
        text = email,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

    Button(
        onClick = onSaveDisplayName,
        enabled = displayName.isNotBlank()
    ) {
        Text("Save")
    }

    if (displayNameSaved) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = DopaShiftTheme.colors.successGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.compact))
            Text(
                text = "Display name saved",
                style = MaterialTheme.typography.bodySmall,
                color = DopaShiftTheme.colors.successGreen
            )
        }
    }
}

// =====================================================================================
// Password Change Section (Requirement 19.7, 19.8)
// =====================================================================================

@Composable
private fun PasswordChangeSection(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isInProgress: Boolean,
    error: String?,
    success: Boolean,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
    onDismissSuccess: () -> Unit
) {
    SectionHeader(title = "Change Password")
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Text(
        text = "Password change is managed through Keycloak.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    // Requirement 19.8: Require current password
    OutlinedTextField(
        value = currentPassword,
        onValueChange = onCurrentPasswordChange,
        label = { Text("Current Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

    OutlinedTextField(
        value = newPassword,
        onValueChange = onNewPasswordChange,
        label = { Text("New Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text("Confirm New Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    if (error != null) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (success) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = DopaShiftTheme.colors.successGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.compact))
            Text(
                text = "Password changed successfully",
                style = MaterialTheme.typography.bodySmall,
                color = DopaShiftTheme.colors.successGreen
            )
        }
    }

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Button(
        onClick = onChangePassword,
        enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() &&
            confirmPassword.isNotBlank() && !isInProgress
    ) {
        if (isInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
        }
        Text("Change Password")
    }
}

// =====================================================================================
// Accent Color Section (Requirement 19.12, 19.13, 19.14)
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorSection(
    selectedColor: String?,
    customHexInput: String,
    customHexError: String?,
    onSelectColor: (String) -> Unit,
    onCustomHexChange: (String) -> Unit,
    onApplyCustomHex: () -> Unit
) {
    SectionHeader(title = "Accent Color")
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Text(
        text = "Choose an accent color for primary interactive elements.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    // Predefined color palette (12+ colors per Requirement 19.12)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
    ) {
        PredefinedAccentColors.forEach { colorLong ->
            val argbInt = colorLong.toInt()
            val hexString = "#%06X".format(argbInt and 0xFFFFFF)
            val isSelected = selectedColor?.equals(hexString, ignoreCase = true) == true
            ColorSwatch(
                color = Color(argbInt),
                isSelected = isSelected,
                onClick = { onSelectColor(hexString) }
            )
        }
    }

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

    // Custom hex input (Requirement 19.14)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = customHexInput,
            onValueChange = onCustomHexChange,
            label = { Text("Custom (#RRGGBB)") },
            placeholder = { Text("#FF5733") },
            singleLine = true,
            isError = customHexError != null,
            supportingText = {
                if (customHexError != null) Text(customHexError)
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
        Button(
            onClick = onApplyCustomHex,
            enabled = customHexInput.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                }
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Accent color option" },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// =====================================================================================
// Locale Section (Requirement 12.1)
// =====================================================================================

@Composable
private fun LocaleSection(
    selectedLocale: SupportedLocale,
    onSelectLocale: (SupportedLocale) -> Unit
) {
    SectionHeader(title = "Language")
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Text(
        text = "Select your preferred language for the app interface.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    SupportedLocale.entries.forEach { locale ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectLocale(locale) }
                .padding(vertical = DopaShiftTheme.spacing.base),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = locale == selectedLocale,
                onClick = { onSelectLocale(locale) }
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.stackSm))
            Column {
                Text(
                    text = locale.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = locale.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =====================================================================================
// LLM Configuration Section (Requirement 15.1, 15.8, 15.9)
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmConfigSection(
    providerType: LlmProviderType?,
    apiKey: String,
    apiKeyMasked: String,
    isConfigured: Boolean,
    isValidated: Boolean,
    validationInProgress: Boolean,
    validationError: String?,
    onSelectProvider: (LlmProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    onRemove: () -> Unit
) {
    SectionHeader(title = "LLM Provider")
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Text(
        text = "Configure your AI provider for goal suggestions and video recommendations.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

    // Requirement 15.9: Info about web-search capability
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
            Text(
                text = "ChatGPT and Gemini support live web search for video link suggestions. " +
                    "Claude may fall back to YouTube keyword search for video recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

    if (isConfigured) {
        // Show current config
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(DopaShiftTheme.spacing.gutter)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Provider",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = providerType?.name ?: "Unknown",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = apiKeyMasked,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isValidated) "Validated" else "Not validated",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isValidated) DopaShiftTheme.colors.successGreen
                        else DopaShiftTheme.colors.warningAmber
                    )
                }
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                    Text("Remove Configuration")
                }
            }
        }
    } else {
        // Provider dropdown (Requirement 15.1: OpenAI, Gemini, Claude)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = providerType?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                placeholder = { Text("Select provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                LlmProviderType.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (provider) {
                                    LlmProviderType.OPENAI -> "OpenAI (ChatGPT)"
                                    LlmProviderType.GEMINI -> "Google (Gemini)"
                                    LlmProviderType.CLAUDE -> "Anthropic (Claude)"
                                }
                            )
                        },
                        onClick = {
                            onSelectProvider(provider)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

        // API Key input (Requirement 15.1: max 256 chars)
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("Enter your API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (validationError != null) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

        // Validate button (Requirement 15.8: test call within 10s)
        Button(
            onClick = onValidate,
            enabled = providerType != null && apiKey.isNotBlank() && !validationInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (validationInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
            }
            Text("Validate & Save")
        }
    }
}

// =====================================================================================
// Interception Rules Entry (Requirement 2 — App Selection & Time Picker)
// =====================================================================================

@Composable
private fun InterceptionRulesEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Manage App Limits" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.stackSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rule_authoring_settings_entry),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.rule_authoring_settings_entry_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =====================================================================================
// Quiet Hours Section (Requirement 5.13)
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursSection(
    startTime: java.time.LocalTime?,
    endTime: java.time.LocalTime?,
    onStartTimeChange: (java.time.LocalTime?) -> Unit,
    onEndTimeChange: (java.time.LocalTime?) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    SectionHeader(title = "Quiet Hours")
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Text(
        text = "Reminders and escalations will not fire during quiet hours. " +
            "Suppressed notifications are delivered at the end of the quiet-hours window.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Start",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            OutlinedTextField(
                value = startTime?.let { formatTime(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("22:00") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showStartPicker = true },
                enabled = false
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            TextButton(onClick = { showStartPicker = true }) {
                Text("Set start time")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "End",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            OutlinedTextField(
                value = endTime?.let { formatTime(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("07:00") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEndPicker = true },
                enabled = false
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            TextButton(onClick = { showEndPicker = true }) {
                Text("Set end time")
            }
        }
    }

    if (startTime != null || endTime != null) {
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        TextButton(onClick = {
            onStartTimeChange(null)
            onEndTimeChange(null)
        }) {
            Text("Clear quiet hours")
        }
    }

    // Start time picker dialog
    if (showStartPicker) {
        TimePickerDialog(
            initialHour = startTime?.hour ?: 22,
            initialMinute = startTime?.minute ?: 0,
            title = "Quiet hours start",
            onConfirm = { hour, minute ->
                onStartTimeChange(java.time.LocalTime.of(hour, minute))
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    // End time picker dialog
    if (showEndPicker) {
        TimePickerDialog(
            initialHour = endTime?.hour ?: 7,
            initialMinute = endTime?.minute ?: 0,
            title = "Quiet hours end",
            onConfirm = { hour, minute ->
                onEndTimeChange(java.time.LocalTime.of(hour, minute))
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

/**
 * Material3 TimePicker wrapped in an AlertDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(title) },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

private fun formatTime(time: java.time.LocalTime): String {
    return "%02d:%02d".format(time.hour, time.minute)
}

// =====================================================================================
// Utility Composables
// =====================================================================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}
