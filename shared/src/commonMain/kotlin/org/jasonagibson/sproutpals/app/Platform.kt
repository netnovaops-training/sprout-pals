package org.jasonagibson.sproutpals.app

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant

/*
 * Small things the shared game needs that each phone provides differently - kept in one place so the
 * rest of the code reads the same on Android and iPhone.
 */

/** The phone's clock, in milliseconds since 1970. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** The phone's own time zone. */
fun localZone(): TimeZone = TimeZone.currentSystemDefault()

/** The hour (0-23) at [ms] in [zone]. */
fun hourAt(ms: Long, zone: TimeZone = localZone()): Int = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).hour

/** The calendar date at [ms] in [zone]. */
fun dateAt(ms: Long, zone: TimeZone = localZone()): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date

/** A number with one decimal place ("3.4"), the same on every phone. */
fun oneDecimal(x: Double): String {
    val tenths = (x * 10).roundToLong()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}

/** A whole number from a decimal ("12"). */
fun noDecimals(x: Double): String = x.roundToLong().toString()

/** The game's sounds; each phone plays them its own way (Android: AudioTrack, iPhone: AVAudioEngine). */
enum class Sfx { A, B, C, SHOP, HATCH, ARRIVE, ACHIEVE, KEY_ITEM }

/** A newer version offered by the self-updater (Android only; iPhone updates come through SideStore). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val md5: String,
    val notes: String,
)
