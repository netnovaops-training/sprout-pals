package org.jasonagibson.sproutpals.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jasonagibson.sproutpals.R
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.engine.Stage

/**
 * Home screen widget: the pal's picture, name and stage, hearts and food, and the one thing it
 * needs most. Tapping opens the game. Refreshed by [refresh] (the reminder job and the app).
 */
class PalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { refresh(app, Store(app).load()) }
    }

    companion object {
        fun refresh(context: Context, save: Save) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PalWidget::class.java))
            if (ids.isEmpty()) return
            val pet = save.pet
            val v = RemoteViews(context.packageName, R.layout.widget_pal)

            // the pal's own sprite, scaled up without smoothing so the pixels stay crisp
            val file = if (pet.stage == Stage.EGG) null else
                "char_${pet.species.name.lowercase()}_${when (pet.stage) { Stage.BABY -> "baby"; Stage.KID -> "kid"; Stage.OLD -> "old"; else -> "adult" }}_${if (pet.sick) 6 else 0}"
            @Suppress("DiscouragedApi")
            val id = file?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
            if (id != 0) {
                val opts = BitmapFactory.Options().apply { inScaled = false }
                val small = BitmapFactory.decodeResource(context.resources, id, opts)
                v.setImageViewBitmap(R.id.widget_pal, Bitmap.createScaledBitmap(small, small.width * 3, small.height * 3, false))
            } else {
                v.setImageViewResource(R.id.widget_pal, R.drawable.ic_launcher)
            }

            val name = when {
                pet.stage == Stage.EGG -> "AN EGG!"
                pet.ended != null -> pet.species.title.uppercase()
                else -> "${pet.species.title.uppercase()} · ${pet.stage.name}"
            }
            v.setTextViewText(R.id.widget_name, name)
            v.setTextViewText(
                R.id.widget_hearts,
                if (pet.stage == Stage.EGG || pet.ended != null) "" else
                    "♥".repeat(pet.happy) + "♡".repeat(Rules.MAX_HEARTS - pet.happy) + "  🍲" + pet.hunger + "/" + Rules.MAX_HEARTS,
            )
            v.setTextViewText(R.id.widget_need, need(save))

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, v)
        }

        /** The most important thing to do, in a few words. */
        private fun need(save: Save): String {
            val p = save.pet
            return when {
                p.ended != null -> "Returned to the garden"
                p.stage == Stage.EGG -> "Shake to open!"
                save.awayUntil > System.currentTimeMillis() -> "Visiting ${save.awayHost}"
                p.sleeping && p.lightsOn -> "Sleepy - lights off?"
                p.sleeping -> "Zzz..."
                p.sick -> "Sick - medicine!"
                p.hunger == 0 -> "Hungry!"
                p.poops > 0 -> "Needs a clean-up"
                p.happy == 0 -> "Lonely - come play"
                else -> "All good! ★ ${save.points}"
            }
        }
    }
}
