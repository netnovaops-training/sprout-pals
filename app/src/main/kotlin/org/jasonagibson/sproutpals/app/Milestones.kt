package org.jasonagibson.sproutpals.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import org.jasonagibson.sproutpals.R
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Stop

/** Buzz + notification when your walking reaches the next garden shop on the route. */
object Milestones {
    private const val CHANNEL = "milestone"
    /** Three short buzzes: off, buzz, pause, buzz, pause, buzz (milliseconds). */
    private val BUZZ = longArrayOf(0, 180, 120, 180, 120, 350)

    fun announce(context: Context, stops: List<Stop>) {
        if (stops.isEmpty()) return
        buzz(context)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Walking milestones", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = BUZZ
            }
        )
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        for (stop in stops) {
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("You walked to ${stop.name}!")
                .setContentText("${stop.town} · your pal found a ${stop.prize}")
                .setVibrate(BUZZ)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // One notification per shop (id = its place on the route), so each prize stays listed.
            nm.notify(100 + Route.STOPS.indexOf(stop), n)
        }
    }

    private fun buzz(context: Context) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(BUZZ, -1))
    }
}
