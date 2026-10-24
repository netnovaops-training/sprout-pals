package org.jasonagibson.sproutpals.engine

import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/*
 * The tests were written with java.time (they run on the computer's JVM). The shared rules now use
 * kotlinx-datetime so they also run on iPhone; these test-only overloads convert at the boundary,
 * so every test reads exactly as it did.
 */
fun PetEngine.advance(state: PetState, to: Long, zone: ZoneId, rng: Random): PetState =
    advance(state, to, zone.toKotlinTimeZone(), rng)

fun Visitors.catchUp(save: Save, now: Long, zone: ZoneId, rng: Random): Pair<Save, List<String>> =
    catchUp(save, now, zone.toKotlinTimeZone(), rng)

fun Weather.plan(save: Save, at: Long, zone: ZoneId, rng: Random): Save =
    plan(save, at, zone.toKotlinTimeZone(), rng)

fun Calendar.season(d: LocalDate): Season = season(d.toKotlinLocalDate())

fun Calendar.holiday(d: LocalDate): Holiday? = holiday(d.toKotlinLocalDate())
