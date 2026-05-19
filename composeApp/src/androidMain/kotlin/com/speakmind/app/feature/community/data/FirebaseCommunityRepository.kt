package com.speakmind.app.feature.community.data

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.community.data.model.ChatMessage
import com.speakmind.app.feature.community.data.model.CommunityLocalProfile
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.DAILY_MESSAGE_LIMIT
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

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
                "gender" to gender,
                "lastSeen" to FieldValue.serverTimestamp(),
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
                .orderBy("nickname")
                .limit(30)
        } else {
            val end = searchQuery + ''
            firestore.collection("users")
                .orderBy("nickname")
                .startAt(searchQuery)
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

    override fun getMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        // Emit cached local messages first
        val cached = database.speakMindQueries.getMessagesForChat(chatId)
            .executeAsList()
            .map { row ->
                ChatMessage(
                    id = row.id,
                    chatId = row.chat_id,
                    senderId = row.sender_id,
                    text = row.text_content,
                    timestamp = row.timestamp,
                    isSynced = row.is_synced == 1L,
                )
            }
        if (cached.isNotEmpty()) trySend(cached)

        val listener = firestore
            .collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val currentUid = auth.currentUser?.uid ?: return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    ChatMessage(
                        id = doc.id,
                        chatId = chatId,
                        senderId = doc.getString("senderId") ?: return@mapNotNull null,
                        text = doc.getString("text") ?: return@mapNotNull null,
                        timestamp = doc.getTimestamp("timestamp")?.seconds ?: 0L,
                        isSynced = true,
                    )
                }
                // Detect truly new messages from the other user for unread count
                val knownIds = database.speakMindQueries
                    .getMessagesForChat(chatId).executeAsList().map { it.id }.toSet()
                val newFromOther = messages.count { it.id !in knownIds && it.senderId != currentUid }
                if (newFromOther > 0) {
                    val otherUid = chatId.split("_").firstOrNull { it != currentUid }
                        ?.takeIf { it.isNotEmpty() } ?: return@addSnapshotListener
                    database.speakMindQueries.insertOrIgnoreUnread(chatId, otherUid)
                    database.speakMindQueries.incrementUnreadCount(newFromOther.toLong(), chatId)
                }
                // Update local cache
                messages.forEach { msg ->
                    database.speakMindQueries.insertCommunityMessage(
                        id = msg.id,
                        chat_id = msg.chatId,
                        sender_id = msg.senderId,
                        text_content = msg.text,
                        timestamp = msg.timestamp,
                        is_synced = 1L,
                    )
                }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(chatId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val msgId = UUID.randomUUID().toString()
        val nowMillis = System.currentTimeMillis()
        val nowSeconds = nowMillis / 1000L

        // Save locally immediately (offline-first)
        database.speakMindQueries.insertCommunityMessage(
            id = msgId,
            chat_id = chatId,
            sender_id = uid,
            text_content = text,
            timestamp = nowSeconds,
            is_synced = 0L,
        )

        // Try to sync to Firestore
        try {
            val otherUid = chatId.split("_").firstOrNull { it != uid } ?: ""
            val chatRef = firestore.collection("chats").document(chatId)
            chatRef.collection("messages").document(msgId).set(
                mapOf(
                    "senderId" to uid,
                    "text" to text,
                    "timestamp" to FieldValue.serverTimestamp(),
                )
            ).await()
            chatRef.set(
                mapOf(
                    "lastMessage" to text,
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "lastSenderId" to uid,
                    "participants" to listOf(uid, otherUid).filter { it.isNotEmpty() },
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            database.speakMindQueries.markMessageSynced(msgId)
        } catch (_: Exception) {
            // Will sync later via syncPendingMessages
        }
    }

    override suspend fun updateLastSeen() {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid)
                .update("lastSeen", FieldValue.serverTimestamp())
                .await()
        } catch (_: Exception) {}
    }

    override fun getTotalUnreadCount(): Flow<Int> = flow {
        val count = database.speakMindQueries.getTotalUnread().executeAsOne()
        emit(count.toInt())
    }

    override fun getUnreadCounts(): Flow<Map<String, Int>> = flow {
        val rows = database.speakMindQueries.getAllUnreadCounts().executeAsList()
        emit(rows.associate { it.other_user_id to it.unread_count.toInt() })
    }

    override suspend fun markChatRead(chatId: String) {
        database.speakMindQueries.resetUnreadCount(chatId)
    }

    override fun observeAllChatsForUnread(): Flow<Int> = callbackFlow {
        val currentUid = auth.currentUser?.uid ?: run {
            trySend(0)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("chats")
            .whereArrayContains("participants", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type != DocumentChange.Type.MODIFIED) continue
                    val doc = change.document
                    val chatId = doc.id
                    val lastSenderId = doc.getString("lastSenderId") ?: continue
                    if (lastSenderId == currentUid) continue  // own message
                    val lastMessageTime = doc.getTimestamp("lastMessageTime")?.seconds ?: 0L
                    // Only count if not already in local cache (avoids double-counting with screen listener)
                    val latestLocal = database.speakMindQueries
                        .getMessagesForChat(chatId).executeAsList()
                        .maxOfOrNull { it.timestamp } ?: 0L
                    if (lastMessageTime > latestLocal) {
                        val otherUid = chatId.split("_").firstOrNull { it != currentUid } ?: continue
                        database.speakMindQueries.insertOrIgnoreUnread(chatId, otherUid)
                        database.speakMindQueries.incrementUnreadCount(1L, chatId)
                    }
                }
                val total = try {
                    database.speakMindQueries.getTotalUnread().executeAsOne().toInt()
                } catch (_: Exception) { 0 }
                trySend(total)
            }
        awaitClose { listener.remove() }
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

    override suspend fun syncPendingMessages() {
        val pending = database.speakMindQueries.getPendingMessages().executeAsList()
        for (row in pending) {
            try {
                val chatRef = firestore.collection("chats").document(row.chat_id)
                chatRef.collection("messages").document(row.id).set(
                    mapOf(
                        "senderId" to row.sender_id,
                        "text" to row.text_content,
                        "timestamp" to FieldValue.serverTimestamp(),
                    )
                ).await()
                val currentUid = auth.currentUser?.uid ?: ""
                val otherUid = row.chat_id.split("_").firstOrNull { it != currentUid } ?: ""
                chatRef.set(
                    mapOf(
                        "lastMessage" to row.text_content,
                        "lastMessageTime" to FieldValue.serverTimestamp(),
                        "lastSenderId" to row.sender_id,
                        "participants" to listOf(currentUid, otherUid).filter { it.isNotEmpty() },
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
                database.speakMindQueries.markMessageSynced(row.id)
            } catch (_: Exception) {
                break // Stop if network unavailable, retry next session
            }
        }
    }
}
