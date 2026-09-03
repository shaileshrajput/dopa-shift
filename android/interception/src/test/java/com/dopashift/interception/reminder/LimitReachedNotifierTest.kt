package com.dopashift.interception.reminder

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dopashift.interception.overlay.OverlayService
import com.dopashift.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit test for [LimitReachedNotifier] covering the limit-reached notification's content and
 * its single Intercept_Screen action.
 *
 * Requirement 4.4: within one sampling interval of a breach, post a local notification informing
 * the user the limit was reached, offering a single action to open the Intercept_Screen.
 * Requirement 4.5: the action opens the Intercept_Screen for the associated app (an
 * [OverlayService] intent carrying the triggering package).
 *
 * The notifier posts through the real Android [NotificationManager] and resolves all strings from
 * `strings.xml`, so a real [Context] is required. Robolectric supplies one on the JVM and its
 * shadow [NotificationManager] lets the test inspect the posted notification and its actions
 * (there is no Kotest/Robolectric-free way to read posted-notification content).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LimitReachedNotifierTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var notifier: LimitReachedNotifier

    private val triggeringPackage = "com.social.distracting"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifier = LimitReachedNotifier(context)
    }

    @Test
    fun `notifyLimitReached posts exactly one notification`() {
        notifier.notifyLimitReached(triggeringPackage)

        val posted = shadowOf(notificationManager).allNotifications
        assertEquals("expected exactly one posted notification", 1, posted.size)
    }

    @Test
    fun `notification title and text match the string resources`() {
        notifier.notifyLimitReached(triggeringPackage)

        val notification = shadowOf(notificationManager).allNotifications.single()
        val shadow = shadowOf(notification)

        assertEquals(
            context.getString(R.string.limit_reached_notification_title),
            shadow.contentTitle.toString()
        )
        assertEquals(
            context.getString(R.string.limit_reached_notification_text),
            shadow.contentText.toString()
        )
    }

    @Test
    fun `notification has exactly one action`() {
        notifier.notifyLimitReached(triggeringPackage)

        val notification = shadowOf(notificationManager).allNotifications.single()
        val actions = notification.actions

        assertNotNull("notification must declare an action", actions)
        assertEquals("expected exactly one notification action", 1, actions.size)
    }

    @Test
    fun `notification action label matches the string resource`() {
        notifier.notifyLimitReached(triggeringPackage)

        val notification = shadowOf(notificationManager).allNotifications.single()
        val action = notification.actions.single()

        assertEquals(
            context.getString(R.string.limit_reached_notification_action),
            action.title.toString()
        )
    }

    @Test
    fun `single action PendingIntent targets OverlayService for the associated app`() {
        notifier.notifyLimitReached(triggeringPackage)

        val notification = shadowOf(notificationManager).allNotifications.single()
        val action = notification.actions.single()

        // Inspect the wrapped service intent behind the action's PendingIntent.
        val shadowPendingIntent = shadowOf(action.actionIntent)
        assertTrue(
            "action PendingIntent must start a Service (OverlayService)",
            shadowPendingIntent.isService
        )

        val savedIntent = shadowPendingIntent.savedIntent
        assertEquals(
            "action must target OverlayService",
            OverlayService::class.java.name,
            savedIntent.component?.className
        )

        // The intent must carry the associated app so the Intercept_Screen opens for it (Req 4.5).
        val expectedIntent = OverlayService.createShowIntent(context, triggeringPackage)
        val expectedExtra = expectedIntent.extras
        assertNotNull(expectedExtra)
        expectedExtra!!.keySet().forEach { key ->
            assertEquals(
                "action intent extra '$key' must match createShowIntent",
                expectedExtra.getString(key),
                savedIntent.getStringExtra(key)
            )
        }
    }

    @Test
    fun `content intent also opens the Intercept_Screen for the associated app`() {
        notifier.notifyLimitReached(triggeringPackage)

        val notification = shadowOf(notificationManager).allNotifications.single()

        val shadowContentIntent = shadowOf(notification.contentIntent)
        assertTrue(shadowContentIntent.isService)
        assertEquals(
            OverlayService::class.java.name,
            shadowContentIntent.savedIntent.component?.className
        )
    }
}
