package com.dopashift.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * The persistent **Quick-Create floating action button** (AUI-1.7, AUI-4.3).
 *
 * A 56dp ([DopaShiftTokens.TouchTargets.fabSize]) circular FAB filled with Productive Blue
 * ([DopaShiftTokens.Colors.primaryBlue]) and a white plus glyph
 * ([DopaShiftTokens.Colors.onPrimaryBlue]). It exposes [contentDescription] for TalkBack and,
 * at 56dp, comfortably clears the 48dp touch-target minimum (AUI-8.3, Property 8).
 *
 * The FAB is presentation-only here: it simply forwards taps to [onClick]. Wiring that click to the
 * `dashboard-quick-create` surface happens at the navigation layer (task 11.1), keeping this
 * component free of any creation-flow logic (no parallel data path).
 *
 * @param onClick invoked on tap; wired to open the Quick-Create surface by the host.
 * @param contentDescription the localized TalkBack label for the button (e.g. "Quick create").
 * @param modifier optional layout modifier for placement in the host scaffold.
 */
@Composable
fun QuickCreateFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = DopaShiftTokens.Colors.primaryBlue,
        contentColor = DopaShiftTokens.Colors.onPrimaryBlue,
        modifier = modifier
            .size(DopaShiftTokens.TouchTargets.fabSize)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null, // described by the FAB's own semantics above
        )
    }
}
