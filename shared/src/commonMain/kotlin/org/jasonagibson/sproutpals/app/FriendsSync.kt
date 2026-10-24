package org.jasonagibson.sproutpals.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jasonagibson.sproutpals.engine.Stage

/**
 * Keeps a [Game] in touch with the friends server: a garden code the first time, then the pal and its
 * outfit, friends, visitors, trips and hearts. The same steps the Android app takes (MainActivity), so the
 * iPhone app plays along with the same server and the same friends.
 */
class FriendsSync(private val game: Game, private val api: Friends, private val scope: CoroutineScope) {
    private var outfitSent: List<String>? = null

    /** First time: get a garden code. Then: tell the server what our pal is, fetch friends and news. */
    suspend fun start() {
        if (game.save.serverToken == null) {
            when (val r = api.register()) {
                is Friends.Reply.Ok -> game.registered(r.value.code, r.value.token)
                is Friends.Reply.Fail -> { game.friendsNote = r.message; return }
            }
        }
        val token = game.save.serverToken ?: return
        val pet = game.pet
        api.setPet(token, if (pet.stage == Stage.EGG) "EGG" else pet.species.name, if (pet.ended != null) "GONE" else pet.stage.name)
        (api.friends(token) as? Friends.Reply.Ok)?.let { game.friendList = it.value }
        poll()
    }

    /** Visitors, our pal's trip, new hearts, and our outfit if it changed (every minute while open). */
    suspend fun poll() {
        val token = game.save.serverToken ?: return
        if (game.save.wearing != outfitSent) {
            if (api.setOutfit(token, game.save.wearing) is Friends.Reply.Ok) outfitSent = game.save.wearing
        }
        (api.garden(token) as? Friends.Reply.Ok)?.let { game.gardenNow(it.value) }
        (api.inbox(token) as? Friends.Reply.Ok)?.value?.lastOrNull()?.let { h ->
            game.friendsNote = "♥ from ${h.from}${if (h.message.isNotBlank()) ": ${h.message}" else ""}"
        }
    }

    fun addFriend(code: String) {
        val token = game.save.serverToken ?: run { game.friendsNote = "Still connecting to the garden server..."; return }
        scope.launch {
            game.friendsNote = "Looking for $code..."
            game.friendsNote = when (val r = api.addFriend(token, code)) {
                is Friends.Reply.Ok -> {
                    (api.friends(token) as? Friends.Reply.Ok)?.let { game.friendList = it.value }
                    game.count("friend")
                    "Added ${r.value.code}! Your gardens are connected."
                }
                is Friends.Reply.Fail -> r.message
            }
        }
    }

    fun visit(code: String, hours: Int) {
        val token = game.save.serverToken ?: return game.visitFailed("Still connecting...")
        scope.launch {
            api.setOutfit(token, game.save.wearing)   // arrive dressed up
            when (val r = api.visit(token, code, hours)) {
                is Friends.Reply.Ok -> game.visitStarted(r.value.host, r.value.until * 1000)
                is Friends.Reply.Fail -> game.visitFailed(r.message)
            }
        }
    }

    fun heart(code: String, message: String) {
        val token = game.save.serverToken ?: return
        scope.launch { (api.sendHeart(token, code, message) as? Friends.Reply.Fail)?.let { game.visitFailed(it.message) } }
    }
}
