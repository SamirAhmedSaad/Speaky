package com.speakmind.app.feature.community.data

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.community.data.model.ChannelMessage
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.DAILY_MESSAGE_LIMIT
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

private const val CHANNEL_COLLECTION = "channelMessages"

class FirebaseCommunityRepository(
    private val database: SpeakyDatabase,
    private val context: Context,
) : CommunityRepository {

    private val auth get() = Firebase.auth
    private val firestore get() = Firebase.firestore

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun signInAnonymously(): String {
        if (auth.currentUser != null) return auth.currentUser!!.uid
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val hash = sha256("$androidId:speakmind_v1")
        val email = "${hash.take(20)}@u.speakmind"
        val password = hash.take(32)
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            auth.currentUser!!.uid
        } catch (e: FirebaseAuthInvalidUserException) {
            auth.createUserWithEmailAndPassword(email, password).await()
            auth.currentUser!!.uid
        } catch (_: Exception) {
            auth.signInAnonymously().await()
            auth.currentUser?.uid ?: ""
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override suspend fun saveUserProfile(nickname: String, gender: String) {
        val uid = auth.currentUser?.uid ?: signInAnonymously()
        firestore.collection("users").document(uid).set(
            mapOf(
                "nickname" to nickname,
                "nicknameSearch" to nickname.lowercase(),
                "gender" to gender,
                "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
        database.speakMindQueries.upsertCommunityProfile(
            firebase_uid = uid,
            nickname = nickname,
            gender = gender,
            photo_url = "",
        )
    }

    override suspend fun getLocalProfile(): CommunityLocalProfile? {
        val row = database.speakMindQueries.getCommunityProfile().executeAsOneOrNull()
            ?: return null
        if (row.firebase_uid.isEmpty()) return null
        return CommunityLocalProfile(
            uid = row.firebase_uid,
            nickname = row.nickname,
            gender = row.gender,
            photoUrl = row.photo_url.ifEmpty { null },
        )
    }

    override suspend fun getUserNickname(): String {
        return database.speakMindQueries.selectProgress()
            .executeAsOneOrNull()?.user_name ?: ""
    }

    override fun getUsers(searchQuery: String, lastUid: String?): Flow<List<CommunityUser>> = callbackFlow {
        val currentUid = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val query = if (searchQuery.isBlank()) {
            firestore.collection("users")
                .orderBy("nicknameSearch")
                .limit(30)
        } else {
            val q = searchQuery.lowercase()
            val end = q + ''
            firestore.collection("users")
                .orderBy("nicknameSearch")
                .startAt(q)
                .endAt(end)
                .limit(30)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val users = snapshot.documents
                .filter { it.id != currentUid }
                .mapNotNull { doc ->
                    CommunityUser(
                        uid = doc.id,
                        nickname = doc.getString("nickname") ?: return@mapNotNull null,
                        gender = doc.getString("gender") ?: "male",
                        lastSeen = doc.getTimestamp("lastSeen")?.seconds ?: 0L,
                        photoUrl = doc.getString("photoUrl"),
                    )
                }
            trySend(users)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateLastSeen() {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid)
                .update("lastSeen", com.google.firebase.firestore.FieldValue.serverTimestamp())
                .await()
        } catch (_: Exception) {}
    }

    override suspend fun checkAndIncrementDailyQuota(): Boolean {
        val today = java.time.LocalDate.now().toString()
        val row = database.speakMindQueries.getCommunityDailyQuota().executeAsOneOrNull()
        val currentCount = if (row == null || row.date != today) {
            database.speakMindQueries.upsertCommunityDailyQuota(today, 0)
            0
        } else {
            row.messages_sent.toInt()
        }
        if (currentCount >= DAILY_MESSAGE_LIMIT) return false
        database.speakMindQueries.incrementDailyMessageCount()
        return true
    }

    // --- Global channel ---

    override suspend fun loadChannelPage(pageSize: Int, beforeTimestampSeconds: Long?): List<ChannelMessage> {
        return try {
            val query = if (beforeTimestampSeconds == null) {
                firestore.collection(CHANNEL_COLLECTION)
                    .orderBy("timestampSeconds", Query.Direction.DESCENDING)
                    .limit(pageSize.toLong())
            } else {
                firestore.collection(CHANNEL_COLLECTION)
                    .orderBy("timestampSeconds", Query.Direction.DESCENDING)
                    .whereLessThan("timestampSeconds", beforeTimestampSeconds)
                    .limit(pageSize.toLong())
            }
            val docs = query.get().await()
            val messages = docs.documents.reversed().mapNotNull { parseChannelDoc(it) }
            messages.forEach { cacheMessage(it) }
            messages
        } catch (_: Exception) {
            // Fallback to local cache
            if (beforeTimestampSeconds == null) {
                database.speakMindQueries.getChannelMessages(pageSize.toLong())
                    .executeAsList().map { mapRowToMessage(it) }.reversed()
            } else {
                database.speakMindQueries.getChannelMessagesBefore(beforeTimestampSeconds, pageSize.toLong())
                    .executeAsList().map { mapRowToMessage(it) }.reversed()
            }
        }
    }

    override fun observeNewChannelMessages(afterTimestampSeconds: Long): Flow<ChannelMessage> = callbackFlow {
        var isFirstSnapshot = true
        val listener = firestore.collection(CHANNEL_COLLECTION)
            .orderBy("timestampSeconds", Query.Direction.ASCENDING)
            .whereGreaterThan("timestampSeconds", afterTimestampSeconds)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (isFirstSnapshot) {
                    isFirstSnapshot = false
                    return@addSnapshotListener
                }
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val msg = parseChannelDoc(change.document) ?: continue
                        cacheMessage(msg)
                        trySend(msg)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendChannelMessage(text: String): ChannelMessage? {
        val uid = auth.currentUser?.uid ?: return null
        val profile = getLocalProfile() ?: return null
        val msgId = UUID.randomUUID().toString()
        val nowSeconds = System.currentTimeMillis() / 1000L

        val msg = ChannelMessage(
            id = msgId,
            senderId = uid,
            senderNickname = profile.nickname,
            senderPhotoUrl = profile.photoUrl ?: "",
            senderGender = profile.gender,
            text = text,
            timestamp = nowSeconds,
            isSynced = false,
        )

        // Optimistic local insert
        cacheMessage(msg, isSynced = 0L)

        // Sync to Firestore
        try {
            firestore.collection(CHANNEL_COLLECTION).document(msgId).set(
                mapOf(
                    "senderId" to uid,
                    "senderNickname" to profile.nickname,
                    "senderPhotoUrl" to (profile.photoUrl ?: ""),
                    "senderGender" to profile.gender,
                    "text" to text,
                    "timestampSeconds" to nowSeconds,
                )
            ).await()
            database.speakMindQueries.markChannelMessageSynced(msgId)
        } catch (_: Exception) {
            // Will retry via syncPendingChannelMessages
        }

        return msg
    }

    override suspend fun syncPendingChannelMessages() {
        val pending = database.speakMindQueries.getPendingChannelMessages().executeAsList()
        for (row in pending) {
            try {
                firestore.collection(CHANNEL_COLLECTION).document(row.id).set(
                    mapOf(
                        "senderId" to row.sender_id,
                        "senderNickname" to row.sender_nickname,
                        "senderPhotoUrl" to row.sender_photo_url,
                        "senderGender" to row.sender_gender,
                        "text" to row.text_content,
                        "timestampSeconds" to row.timestamp,
                    )
                ).await()
                database.speakMindQueries.markChannelMessageSynced(row.id)
            } catch (_: Exception) {
                break
            }
        }
    }

    override suspend fun updateUserName(name: String) {
        database.speakMindQueries.updateUserName(name)
        val profile = getLocalProfile() ?: return
        database.speakMindQueries.upsertCommunityProfile(
            firebase_uid = profile.uid,
            nickname = name,
            gender = profile.gender,
            photo_url = profile.photoUrl ?: "",
        )
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "nickname" to name,
                    "nicknameSearch" to name.lowercase(),
                )
            ).await()
        } catch (_: Exception) {}
    }

    // --- Helpers ---

    private fun parseChannelDoc(doc: DocumentSnapshot): ChannelMessage? {
        return ChannelMessage(
            id = doc.id,
            senderId = doc.getString("senderId") ?: return null,
            senderNickname = doc.getString("senderNickname") ?: "",
            senderPhotoUrl = doc.getString("senderPhotoUrl") ?: "",
            senderGender = doc.getString("senderGender") ?: "male",
            text = doc.getString("text") ?: return null,
            timestamp = (doc.get("timestampSeconds") as? Long) ?: 0L,
            isSynced = true,
        )
    }

    private fun cacheMessage(msg: ChannelMessage, isSynced: Long = 1L) {
        database.speakMindQueries.insertChannelMessage(
            id = msg.id,
            sender_id = msg.senderId,
            sender_nickname = msg.senderNickname,
            sender_photo_url = msg.senderPhotoUrl,
            sender_gender = msg.senderGender,
            text_content = msg.text,
            timestamp = msg.timestamp,
            is_synced = isSynced,
        )
    }

    private fun mapRowToMessage(row: com.speakmind.app.db.Channel_messages): ChannelMessage {
        return ChannelMessage(
            id = row.id,
            senderId = row.sender_id,
            senderNickname = row.sender_nickname,
            senderPhotoUrl = row.sender_photo_url,
            senderGender = row.sender_gender,
            text = row.text_content,
            timestamp = row.timestamp,
            isSynced = row.is_synced == 1L,
        )
    }
}
