package com.simonpena.marraxo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The status bar and the playback notification show the app's fin, not Media3's default. */
@RunWith(RobolectricTestRunner::class)
class NotificationIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val actions = object : MediaNotification.ActionFactory {
        private fun action(icon: IconCompat, title: CharSequence) = NotificationCompat.Action.Builder(icon, title, null).build()
        override fun createMediaAction(session: MediaSession, icon: IconCompat, title: CharSequence, command: Int) = action(icon, title)
        override fun createCustomAction(session: MediaSession, icon: IconCompat, title: CharSequence, action: String, extras: Bundle) = action(icon, title)
        override fun createCustomActionFromCustomCommandButton(session: MediaSession, button: CommandButton) =
            action(IconCompat.createWithResource(context, button.iconResId), button.displayName)
        override fun createMediaActionPendingIntent(session: MediaSession, command: Long): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
    }

    @Test fun `the playback notification shows the fin`() {
        val session = MediaSession.Builder(context, FakePlayer()).build()
        try {
            val notification = notificationProvider(context)
                .createNotification(session, ImmutableList.of(), actions) {}
                .notification
            assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        } finally {
            session.release()
        }
    }
}
