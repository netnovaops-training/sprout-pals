package org.jasonagibson.sproutpals.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
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
import org.jasonagibson.sproutpals.engine.Stage
import android.content.Intent
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
    private val friendsApi = Friends()

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startPedometer()
        checkNotifications()
    }

    /** True when the phone won't show our notifications; PalScreen shows a "turn on" banner. */
    private var notificationsOff by mutableStateOf(false)

    private fun checkNotifications() {
        val wasOff = notificationsOff
        notificationsOff = !NotificationManagerCompat.from(this).areNotificationsEnabled()
        // just switched on: one test notification, so it's clear they work
        if (wasOff && !notificationsOff) ReminderWorker.hello(applicationContext)
    }

    /** The banner's tap: ask again if Android still allows it, otherwise open this app's notification settings. */
    private fun enableNotifications() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            askPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(applicationContext)
        CharacterArt.init(this)
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
            PalScreen(
                g, debug = BuildConfig.DEBUG, onUpdate = { startUpdate(g) },
                onShareCode = { shareCode(g) },
                onAddFriend = { code -> addFriend(g, code) },
                notificationsOff = notificationsOff,
                onEnableNotifications = { enableNotifications() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotifications()   // they may have just been switched on in Settings
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
            game?.let { syncFriends(it) }
            updater.check()?.let { game?.offerUpdate(it) }
        }
    }

    // ---------- friends server ----------

    /** First time: get a garden code. Then: tell the server what our pet is, fetch friends and hearts. */
    private suspend fun syncFriends(g: Game) {
        if (g.save.serverToken == null) {
            when (val r = friendsApi.register()) {
                is Friends.Reply.Ok -> g.registered(r.value.code, r.value.token)
                is Friends.Reply.Fail -> { g.friendsNote = r.message; return }
            }
        }
        val token = g.save.serverToken ?: return
        val pet = g.pet
        friendsApi.setPet(token, if (pet.stage == Stage.EGG) "EGG" else pet.species.name, if (pet.ended != null) "GONE" else pet.stage.name)
        (friendsApi.friends(token) as? Friends.Reply.Ok)?.let { g.friendList = it.value }
        (friendsApi.inbox(token) as? Friends.Reply.Ok)?.value?.lastOrNull()?.let { h ->
            g.friendsNote = "♥ from ${h.from}${if (h.message.isNotBlank()) ": ${h.message}" else ""}"
        }
    }

    private fun addFriend(g: Game, code: String) {
        val token = g.save.serverToken ?: run { g.friendsNote = "Still connecting to the garden server..."; return }
        lifecycleScope.launch {
            g.friendsNote = "Looking for $code..."
            g.friendsNote = when (val r = friendsApi.addFriend(token, code)) {
                is Friends.Reply.Ok -> {
                    (friendsApi.friends(token) as? Friends.Reply.Ok)?.let { g.friendList = it.value }
                    "Added ${r.value.code}! Your gardens are connected."
                }
                is Friends.Reply.Fail -> r.message
            }
        }
    }

    /** Android's share sheet: text it, email it, whatever the person likes. */
    private fun shareCode(g: Game) {
        val code = g.save.gardenCode ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(code))
        startActivity(Intent.createChooser(send, "Share your garden code"))
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
