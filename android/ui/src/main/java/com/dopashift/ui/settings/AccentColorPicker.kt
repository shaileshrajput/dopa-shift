package com.dopashift.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.ui.R
import com.dopashift.ui.theme.A11yDimens
import com.dopashift.ui.theme.DEFAULT_ACCENT_SEED
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.minTouchTarget

/**
 * Standalone Settings accent-color picker (DUX-4.6, DUX-4.7, DUX-5.4).
 *
 * Presents a palette of accent seeds plus a "default" (design-system primary) option. Selecting a
 * swatch persists the seed via [AccentPickerViewModel.apply] so [com.dopashift.ui.theme.DopaShiftTheme]
 * recolors the visible screen live, without an app restart (DUX-4.7).
 *
 * **Contrast guard (DUX-5.4):** a chosen accent that fails WCAG AA against the current theme
 * surface ([MaterialTheme.colorScheme.surface]) is nudged to the nearest compliant shade by
 * [com.dopashift.domain.presentation.ContrastCalculator.nearestCompliant]; the effective (compliant)
 * shade is what gets persisted, and a small indicator explains the adjustment. Accent seeds drive
 * meaningful non-text elements (progress rings, toggles, nav highlights), so AA is evaluated
 * against the 3:1 large/non-text threshold (`largeText = true`).
 *
 * This composable is deliberately self-contained and does not depend on `DopaShiftApp`, the nav
 * graph, or `SettingsScreen`, so it can be dropped into the Settings screen once navigation
 * wiring (task 12.1) lands.
 *
 * @param modifier layout modifier applied to the picker column.
 * @param viewModel coordinates the current seed and the contrast-guarded persistence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPicker(
    modifier: Modifier = Modifier,
    viewModel: AccentPickerViewModel = hiltViewModel(),
) {
    val currentSeed by viewModel.currentSeed.collectAsStateWithLifecycle()
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb().toLong() and 0xFFFFFFFFL

    AccentColorPickerContent(
        currentSeed = currentSeed,
        surfaceArgb = surfaceArgb,
        onSelectSeed = { seed -> viewModel.apply(seed, surfaceArgb) },
        isContrastAdjusted = { seed -> viewModel.isContrastAdjusted(seed, surfaceArgb) },
        effectiveSeed = { seed -> viewModel.effectiveSeed(seed, surfaceArgb) },
        modifier = modifier,
    )
}

/**
 * Stateless content of [AccentColorPicker], separated so it can be previewed/tested without Hilt.
 *
 * @param currentSeed the persisted accent seed ([DEFAULT_ACCENT_SEED] for the palette default).
 * @param surfaceArgb the active theme surface as an ARGB long (used to preview the effective shade).
 * @param onSelectSeed invoked with the raw seed the user picked; the caller applies the guard.
 * @param isContrastAdjusted whether the raw seed would be nudged for AA against the surface.
 * @param effectiveSeed the guarded shade actually applied for a raw seed (for the swatch fill).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentColorPickerContent(
    currentSeed: Long,
    surfaceArgb: Long,
    onSelectSeed: (Long) -> Unit,
    isContrastAdjusted: (Long) -> Boolean,
    effectiveSeed: (Long) -> Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.dux_accent_picker_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        Text(
            text = stringResource(R.string.dux_accent_picker_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm),
        ) {
            // Default (design-system primary) swatch.
            AccentSwatch(
                fillColor = MaterialTheme.colorScheme.primary,
                selected = currentSeed == DEFAULT_ACCENT_SEED,
                contentDescription = stringResource(
                    if (currentSeed == DEFAULT_ACCENT_SEED) {
                        R.string.dux_accent_default_swatch_selected_content_description
                    } else {
                        R.string.dux_accent_default_swatch_content_description
                    },
                ),
                onClick = { onSelectSeed(DEFAULT_ACCENT_SEED) },
            )

            PredefinedAccentColors.forEach { rgb ->
                // PredefinedAccentColors are stored without an alpha byte; force full opacity so
                // the seed maps to a concrete, measurable accent color.
                val seed = 0xFF000000L or rgb
                val hexLabel = "#%06X".format(rgb and 0xFFFFFFL)
                val selected = currentSeed == seed || currentSeed == effectiveSeed(seed)
                AccentSwatch(
                    // Fill the swatch with the effective (guarded) shade so what the user sees is
                    // what the theme will actually apply (DUX-5.4).
                    fillColor = Color((effectiveSeed(seed) and 0xFFFFFFFFL).toInt()),
                    selected = selected,
                    contentDescription = stringResource(
                        if (selected) {
                            R.string.dux_accent_swatch_selected_content_description
                        } else {
                            R.string.dux_accent_swatch_content_description
                        },
                        hexLabel,
                    ),
                    onClick = { onSelectSeed(seed) },
                )
            }
        }

        // Contrast-guard indicator: shown when the current selection was nudged for readability
        // against the active surface (DUX-5.4).
        if (currentSeed != DEFAULT_ACCENT_SEED && isContrastAdjusted(currentSeed)) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(A11yDimens.MinTouchTarget / 3),
                )
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                Text(
                    text = stringResource(R.string.dux_accent_contrast_adjusted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A single selectable accent swatch. The visible dot is 40dp but the interactive area meets the
 * 48dp minimum touch target (DUX-5.1) via [minTouchTarget]. The selection ring and check icon are
 * a non-color-only indicator of the selected state (DUX-5.7). The swatch's semantics are collapsed
 * to a single node carrying [contentDescription] so screen readers announce a meaningful label
 * rather than the raw color (DUX-5.2).
 */
@Composable
private fun AccentSwatch(
    fillColor: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minTouchTarget()
            .selectable(selected = selected, onClick = onClick)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(fillColor)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    // Decorative: the swatch's own contentDescription conveys the selected state.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
