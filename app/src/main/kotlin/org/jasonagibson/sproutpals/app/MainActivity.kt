package org.jasonagibson.sproutpals.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jasonagibson.sproutpals.BuildConfig
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.ui.PalScreen
import org.jasonagibson.sproutpals.ui.TitleScreen

class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var pedometer: Pedometer
    private lateinit var shaker: ShakeDetector
    private var game by mutableStateOf<Game?>(null)
    /** The opening scene shows once per app launch, until tapped. */
    private var showTitle by mutableStateOf(true)
    private val updater = Updater()
    private val chip = Chiptune()

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startPedometer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(applicationContext)
        CharacterArt.init(resources)
        pedometer = Pedometer(applicationContext)
        // Shaking only matters on the pet screen (not the title), and only while it's an egg.
        shaker = ShakeDetector(applicationContext) { runOnUiThread { if (!showTitle) game?.onShake() } }
        ReminderWorker.schedule(applicationContext)

        // Steps (pedometer) and reminders (notifications) each need a yes from the person, once.
        askPermissions.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS))

        setContent {
            if (showTitle) { TitleScreen(onStart = { showTitle = false }); return@setContent }
            val g = game ?: return@setContent
            LaunchedEffect(g) {
                while (true) {
                    g.tick(System.currentTimeMillis())
                    delay(125)
                }
            }
            PalScreen(g, debug = BuildConfig.DEBUG, onUpdate = { startUpdate(g) })
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload every time the app comes back: the reminder job may have moved the pet on.
        lifecycleScope.launch {
            val loaded = store.load()
            game = Game(loaded) { s -> lifecycleScope.launch { store.save(s) } }.also {
                it.onArrive = { stops -> Milestones.announce(applicationContext, stops) }
                it.onSoundOut = { sfx -> chip.play(sfx) }
                it.onMusic = { on -> if (on) chip.startMusic() else chip.stopMusic() }
                if (it.save.musicOn) chip.startMusic()
            }
            startPedometer()
            shaker.start()
            updater.check()?.let { game?.offerUpdate(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        pedometer.stop()
        shaker.stop()
        chip.stopMusic()   // music only while the app is on screen
    }

    private fun startPedometer() {
        if (game == null) return
        pedometer.start { reading -> runOnUiThread { game?.onSteps(reading) } }
    }

    private fun startUpdate(g: Game) {
        val info = (g.panel as? Panel.Update)?.info ?: return
        lifecycleScope.launch {
            try {
                g.updateProgress(0)
                updater.downloadAndInstall(this@MainActivity, info) { p -> runOnUiThread { g.updateProgress(p) } }
            } catch (e: Exception) {
                g.updateFailed(e.message ?: "TRY AGAIN")
            }
        }
    }
}
