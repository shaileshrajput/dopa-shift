package com.dopashift.app.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.dopashift.app.R
import com.dopashift.interception.InterceptionPermissionHelper

/**
 * Wraps the app content and enforces that the two special-access permissions required for
 * screen-time interception — PACKAGE_USAGE_STATS (usage access) and SYSTEM_ALERT_WINDOW (overlay)
 * — are granted.
 *
 * The status is re-checked in the background on **every** app open / focus (each `ON_RESUME`
 * lifecycle event), not just once at startup. This covers the case where the user revokes a
 * permission from system Settings while the app is backgrounded, or returns to the app after
 * granting a permission. When either permission is missing, a blocking [AlertDialog] is shown
 * with a "Grant" action per permission that opens the relevant system Settings screen. Because the
 * status is re-read on the resume that follows returning from Settings, the dialog updates its
 * rows and dismisses itself automatically once both permissions are granted.
 *
 * Neither of these permissions can be requested through a runtime permission dialog — they are
 * only togglable from dedicated Settings screens — so this gate is the in-app surface that drives
 * the user there.
 *
 * @param permissionHelper the shared checker/intent-builder from the interception module.
 * @param enabled when false, the blocking dialog is suppressed (e.g. during first-run onboarding,
 *   which drives its own permission handoff); status is still tracked so the gate engages as soon
 *   as [enabled] becomes true.
 * @param content the app UI to render behind the (optional) dialog.
 */
@Composable
fun RequiredPermissionsGate(
    permissionHelper: InterceptionPermissionHelper,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Holds the latest permission status. Recomputed on every ON_RESUME so the dialog reflects
    // grants/revocations that happened while the app was in the background.
    var status by remember {
        mutableStateOf(permissionHelper.checkPermissionStatus(context))
    }

    // Re-check on every resume (app open / regains focus). Also refresh on any lifecycle event so
    // returning from the Settings screen (which resumes this activity) picks up a fresh status.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = permissionHelper.checkPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    content()

    // The gate is suppressed while [enabled] is false (e.g. during first-run onboarding, which has
    // its own permission-handoff step) so the blocking dialog does not cover the guided flow.
    if (enabled && !status.allGranted) {
        RequiredPermissionsDialog(
            status = status,
            onGrantUsageStats = {
                launchSettings(context, permissionHelper.createUsageStatsSettingsIntent())
            },
            onGrantOverlay = {
                launchSettings(context, permissionHelper.createOverlaySettingsIntent(context))
            },
            onRefresh = { status = permissionHelper.checkPermissionStatus(context) },
        )
    }
}

private fun launchSettings(context: Context, intent: Intent) {
    context.startActivity(intent)
}

/**
 * Non-dismissable dialog listing each required permission with its live grant status and a "Grant"
 * button that opens the corresponding Settings screen. It cannot be dismissed by tapping outside or
 * pressing back, because interception cannot function without these permissions; it clears itself
 * automatically once [RequiredPermissionsGate] observes that both are granted.
 */
@Composable
private fun RequiredPermissionsDialog(
    status: InterceptionPermissionHelper.PermissionStatus,
    onGrantUsageStats: () -> Unit,
    onGrantOverlay: () -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissable: permissions are mandatory for interception. */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        },
        title = { Text(text = stringResource(R.string.permission_gate_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.permission_gate_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                PermissionGateRow(
                    icon = Icons.Default.Warning,
                    label = stringResource(R.string.permission_usage_stats_title),
                    grantedLabel = stringResource(R.string.permission_gate_usage_granted),
                    isGranted = status.hasUsageStats,
                    onGrant = onGrantUsageStats,
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionGateRow(
                    icon = Icons.Default.Warning,
                    label = stringResource(R.string.permission_overlay_title),
                    grantedLabel = stringResource(R.string.permission_gate_overlay_granted),
                    isGranted = status.hasOverlay,
                    onGrant = onGrantOverlay,
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onRefresh) {
                Text(text = stringResource(R.string.action_retry))
            }
        },
    )
}

/**
 * A single permission row inside the gate dialog: an icon, the permission label, and either a
 * "granted" indicator or a "Grant" button that opens Settings.
 */
@Composable
private fun PermissionGateRow(
    icon: ImageVector,
    label: String,
    grantedLabel: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Text(
                text = grantedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(
                onClick = onGrant,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp,
                ),
            ) {
                Text(text = stringResource(R.string.permission_grant))
            }
        }
    }
}
