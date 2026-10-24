@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.jasonagibson.sproutpals.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.ui.PalScreen
import org.jasonagibson.sproutpals.ui.TitleScreen
import org.jetbrains.skia.Image
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeBufferLoops
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.CoreMotion.CMMotionManager
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The iPhone app: the same game as on Android (everything in commonMain), wrapped in what an iPhone
 * provides - its storage, sound, motion chip (shake to hatch, steps), notifications and share sheet.
 * Swift calls MainViewController() (see iosApp/Sources/App.swift) and shows what it returns.
 */

/** Called once by the Swift app; everything below runs inside this view controller. */
fun MainViewController(): UIViewController {
    CharacterArt.loader = ::bundledPicture
    return ComposeUIViewController { IosApp() }
}

@Composable
private fun IosApp() {
    val scope = rememberCoroutineScope()
    var showTitle by remember { mutableStateOf(true) }
    val sound = remember { IosSound() }
    val game = remember { Game(IosStore.load()) { s -> IosStore.save(s) } }
    val sync = remember { FriendsSync(game, Friends(), scope) }

    DisposableEffect(Unit) {
        game.onSoundOut = { sound.play(it) }
        game.onMusic = { on -> if (on) sound.startMusic() else sound.stopMusic() }
        game.onVisitOut = { code, hours -> sync.visit(code, hours) }
        game.onHeartOut = { code, msg -> sync.heart(code, msg) }
        if (game.save.musicOn) sound.startMusic()
        val shake = IosShake { if (!showTitle) game.onShake() }.also { it.start() }
        val steps = IosSteps { game.onSteps(it) }.also { it.start() }
        Reminders.ask()
        // leaving the app: plan the reminders it can't send while closed
        val leaving = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> Reminders.plan(game.save) }
        onDispose {
            shake.stop()
            steps.stop()
            sound.stopMusic()
            NSNotificationCenter.defaultCenter.removeObserver(leaving)
        }
    }
    LaunchedEffect(Unit) {
        sync.start()
        while (true) { delay(60_000); sync.poll() }
    }

    if (showTitle) {
        TitleScreen(onStart = { showTitle = false })
        return
    }
    LaunchedEffect(game) {
        while (true) { game.tick(nowMillis()); delay(125) }
    }
    PalScreen(
        game, debug = false, onUpdate = {},
        onShareCode = { game.save.gardenCode?.let { share(shareText(it)) } },
        onAddFriend = { code -> sync.addFriend(code) },
    )
}

/** The whole save as one JSON string in the app's own settings storage (same format as Android). */
private object IosStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val KEY = "save"

    fun load(): Save = NSUserDefaults.standardUserDefaults.stringForKey(KEY)
        ?.let { runCatching { json.decodeFromString(Save.serializer(), it) }.getOrNull() }
        ?: Save(pet = PetEngine.newPet(nowMillis(), Random.Default))

    fun save(s: Save) = NSUserDefaults.standardUserDefaults.setObject(json.encodeToString(Save.serializer(), s), KEY)
}

/** A character picture from the app bundle (the same PNGs as Android, copied in at build time). */
private fun bundledPicture(name: String): ImageBitmap {
    val path = NSBundle.mainBundle.pathForResource(name, "png") ?: return ImageBitmap(1, 1)
    val data = NSData.dataWithContentsOfFile(path) ?: return ImageBitmap(1, 1)
    val size = data.length.toInt()
    if (size == 0) return ImageBitmap(1, 1)
    val bytes = ByteArray(size)
    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

/** The 8-bit beeps and music: shared samples (Jingles, Theme), played through AVAudioEngine. */
private class IosSound {
    private val rate = 22_050
    private val engine = AVAudioEngine()
    private val beeps = AVAudioPlayerNode()
    private val music = AVAudioPlayerNode()
    private val format = AVAudioFormat(standardFormatWithSampleRate = rate.toDouble(), channels = 1u)

    init {
        // "ambient": mixes with other audio and follows the silent switch, like a game should
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        engine.attachNode(beeps)
        engine.attachNode(music)
        engine.connect(beeps, engine.mainMixerNode, format)
        engine.connect(music, engine.mainMixerNode, format)
        engine.startAndReturnError(null)
    }

    private fun buffer(pcm: ShortArray): AVAudioPCMBuffer {
        val buf = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = pcm.size.toUInt())
        buf.frameLength = pcm.size.toUInt()
        val samples = buf.floatChannelData!![0]!!
        for (i in pcm.indices) samples[i] = pcm[i] / 32768f
        return buf
    }

    private fun running(): Boolean = engine.running || engine.startAndReturnError(null)

    fun play(sfx: Sfx) {
        if (!running()) return
        beeps.scheduleBuffer(buffer(Jingles.pcm(sfx, rate)), null)
        beeps.play()
    }

    fun startMusic() {
        if (!running() || music.playing) return
        val (pcm, loopFrom) = Theme.render(rate)
        music.scheduleBuffer(buffer(pcm.copyOfRange(0, loopFrom)), null)                     // the fanfare, once
        music.scheduleBuffer(buffer(pcm.copyOfRange(loopFrom, pcm.size)), null, AVAudioPlayerNodeBufferLoops, null)
        music.play()
    }

    fun stopMusic() = music.stop()
}

/** A firm shake (over 2.5 g, at most one every 400 ms) counts as one jolt for opening the egg. */
private class IosShake(private val onJolt: () -> Unit) {
    private val motion = CMMotionManager()
    private var last = 0L

    fun start() {
        if (!motion.accelerometerAvailable) return
        motion.accelerometerUpdateInterval = 0.02
        motion.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            data?.acceleration?.useContents {
                val g = sqrt(x * x + y * y + z * z)          // already in g on iPhone
                val now = nowMillis()
                if (g > 2.5 && now - last > 400) { last = now; onJolt() }
            }
        }
    }

    fun stop() = motion.stopAccelerometerUpdates()
}

/**
 * Steps from the motion chip while the app is open. The count starts at 0 each time the app opens,
 * which the shared step rules read as a fresh start (like an Android phone after a reboot).
 */
private class IosSteps(private val onReading: (Long) -> Unit) {
    private val pedometer = CMPedometer()

    fun start() {
        if (!CMPedometer.isStepCountingAvailable()) return
        pedometer.startPedometerUpdatesFromDate(NSDate()) { data, _ ->
            val steps = data?.numberOfSteps?.longValue ?: return@startPedometerUpdatesFromDate
            dispatch_async(dispatch_get_main_queue()) { onReading(steps) }
        }
    }

    fun stop() = pedometer.stopPedometerUpdates()
}

/**
 * iPhone apps can't wake up every 15 minutes the way Android's reminder job does, so when the app is
 * left, it plans its reminders ahead: bedtime lights, and a check-in later on.
 */
private object Reminders {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    fun ask() = center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }

    fun plan(save: Save) {
        center.removeAllPendingNotificationRequests()
        val name = save.pet.species.title
        if (save.pet.ended != null) return
        // a gentle check-in after 4 hours away
        add("checkin", 4 * 3600.0, if (save.pet.hatchedAt == null) "An egg is waiting! Shake your phone to open it." else "$name misses you. Come say hi!")
        // bedtime: lights off a little after 10pm
        val now = nowMillis()
        val hour = hourAt(now)
        if (save.pet.hatchedAt != null && hour < 22) {
            val untilTen = ((22 - hour) * 3600 - (now / 1000 % 3600)).toDouble() + 300
            add("lights", untilTen, "$name is getting sleepy. Lights off?")
        }
    }

    private fun add(id: String, seconds: Double, text: String) {
        val content = UNMutableNotificationContent()
        content.setTitle("Sprout Pals")
        content.setBody(text)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds.coerceAtLeast(60.0), false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), null)
    }
}

/** The iPhone share sheet (Messages, Mail, ...) for sending the garden code. */
private fun share(text: String) {
    val sheet = UIActivityViewController(listOf(text), null)
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(sheet, true, null)
}
