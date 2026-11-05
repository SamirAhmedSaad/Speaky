package com.speakmind.app.feature.community.ui.components

import org.jetbrains.compose.resources.DrawableResource
import speaky.composeapp.generated.resources.Res
import speaky.composeapp.generated.resources.avatar_fox
import speaky.composeapp.generated.resources.avatar_panda
import speaky.composeapp.generated.resources.avatar_cat
import speaky.composeapp.generated.resources.avatar_owl
import speaky.composeapp.generated.resources.avatar_bunny
import speaky.composeapp.generated.resources.avatar_sun
import speaky.composeapp.generated.resources.avatar_bear
import speaky.composeapp.generated.resources.avatar_koala
import speaky.composeapp.generated.resources.avatar_penguin
import speaky.composeapp.generated.resources.avatar_frog
import speaky.composeapp.generated.resources.avatar_lion
import speaky.composeapp.generated.resources.avatar_robot
import speaky.composeapp.generated.resources.avatar_moon
import speaky.composeapp.generated.resources.avatar_avocado
import speaky.composeapp.generated.resources.avatar_bubble_tea
import speaky.composeapp.generated.resources.avatar_drop
import speaky.composeapp.generated.resources.avatar_heart
import speaky.composeapp.generated.resources.avatar_cactus
import kotlin.math.abs

/**
 * Fun avatars for people without a photo. A pick is saved as the public URL of a copy in Storage
 * (avatars/<admin>/fun/<key>.png), so older app versions, which only load web images, show it too;
 * this version recognises the URL and draws its built-in copy. Without a pick, everyone sees the same
 * avatar for a person (chosen from their name), so nobody shows a bare letter.
 */
object FunAvatars {
    /** Early 1.5.0 test builds saved "avatar:<key>"; still understood, and rewritten to the URL. */
    const val PREFIX = "avatar:"

    /** The admin account folder holding the shared copies (kept by storage cleanup while it exists). */
    const val STORAGE_FOLDER = "751ad4b1-785b-4b9c-a433-39d79fc6dedd/fun"
    const val BASE_URL = "https://bkivjjvgzsqgbkfubjtx.supabase.co/storage/v1/object/public/avatars/$STORAGE_FOLDER/"

    val keys = listOf("fox", "panda", "cat", "owl", "bunny", "sun", "bear", "koala", "penguin", "frog", "lion", "robot", "moon", "avocado", "bubble_tea", "drop", "heart", "cactus")

    fun resource(key: String): DrawableResource? = when (key) {
        "fox" -> Res.drawable.avatar_fox
        "panda" -> Res.drawable.avatar_panda
        "cat" -> Res.drawable.avatar_cat
        "owl" -> Res.drawable.avatar_owl
        "bunny" -> Res.drawable.avatar_bunny
        "sun" -> Res.drawable.avatar_sun
        "bear" -> Res.drawable.avatar_bear
        "koala" -> Res.drawable.avatar_koala
        "penguin" -> Res.drawable.avatar_penguin
        "frog" -> Res.drawable.avatar_frog
        "lion" -> Res.drawable.avatar_lion
        "robot" -> Res.drawable.avatar_robot
        "moon" -> Res.drawable.avatar_moon
        "avocado" -> Res.drawable.avatar_avocado
        "bubble_tea" -> Res.drawable.avatar_bubble_tea
        "drop" -> Res.drawable.avatar_drop
        "heart" -> Res.drawable.avatar_heart
        "cactus" -> Res.drawable.avatar_cactus
        else -> null
    }

    /** The fun avatar a photo_url points at, or null for a real photo / nothing. */
    fun keyOf(photoUrl: String?): String? {
        val key = when {
            photoUrl == null -> null
            photoUrl.startsWith(PREFIX) -> photoUrl.removePrefix(PREFIX)
            photoUrl.startsWith(BASE_URL) -> photoUrl.removePrefix(BASE_URL).substringBefore('.')
            else -> null
        }
        return key?.takeIf { it in keys }
    }

    fun fallbackFor(name: String): String = keys[abs(name.hashCode()) % keys.size]

    fun photoUrlOf(key: String) = "$BASE_URL$key.png"

    /** Debug builds, once: uploads the built-in images to [STORAGE_FOLDER] (signed in as that admin). */
    suspend fun bytesOf(key: String): ByteArray = Res.readBytes("drawable/avatar_$key.png")
}
