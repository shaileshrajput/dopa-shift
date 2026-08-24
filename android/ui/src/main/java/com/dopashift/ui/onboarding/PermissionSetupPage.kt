package com.dopashift.ui.onboarding

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * Interactive permission setup page for onboarding (Requirement 2, AC1).
 *
 * Displays the current grant status of PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW,
 * with buttons to launch the respective system Settings screens.
 *
 * When both permissions are granted, the user can proceed to the next onboarding step.
 */
@Composable
internal fun PermissionSetupPage(
    uiState: PermissionSetupUiState,
    onLaunchSettings: (Intent) -> Unit,
    onRefreshPermissions: () -> Unit,
    usageStatsIntent: () -> Intent,
    overlayIntent: () -> Intent,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = DopaShiftTheme.colors.productiveBlue,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section))

        Text(
            text = stringResource(R.string.permission_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        Text(
            text = stringResource(R.string.permission_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section))

        // Usage Stats permission row
        PermissionRow(
            icon = Icons.Outlined.AppBlocking,
            label = stringResource(R.string.permission_usage_stats_label),
            description = stringResource(R.string.permission_usage_stats_hint),
            isGranted = uiState.hasUsageStatsPermission,
            onGrant = { onLaunchSettings(usageStatsIntent()) },
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // Overlay permission row
        PermissionRow(
            icon = Icons.Outlined.Layers,
            label = stringResource(R.string.permission_overlay_label),
            description = stringResource(R.string.permission_overlay_hint),
            isGranted = uiState.hasOverlayPermission,
            onGrant = { onLaunchSettings(overlayIntent()) },
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section))

        // Refresh button for when user returns from Settings
        OutlinedButton(onClick = onRefreshPermissions) {
            Text(text = stringResource(R.string.permission_check_status))
        }

        if (uiState.allGranted) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
            Text(
                text = stringResource(R.string.permission_all_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = DopaShiftTheme.colors.successGreen,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A single permission row showing status and a grant button.
 */
@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
) {
    val statusColor by animateColorAsState(
        targetValue = if (isGranted) DopaShiftTheme.colors.successGreen
        else MaterialTheme.colorScheme.outline,
        label = "permissionStatusColor",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = statusColor,
        )

        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.gutter))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))

        if (!isGranted) {
            Button(onClick = onGrant) {
                Text(text = stringResource(R.string.permission_grant))
            }
        } else {
            Text(
                text = stringResource(R.string.permission_granted_label),
                style = MaterialTheme.typography.labelMedium,
                color = DopaShiftTheme.colors.successGreen,
            )
        }
    }
}
