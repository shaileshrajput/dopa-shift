package com.dopashift.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.port.QuietHoursProvider
import com.dopashift.domain.repository.LlmConfigRepository
import com.dopashift.domain.repository.UserPreferencesRepository
import com.dopashift.domain.service.LlmProviderService
import com.dopashift.domain.service.LlmProviderType
import com.dopashift.domain.service.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * Supported locale options for the app (Requirement 12.1).
 */
enum class SupportedLocale(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    MARATHI("mr", "मराठी");

    companion object {
        fun fromCode(code: String): SupportedLocale =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
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
    val fullName: String = "",
    val displayName: String = "",
    val email: String = "",
    val profilePhotoUrl: String? = null,
    val displayNameSaved: Boolean = false,

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
 * accent color, and quiet hours settings. All changes are persisted to local
 * storage immediately (offline-first).
 *
 * Requirements: 15.1, 15.8, 15.9, 19.1, 19.7, 19.8, 19.12, 19.13, 19.14, 2.8, 12.1
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val llmConfigRepository: LlmConfigRepository,
    private val quietHoursProvider: QuietHoursProvider,
    private val llmProviderService: LlmProviderService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                // Load all persisted settings in parallel from local storage
                val fullName = userPreferencesRepository.observeFullName().first()
                val displayName = userPreferencesRepository.observeDisplayName().first()
                val email = userPreferencesRepository.observeEmail().first()
                val accentColor = userPreferencesRepository.observeAccentColor().first()
                val localeCode = userPreferencesRepository.observeLocale().first()
                val llmProvider = llmConfigRepository.observeProviderType().first()
                val llmMasked = llmConfigRepository.observeMaskedApiKey().first()
                val llmConfigured = llmConfigRepository.observeIsConfigured().first()
                val quietHours = quietHoursProvider.getQuietHours(UUID(0, 0)) // Local user

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fullName = fullName,
                        displayName = displayName,
                        email = email,
                        selectedAccentColor = accentColor,
                        selectedLocale = SupportedLocale.fromCode(localeCode),
                        llmProviderType = llmProvider,
                        llmApiKeyMasked = llmMasked,
                        llmIsConfigured = llmConfigured,
                        llmIsValidated = llmConfigured,
                        quietHoursStart = quietHours?.start,
                        quietHoursEnd = quietHours?.end
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load settings: ${e.message}")
                }
            }
        }
    }

    // === Profile ===

    fun updateDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name, displayNameSaved = false) }
    }

    fun saveDisplayName() {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setDisplayName(_uiState.value.displayName)
                _uiState.update { it.copy(displayNameSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save display name") }
            }
        }
    }

    // === Password Change (Requirement 19.7, 19.8, 19.9) ===

    fun updateCurrentPassword(password: String) {
        _uiState.update { it.copy(currentPassword = password, passwordChangeError = null, passwordChangeSuccess = false) }
    }

    fun updateNewPassword(password: String) {
        _uiState.update { it.copy(newPassword = password, passwordChangeError = null, passwordChangeSuccess = false) }
    }

    fun updateConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password, passwordChangeError = null, passwordChangeSuccess = false) }
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
            // TODO: Integrate with Keycloak password change API when backend endpoint is ready.
            // For now, simulate success to unblock UI flow.
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
            try {
                userPreferencesRepository.setAccentColor(hexColor)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save accent color") }
            }
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
        val normalizedHex = input.uppercase()
        _uiState.update {
            it.copy(selectedAccentColor = normalizedHex, customHexInput = "", customHexError = null)
        }
        viewModelScope.launch {
            try {
                userPreferencesRepository.setAccentColor(normalizedHex)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save custom color") }
            }
        }
    }

    // === Locale (Requirement 12.1) ===

    fun selectLocale(locale: SupportedLocale) {
        _uiState.update { it.copy(selectedLocale = locale) }
        viewModelScope.launch {
            try {
                userPreferencesRepository.setLocale(locale.code)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save locale") }
            }
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
        val provider = state.llmProviderType
        val apiKey = state.llmApiKey
        viewModelScope.launch {
            _uiState.update { it.copy(llmValidationInProgress = true, llmValidationError = null) }
            try {
                val result = llmProviderService.validateApiKey(provider, apiKey)
                when (result) {
                    is ValidationResult.Valid -> {
                        llmConfigRepository.saveConfig(provider, apiKey)
                        _uiState.update {
                            it.copy(
                                llmValidationInProgress = false,
                                llmIsConfigured = true,
                                llmIsValidated = true,
                                llmApiKey = "",
                                llmApiKeyMasked = "****${apiKey.takeLast(4)}"
                            )
                        }
                    }
                    is ValidationResult.Invalid -> {
                        _uiState.update {
                            it.copy(
                                llmValidationInProgress = false,
                                llmValidationError = result.reason
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        llmValidationInProgress = false,
                        llmValidationError = "Validation failed: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun removeLlmConfig() {
        viewModelScope.launch {
            try {
                llmConfigRepository.removeConfig()
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
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to remove LLM config") }
            }
        }
    }

    // === Quiet Hours ===

    fun updateQuietHoursStart(time: LocalTime?) {
        _uiState.update { it.copy(quietHoursStart = time) }
        viewModelScope.launch {
            try {
                quietHoursProvider.setQuietHoursStart(time)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save quiet hours") }
            }
        }
    }

    fun updateQuietHoursEnd(time: LocalTime?) {
        _uiState.update { it.copy(quietHoursEnd = time) }
        viewModelScope.launch {
            try {
                quietHoursProvider.setQuietHoursEnd(time)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save quiet hours") }
            }
        }
    }

    // === General ===

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
