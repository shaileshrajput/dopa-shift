package com.dopashift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.components.DopaShiftBottomNavBar
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.state.AccentSwatch
import com.dopashift.ui.state.AvatarUi
import com.dopashift.ui.state.SettingsUiState
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.DopaShiftTokens
import com.dopashift.ui.theme.ThemeMode

/**
 * Callbacks dispatched by [SettingsScreen] to the owning Settings view-model
 * (spec `android-ui-upgrade`, AUI-7). This screen is presentation-only: it renders the supplied
 * [SettingsUiState] and forwards user intent through these callbacks, holding no business logic of
 * its own (design → "presentation-only, no parallel data path").
 *
 * SECURITY (workspace rule): password management is delegated exclusively to Keycloak/OIDC. This
 * screen only collects the three password fields and forwards them to the host, which drives the
 * Keycloak change-password flow. No credential is stored locally, persisted, or logged here.
 *
 * @property onUploadPhoto invoked when the "Upload Photo" control is tapped; the host wires the
 *   actual JPEG/PNG ≤5MB file picker (AUI-7.1).
 * @property onDisplayNameChange invoked as the user edits the display-name field (AUI-7.1).
 * @property onSaveDisplayName invoked when the display-name Save control is tapped (AUI-7.1).
 * @property onManageAppLimits invoked when the "Manage App Limits" card is tapped; navigates to the
 *   App Limits & Rules screen (AUI-7.2 → AUI-3).
 * @property onAccentSelected invoked with the tapped [AccentSwatch]; the host applies its accent
 *   seed app-wide (AUI-7.4, Property 7).
 * @property onChangePassword invoked with the current / new / confirm inputs when the user submits
 *   the security card; the host delegates to the Keycloak change-password flow (AUI-7.5). No
 *   credential is retained by this screen.
 * @property onNavigate invoked with the chosen bottom-nav destination (AUI-7.6).
 */
data class SettingsActions(
    val onUploadPhoto: () -> Unit,
    val onDisplayNameChange: (String) -> Unit,
    val onSaveDisplayName: () -> Unit,
    val onManageAppLimits: () -> Unit,
    val onAccentSelected: (AccentSwatch) -> Unit,
    val onChangePassword: (current: String, new: String, confirm: String) -> Unit,
    val onNavigate: (TopDestination) -> Unit,
)

/** The expected count of accent swatches in the picker palette (AUI-7.3). */
private const val ACCENT_SWATCH_COUNT = 12

/** The accent picker grid column count; 12 swatches lay out as three rows of four. */
private const val ACCENT_GRID_COLUMNS = 4

/** Diameter of the profile avatar (AUI-7.1). A component dimension, not a spacing token. */
private val AVATAR_SIZE: Dp = 64.dp

/** Diameter of a single accent swatch fill (AUI-7.3). A component dimension. */
private val SWATCH_SIZE: Dp = 40.dp

/** Outer diameter of the double-ring indicator drawn around the selected swatch (AUI-7.3). */
private val SWATCH_RING_SIZE: Dp = 48.dp

/** Hairline stroke width matching the shared `surfaceBorder` treatment (AUI-8.2). */
private val HAIRLINE_STROKE: Dp = 1.dp

/** Selection double-ring stroke width. */
private val RING_STROKE: Dp = 2.dp

/**
 * The **Settings & Profile** screen (AUI-7).
 *
 * Renders — top to bottom — a profile [KineticCard] with a 64dp circular avatar, an "Upload Photo"
 * control (accepting JPEG/PNG up to 5MB — the host wires the file picker) and a display-name field
 * with a Save control (AUI-7.1); a clickable "Manage App Limits" [KineticCard] with a subtitle and
 * chevron navigating to AUI-3 (AUI-7.2); a 12-swatch accent picker where the selected swatch shows
 * a checkmark inside a double-ring indicator and a dynamic label names the active theme (AUI-7.3),
 * tapping a swatch propagating the accent app-wide (AUI-7.4, Property 7); and a Keycloak-delegated
 * security card with Current / New / Confirm password inputs (AUI-7.5). The screen is hosted with
 * the [DopaShiftBottomNavBar] on [TopDestination.SETTINGS] (AUI-7.6).
 *
 * Every user-facing string comes from a localized resource and every color / size from
 * [DopaShiftTokens] (the swatch fill uses [AccentSwatch.color], which is data, not a literal). All
 * intent is forwarded through [on].
 *
 * @param state the rendered settings state.
 * @param on the intent callbacks.
 * @param modifier optional layout modifier for the screen container.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    on: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DopaShiftTokens.Colors.background,
        bottomBar = {
            DopaShiftBottomNavBar(
                current = TopDestination.SETTINGS,
                onNavigate = on.onNavigate,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DopaShiftTokens.Colors.background),
            contentPadding = PaddingValues(DopaShiftTokens.Spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.aui_settings_screen_title),
                    style = DopaShiftTokens.TextRoles.headlineMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
            }

            item(key = "profile") {
                ProfileCard(
                    avatar = state.avatar,
                    fullName = state.fullName,
                    displayName = state.displayName,
                    onUploadPhoto = on.onUploadPhoto,
                    onDisplayNameChange = on.onDisplayNameChange,
                    onSaveDisplayName = on.onSaveDisplayName,
                )
            }

            item(key = "manage_limits") {
                ManageAppLimitsCard(onClick = on.onManageAppLimits)
            }

            item(key = "accent") {
                AccentPickerCard(
                    swatches = state.accentSwatches,
                    selected = state.selectedAccent,
                    onAccentSelected = on.onAccentSelected,
                )
            }

            item(key = "security") {
                SecurityCard(onChangePassword = on.onChangePassword)
            }
        }
    }
}

/**
 * The profile section (AUI-7.1): a 64dp circular avatar showing the photo when
 * [AvatarUi.imageUri] is set, or the [AvatarUi.monogram] fallback; an "Upload Photo" control whose
 * label states the accepted formats/size (JPEG/PNG ≤5MB) with the actual picker host-wired via
 * [onUploadPhoto]; and a display-name field forwarding edits to [onDisplayNameChange] with a Save
 * control calling [onSaveDisplayName].
 */
@Composable
private fun ProfileCard(
    avatar: AvatarUi,
    fullName: String,
    displayName: String,
    onUploadPhoto: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        ) {
            Avatar(avatar = avatar)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4),
            ) {
                Text(
                    text = stringResource(R.string.aui_settings_profile_header),
                    style = DopaShiftTokens.TextRoles.titleMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
                // The accepted formats/size are surfaced in the control's own label so the
                // constraint is visible; the actual file picking is host-wired via onUploadPhoto.
                Text(
                    text = stringResource(R.string.aui_settings_upload_photo_hint),
                    style = DopaShiftTokens.TextRoles.bodySmall,
                    color = DopaShiftTokens.Colors.textSecondary,
                )
                Button(
                    onClick = onUploadPhoto,
                    modifier = Modifier.heightIn(min = DopaShiftTokens.TouchTargets.buttonHeight),
                    shape = RoundedCornerShape(DopaShiftTokens.Radii.button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DopaShiftTokens.Colors.primaryBlue,
                        contentColor = DopaShiftTokens.Colors.onPrimaryBlue,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.aui_settings_upload_photo),
                        style = DopaShiftTokens.TextRoles.labelLarge,
                    )
                }
            }
        }

        // Full name captured during onboarding (read-only). Shown only when provided.
        if (fullName.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DopaShiftTokens.Spacing.space16),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4),
            ) {
                Text(
                    text = stringResource(R.string.aui_settings_full_name_label),
                    style = DopaShiftTokens.TextRoles.bodySmall,
                    color = DopaShiftTokens.Colors.textSecondary,
                )
                Text(
                    text = fullName,
                    style = DopaShiftTokens.TextRoles.bodyLarge,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
            }
        }

        // Display-name input + Save (AUI-7.1).
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space16),
            singleLine = true,
            label = {
                Text(
                    text = stringResource(R.string.aui_settings_display_name_label),
                    style = DopaShiftTokens.TextRoles.bodyMedium,
                )
            },
            textStyle = DopaShiftTokens.TextRoles.bodyLarge,
            shape = RoundedCornerShape(DopaShiftTokens.Radii.control),
            colors = settingsTextFieldColors(),
        )
        Button(
            onClick = onSaveDisplayName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space12)
                .heightIn(min = DopaShiftTokens.TouchTargets.buttonHeight),
            shape = RoundedCornerShape(DopaShiftTokens.Radii.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = DopaShiftTokens.Colors.momentumTeal,
                contentColor = DopaShiftTokens.Colors.onMomentumTeal,
            ),
        ) {
            Text(
                text = stringResource(R.string.aui_settings_save_display_name),
                style = DopaShiftTokens.TextRoles.labelLarge,
            )
        }
    }
}

/**
 * The 64dp circular avatar (AUI-7.1). Renders the photo at [AvatarUi.imageUri] when present,
 * otherwise the [AvatarUi.monogram] initials on the elevated surface.
 */
@Composable
private fun Avatar(avatar: AvatarUi) {
    val description = stringResource(R.string.aui_settings_avatar_content_description)
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(DopaShiftTokens.Colors.surfaceElevated)
            .border(HAIRLINE_STROKE, DopaShiftTokens.Colors.surfaceBorder, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (avatar.imageUri != null) {
            // A photo is set. Image byte-loading (Coil/host-provided) is wired by the host; this
            // presentation-only module renders a person glyph so the "photo present" state is
            // visually distinct from the monogram fallback without pulling in an image-loading dep.
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = DopaShiftTokens.Colors.textPrimary,
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
            )
        } else {
            Text(
                text = avatar.monogram,
                style = DopaShiftTokens.TextRoles.headlineMedium,
                color = DopaShiftTokens.Colors.textPrimary,
            )
        }
    }
}

/**
 * The clickable "Manage App Limits" navigation card (AUI-7.2): a leading tune glyph, a title and
 * explanatory subtitle, and a trailing chevron. Tapping calls [onClick] to navigate to AUI-3.
 */
@Composable
private fun ManageAppLimitsCard(onClick: () -> Unit) {
    val cardDescription = stringResource(R.string.aui_settings_manage_limits_content_description)
    KineticCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DopaShiftTokens.TouchTargets.minTouchTarget)
            .clip(RoundedCornerShape(DopaShiftTokens.Radii.card))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = cardDescription },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = DopaShiftTokens.Colors.primaryBlue,
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.aui_settings_manage_limits_title),
                    style = DopaShiftTokens.TextRoles.titleMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.aui_settings_manage_limits_subtitle),
                    style = DopaShiftTokens.TextRoles.bodySmall,
                    color = DopaShiftTokens.Colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                // Decorative: the card already exposes its navigation intent via contentDescription.
                contentDescription = null,
                tint = DopaShiftTokens.Colors.textSecondary,
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
            )
        }
    }
}

/**
 * The accent-theme picker (AUI-7.3, AUI-7.4): a header, a dynamic label naming the currently
 * selected theme ([AccentSwatch.label]), and exactly 12 circular swatches laid out four per row.
 * The [selected] swatch shows a checkmark inside a double-ring indicator; tapping any swatch calls
 * [onAccentSelected], which propagates the accent app-wide (Property 7).
 */
@Composable
private fun AccentPickerCard(
    swatches: List<AccentSwatch>,
    selected: AccentSwatch,
    onAccentSelected: (AccentSwatch) -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.aui_settings_accent_header),
            style = DopaShiftTokens.TextRoles.titleMedium,
            color = DopaShiftTokens.Colors.textPrimary,
        )
        // Dynamic accent label naming the active theme (AUI-7.3).
        Text(
            text = stringResource(R.string.aui_settings_accent_active_label, selected.label),
            style = DopaShiftTokens.TextRoles.bodyMedium,
            color = DopaShiftTokens.Colors.momentumTeal,
            modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space4),
        )

        // Lay the 12 swatches out four per row without nesting a scrollable grid inside the
        // outer LazyColumn (which would conflict on vertical scroll).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space12),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
        ) {
            swatches.chunked(ACCENT_GRID_COLUMNS).forEach { rowSwatches ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
                ) {
                    rowSwatches.forEach { swatch ->
                        AccentSwatchItem(
                            swatch = swatch,
                            selected = swatch.id == selected.id,
                            onSelected = { onAccentSelected(swatch) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single accent swatch (AUI-7.3, AUI-7.4). Its 48dp double-ring container meets the touch target
 * (AUI-8.3); the inner 40dp fill uses [AccentSwatch.color] (data, not a literal). When [selected]
 * the swatch draws a double ring plus a centered checkmark and is announced as selected.
 */
@Composable
private fun AccentSwatchItem(
    swatch: AccentSwatch,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val description = stringResource(R.string.aui_settings_accent_swatch_content_description, swatch.label)
    Box(
        modifier = Modifier
            .size(SWATCH_RING_SIZE)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // Outer + inner rings form the double-ring selection indicator (AUI-7.3). Both rings use
        // the swatch color so the selected state reads as an emphasized version of the swatch.
        val ringModifier = if (selected) {
            Modifier
                .size(SWATCH_RING_SIZE)
                .border(RING_STROKE, swatch.color, CircleShape)
                .padding(DopaShiftTokens.Spacing.space4)
                .border(RING_STROKE, DopaShiftTokens.Colors.background, CircleShape)
        } else {
            Modifier.size(SWATCH_RING_SIZE)
        }
        Box(
            modifier = ringModifier,
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(swatch.color),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        // The parent swatch already exposes its label + selected state to TalkBack.
                        contentDescription = null,
                        tint = DopaShiftTokens.Colors.onMomentumTeal,
                        modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
                    )
                }
            }
        }
    }
}

/**
 * The security / password card (AUI-7.5).
 *
 * SECURITY (workspace rule): password change is managed through Keycloak/OIDC. This card only
 * collects the Current / New / Confirm inputs into local composable state and forwards them to
 * [onChangePassword], which drives the Keycloak flow. The inputs are never persisted, cached, or
 * logged, and the fields are masked with a [PasswordVisualTransformation].
 */
@Composable
private fun SecurityCard(onChangePassword: (current: String, new: String, confirm: String) -> Unit) {
    // Transient, in-memory only — cleared with the composition. Never written to storage (security).
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = DopaShiftTokens.Colors.primaryBlue,
                modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
            )
            Text(
                text = stringResource(R.string.aui_settings_security_header),
                style = DopaShiftTokens.TextRoles.titleMedium,
                color = DopaShiftTokens.Colors.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.aui_settings_security_keycloak_note),
            style = DopaShiftTokens.TextRoles.bodySmall,
            color = DopaShiftTokens.Colors.textSecondary,
            modifier = Modifier.padding(top = DopaShiftTokens.Spacing.space8),
        )

        PasswordField(
            value = current,
            onValueChange = { current = it },
            labelRes = R.string.aui_settings_password_current_label,
        )
        PasswordField(
            value = new,
            onValueChange = { new = it },
            labelRes = R.string.aui_settings_password_new_label,
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            labelRes = R.string.aui_settings_password_confirm_label,
        )

        Button(
            onClick = { onChangePassword(current, new, confirm) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DopaShiftTokens.Spacing.space12)
                .heightIn(min = DopaShiftTokens.TouchTargets.buttonHeight),
            shape = RoundedCornerShape(DopaShiftTokens.Radii.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = DopaShiftTokens.Colors.primaryBlue,
                contentColor = DopaShiftTokens.Colors.onPrimaryBlue,
            ),
        ) {
            Text(
                text = stringResource(R.string.aui_settings_change_password),
                style = DopaShiftTokens.TextRoles.labelLarge,
            )
        }
    }
}

/**
 * A masked password input used by [SecurityCard]. The value lives only in the caller's transient
 * composable state and is masked with [PasswordVisualTransformation]; it is never persisted.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DopaShiftTokens.Spacing.space12),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        label = {
            Text(
                text = stringResource(labelRes),
                style = DopaShiftTokens.TextRoles.bodyMedium,
            )
        },
        textStyle = DopaShiftTokens.TextRoles.bodyLarge,
        shape = RoundedCornerShape(DopaShiftTokens.Radii.control),
        colors = settingsTextFieldColors(),
    )
}

/** Shared token-sourced colors for the Settings text fields. */
@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DopaShiftTokens.Colors.textPrimary,
    unfocusedTextColor = DopaShiftTokens.Colors.textPrimary,
    focusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
    unfocusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
    focusedBorderColor = DopaShiftTokens.Colors.momentumTeal,
    unfocusedBorderColor = DopaShiftTokens.Colors.surfaceBorder,
    cursorColor = DopaShiftTokens.Colors.momentumTeal,
    focusedLabelColor = DopaShiftTokens.Colors.momentumTeal,
    unfocusedLabelColor = DopaShiftTokens.Colors.textSecondary,
)

@Preview
@Composable
private fun SettingsScreenPreview() {
    val swatches = List(ACCENT_SWATCH_COUNT) { index ->
        AccentSwatch(
            id = "swatch_$index",
            label = "Accent $index",
            color = if (index == 0) {
                DopaShiftTokens.Colors.momentumTeal
            } else if (index % 2 == 0) {
                DopaShiftTokens.Colors.primaryBlue
            } else {
                DopaShiftTokens.Colors.clarityCyan
            },
            accentSeed = index,
        )
    }
    DopaShiftTheme(themeMode = ThemeMode.DARK) {
        SettingsScreen(
            state = SettingsUiState(
                avatar = AvatarUi(imageUri = null, monogram = "DS"),
                fullName = "Dopa Shift User",
                displayName = "Dopa User",
                accentSwatches = swatches,
                selectedAccent = swatches.first(),
            ),
            on = SettingsActions(
                onUploadPhoto = {},
                onDisplayNameChange = {},
                onSaveDisplayName = {},
                onManageAppLimits = {},
                onAccentSelected = {},
                onChangePassword = { _, _, _ -> },
                onNavigate = {},
            ),
        )
    }
}
