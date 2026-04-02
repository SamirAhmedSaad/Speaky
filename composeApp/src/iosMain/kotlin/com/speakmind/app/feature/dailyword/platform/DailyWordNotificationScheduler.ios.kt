package com.speakmind.app.feature.dailyword.platform

import platform.UserNotifications.*
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents

actual class DailyWordNotificationScheduler {

    actual fun schedule(hour: Int, minute: Int, word: String, meaning: String): Boolean {
        val center = UNUserNotificationCenter.currentNotificationCenter()

        // Request permission
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions

            val content = UNMutableNotificationContent().apply {
                setTitle("Word of the Day: $word")
                setBody(meaning)
                setSound(UNNotificationSound.defaultSound())
                setUserInfo(mapOf("word" to word))
            }

            val dateComponents = NSDateComponents().apply {
                setHour(hour.toLong())
                setMinute(minute.toLong())
            }

            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents,
                repeats = false,
            )

            val request = UNNotificationRequest.requestWithIdentifier(
                "daily_word",
                content,
                trigger,
            )

            center.addNotificationRequest(request) { error ->
                error?.let { println("Failed to schedule notification: ${it.localizedDescription}") }
            }
        }
        return false // iOS handles its own permission flow via requestAuthorizationWithOptions
    }

    actual fun cancel() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf("daily_word"))
    }
}
