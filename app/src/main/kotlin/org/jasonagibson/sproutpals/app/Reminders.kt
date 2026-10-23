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
import org.jasonagibson.sproutpals.engine.Alerts
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Visitors
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Steps
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Every 15 minutes, even with the app closed: play the pet's life and the garden forward,
 * collect steps, and post a notification for anything new that needs you (see [Alerts]):
 * lights, sickness, hunger, mess, loneliness, birthday eve, ripe or thirsty crops, weather.
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
        val zone = ZoneId.systemDefault()
        // the garden's hours (creatures, weather) and the pet's minutes, played forward
        val (visited, news) = Visitors.catchUp(save, now, zone, Random.Default)
        save = visited.copy(pet = PetEngine.advance(visited.pet, now, zone, Random.Default))

        // each alert once, until it clears; garden news as it happens
        val eve = save.pet.hatchedAt?.let { Rules.birthdaySoon(now - it) } ?: false
        val (fresh, keys) = Alerts.fresh(save, now, eve)
        save = save.copy(notified = keys)
        store.save(save)
        fresh.forEach { notify(applicationContext, it.key, it.text) }
        news.takeLast(2).forEach { notify(applicationContext, "news:$it", it.replace('\n', ' ').lowercase().replaceFirstChar { c -> c.uppercase() }) }
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

        /** Sent once when notifications get switched on, to show they work. */
        fun hello(context: Context) = notify(context, "hello", "Notifications are on! Your pal will call you when it needs you.")

        private fun notify(context: Context, key: String, text: String) {
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
            // One slot per kind of alert: a repeat replaces its own, different kinds sit side by side.
            nm.notify(key.substringBefore(':').hashCode(), n)
        }
    }
}
