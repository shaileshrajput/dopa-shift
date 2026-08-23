package com.dopashift.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.service.LlmProviderType
import com.dopashift.domain.service.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Supported locale options for the app (Requirement 12.1).
 */
enum class SupportedLocale(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    MARATHI("mr", "मराठी")
}

/**
 * Predefined accent colors for the color picker (Requirement 19.12).
 * At least 12 predefined + custom hex input.
 */
val PredefinedAccentColors: List<Long> = listOf(
    0xFF0066FF, // Productive Blue (default)
    0xFF2DD4BF, // Momentum Teal
    0xFF6366F1, // Focus Indigo
    0xFF10B981, // Green
    0xFFF59E0B, // Amber
    0xFFE11D48, // Rose
    0xFF8B5CF6, // Violet
    0xFFEC4899, // Pink
    0xFF14B8A6, // Teal
    0xFFF97316, // Orange
    0xFF06B6D4, // Cyan
    0xFF84CC16, // Lime
    0xFF6D28D9, // Purple
)

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    // Profile
    val displayName: String = "",
    val email: String = "",
    val profilePhotoUrl: String? = null,

    // Password change
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordChangeInProgress: Boolean = false,
    val passwordChangeError: String? = null,
    val passwordChangeSuccess: Boolean = false,

    // Accent color (Requirement 19.12–19.17)
    val selectedAccentColor: String? = null, // Hex color string (#RRGGBB) or null for default
    val customHexInput: String = "",
    val customHexError: String? = null,

    // Locale (Requirement 12.1)
    val selectedLocale: SupportedLocale = SupportedLocale.ENGLISH,

    // LLM config (Requirement 15.1, 15.8, 15.9)
    val llmProviderType: LlmProviderType? = null,
    val llmApiKey: String = "",
    val llmApiKeyMasked: String = "",
    val llmIsConfigured: Boolean = false,
    val llmIsValidated: Boolean = false,
    val llmValidationInProgress: Boolean = false,
    val llmValidationError: String? = null,

    // Quiet hours (Requirement 5.13)
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null,

    // General
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * ViewModel for the Settings screen.
 *
 * Manages profile customization, LLM provider configuration, locale selection,
 * accent color, and quiet hours settings.
 *
 * Requirements: 15.1, 15.8, 15.9, 19.1, 19.7, 19.8, 19.12, 19.13, 19.14, 2.8, 12.1
 */
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // In production, this would fetch from repository/API.
            // For now, mark as loaded with defaults.
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // === Profile ===

    fun updateDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun saveDisplayName() {
        viewModelScope.launch {
            // Would call profile update API
        }
    }

    // === Password Change (Requirement 19.7, 19.8, 19.9) ===

    fun updateCurrentPassword(password: String) {
        _uiState.update { it.copy(currentPassword = password, passwordChangeError = null) }
    }

    fun updateNewPassword(password: String) {
        _uiState.update { it.copy(newPassword = password, passwordChangeError = null) }
    }

    fun updateConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password, passwordChangeError = null) }
    }

    fun changePassword() {
        val state = _uiState.value
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(passwordChangeError = "Passwords do not match") }
            return
        }
        if (state.newPassword.length < 8) {
            _uiState.update { it.copy(passwordChangeError = "Password must be at least 8 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(passwordChangeInProgress = true, passwordChangeError = null) }
            // Would call Keycloak password change API
            _uiState.update {
                it.copy(
                    passwordChangeInProgress = false,
                    passwordChangeSuccess = true,
                    currentPassword = "",
                    newPassword = "",
                    confirmPassword = ""
                )
            }
        }
    }

    fun dismissPasswordSuccess() {
        _uiState.update { it.copy(passwordChangeSuccess = false) }
    }

    // === Accent Color (Requirement 19.12–19.14) ===

    fun selectAccentColor(hexColor: String) {
        _uiState.update { it.copy(selectedAccentColor = hexColor, customHexInput = "", customHexError = null) }
        viewModelScope.launch {
            // Would persist to profile
        }
    }

    fun updateCustomHexInput(input: String) {
        _uiState.update { it.copy(customHexInput = input, customHexError = null) }
    }

    fun applyCustomHex() {
        val input = _uiState.value.customHexInput.trim()
        val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")
        if (!hexPattern.matches(input)) {
            _uiState.update { it.copy(customHexError = "Invalid hex color. Use format #RRGGBB") }
            return
        }
        _uiState.update {
            it.copy(selectedAccentColor = input.uppercase(), customHexInput = "", customHexError = null)
        }
        viewModelScope.launch {
            // Would persist to profile
        }
    }

    // === Locale (Requirement 12.1) ===

    fun selectLocale(locale: SupportedLocale) {
        _uiState.update { it.copy(selectedLocale = locale) }
        viewModelScope.launch {
            // Would persist and trigger locale change
        }
    }

    // === LLM Configuration (Requirement 15.1, 15.8) ===

    fun selectLlmProvider(provider: LlmProviderType) {
        _uiState.update { it.copy(llmProviderType = provider, llmValidationError = null) }
    }

    fun updateLlmApiKey(apiKey: String) {
        _uiState.update { it.copy(llmApiKey = apiKey, llmValidationError = null) }
    }

    fun validateAndSaveLlmConfig() {
        val state = _uiState.value
        if (state.llmProviderType == null) {
            _uiState.update { it.copy(llmValidationError = "Please select a provider") }
            return
        }
        if (state.llmApiKey.isBlank()) {
            _uiState.update { it.copy(llmValidationError = "API key is required") }
            return
        }
        if (state.llmApiKey.length > 256) {
            _uiState.update { it.copy(llmValidationError = "API key must be at most 256 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(llmValidationInProgress = true, llmValidationError = null) }
            // Would call validate API endpoint (Requirement 15.8: 10s timeout)
            // Simulating success for now
            _uiState.update {
                it.copy(
                    llmValidationInProgress = false,
                    llmIsConfigured = true,
                    llmIsValidated = true,
                    llmApiKey = "",
                    llmApiKeyMasked = "****${state.llmApiKey.takeLast(4)}"
                )
            }
        }
    }

    fun removeLlmConfig() {
        _uiState.update {
            it.copy(
                llmProviderType = null,
                llmApiKey = "",
                llmApiKeyMasked = "",
                llmIsConfigured = false,
                llmIsValidated = false,
                llmValidationError = null
            )
        }
        viewModelScope.launch {
            // Would call delete LLM config API
        }
    }

    // === Quiet Hours ===

    fun updateQuietHoursStart(time: LocalTime?) {
        _uiState.update { it.copy(quietHoursStart = time) }
        viewModelScope.launch {
            // Would persist to profile
        }
    }

    fun updateQuietHoursEnd(time: LocalTime?) {
        _uiState.update { it.copy(quietHoursEnd = time) }
        viewModelScope.launch {
            // Would persist to profile
        }
    }

    // === General ===

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
