package com.dopashift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.theme.DopaShiftPillShape
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * The 1dp hairline stroke width that matches the `surfaceBorder` treatment shared by the Kinetic
 * containers (AUI-8.2). Named here so the width is referenced by name rather than an inline literal.
 */
private val HairlineStroke: Dp = 1.dp

/**
 * The global bottom navigation bar shared by the Home, Goals, Analytics, and Settings screens
 * (AUI-7.6). It always renders **exactly four** destinations — the four [TopDestination.entries]
 * (`HOME`, `GOALS`, `ANALYTICS`, `SETTINGS`) — so the same top-level affordance is present on every
 * screen that hosts it (AUI-1 / AUI-4 / AUI-6 / AUI-7).
 *
 * The [current] destination is highlighted with a [DopaShiftTokens.Colors.momentumTeal] pill
 * container behind its icon + label, and its title is drawn **bold** in the on-teal foreground
 * ([DopaShiftTokens.Colors.onMomentumTeal]); every inactive destination sits transparent with a
 * secondary-tone icon and label ([DopaShiftTokens.Colors.textSecondary]). Selecting a destination
 * invokes [onNavigate] with that [TopDestination]; the caller owns the actual navigation.
 *
 * Each item is a [selectable] inside a [selectableGroup] so TalkBack announces the bar as a single
 * tab/radio set and reads each destination's localized label and selected state. Every item fills
 * the full [DopaShiftTokens.TouchTargets.minTouchTarget] (48dp) height so it meets the accessibility
 * touch target (AUI-8.3, Property 8). Labels come exclusively from the destinations' localized
 * string resources ([TopDestination.labelResId]), so no user-facing string is hardcoded.
 *
 * The bar sits on the [DopaShiftTokens.Colors.surface] container with a 1dp
 * [DopaShiftTokens.Colors.surfaceBorder] hairline top edge and reserves the system navigation-bar
 * inset so it draws correctly edge-to-edge.
 *
 * @param current the currently active [TopDestination], rendered with the teal pill + bold title.
 * @param onNavigate invoked with the tapped [TopDestination] when the user selects a destination.
 * @param modifier optional layout modifier for the bar container.
 */
@Composable
fun DopaShiftBottomNavBar(
    current: TopDestination,
    onNavigate: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DopaShiftTokens.Colors.surface)
            .border(HairlineStroke, DopaShiftTokens.Colors.surfaceBorder)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                horizontal = DopaShiftTokens.Spacing.space8,
                vertical = DopaShiftTokens.Spacing.space8,
            )
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopDestination.entries.forEach { destination ->
            NavBarItem(
                destination = destination,
                selected = destination == current,
                onSelect = { onNavigate(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single destination inside [DopaShiftBottomNavBar].
 *
 * When [selected], the icon + label are wrapped in a [DopaShiftTokens.Colors.momentumTeal]
 * [DopaShiftPillShape] pill and the label is bold in the on-teal foreground; otherwise the item is
 * transparent with secondary-tone content. The whole item is a [selectable] (role
 * [Role.Tab]) with the localized label as its accessible name, and it fills the 48dp minimum touch
 * target height (AUI-8.3).
 */
@Composable
private fun NavBarItem(
    destination: TopDestination,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(id = destination.labelResId)
    // The active-pill fill and its on-color read the theme's reward accent role so a Settings
    // accent selection recolors the highlighted destination app-wide (AUI-7.4); the default seed
    // resolves this back to Momentum Teal. Inactive items keep the secondary-tone label.
    val pillColor = MaterialTheme.colorScheme.secondary
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        DopaShiftTokens.Colors.textSecondary
    }
    Row(
        modifier = modifier
            .heightIn(min = DopaShiftTokens.TouchTargets.minTouchTarget)
            .clip(DopaShiftPillShape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onSelect,
            )
            .background(
                color = if (selected) pillColor else Color.Transparent,
                shape = DopaShiftPillShape,
            )
            .padding(
                horizontal = DopaShiftTokens.Spacing.space12,
                vertical = DopaShiftTokens.Spacing.space8,
            )
            .wrapContentWidth(Alignment.CenterHorizontally)
            // The destination's localized label is attached to the tab itself so TalkBack always
            // announces the destination — including inactive tabs, which hide their text label
            // (AUI-8.5). `selectable` only sets the Selected/Role state, not an accessible name; the
            // visible Text child (shown for the active tab) is left intact and independently
            // addressable because this node does not merge its descendants.
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            // The tab row exposes `label` as its accessible name via semantics above; the icon is
            // decorative here, so it carries no separate content description to avoid double reads.
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
        )
        if (selected) {
            Text(
                text = label,
                style = DopaShiftTokens.TextRoles.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
            )
        }
    }
}
