package org.jasonagibson.sproutpals.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.jasonagibson.sproutpals.R
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Steps
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Every 15 minutes, even with the app closed: play the pet's life forward, collect steps,
 * and post a reminder if it needs you. Like the 90s beep, but quieter, and never at night.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        var save = store.load()
        Pedometer(applicationContext).readOnce()?.let { save = Steps.apply(save, it) }
        val (withPrizes, arrived) = Route.collect(save)
        save = withPrizes
        Milestones.announce(applicationContext, arrived)
        val now = System.currentTimeMillis() + save.timeOffset
        val pet = PetEngine.advance(save.pet, now, ZoneId.systemDefault(), Random.Default)
        save = save.copy(pet = pet)
        store.save(save)

        val name = pet.species.title
        val text = when {
            pet.ended != null -> "$name has returned to the garden."
            pet.sleeping -> if (pet.lightsOn) "$name is trying to sleep. Lights off?" else null
            pet.sick -> "$name is sick. Medicine time!"
            pet.hunger == 0 -> "$name is hungry!"
            pet.poops > 0 -> "$name made a mess. Time to clean."
            pet.happy == 0 -> "$name is lonely. Come play!"
            else -> null
        }
        if (text != null) notify(applicationContext, text)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "pal"
        private const val WORK = "pal-check"

        fun schedule(context: Context) {
            // 15 minutes is the shortest Android allows. The phone's step chip counts on its own
            // in between, so no steps are missed; this just decides how soon a milestone buzzes.
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        private fun notify(context: Context, text: String) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Your pal", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Sprout Pals")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // One notification slot: a newer reminder replaces the older one instead of piling up.
            nm.notify(1, n)
        }
    }
}
