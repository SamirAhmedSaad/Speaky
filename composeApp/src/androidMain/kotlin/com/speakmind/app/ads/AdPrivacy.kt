package com.speakmind.app.ads

import android.content.Context
import android.telephony.TelephonyManager
import com.unity3d.mediation.LevelPlayPrivacySettings
import java.util.Locale

/**
 * Privacy signals for LevelPlay, set before init. Speaky has no consent form, so users in the
 * EEA, UK and Switzerland (where GDPR-style consent is required) only get non-personalized ads.
 */
object AdPrivacy {

    fun apply(context: Context) {
        if (needsConsent(context)) LevelPlayPrivacySettings.setGDPRConsent(false)
        // Speaky is for ages 13+, not a children's app.
        LevelPlayPrivacySettings.setCOPPA(false)
    }

    private fun needsConsent(context: Context): Boolean {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val country = listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }
            ?.uppercase()
            ?: return true // unknown: be safe
        return country in CONSENT_COUNTRIES
    }

    private val CONSENT_COUNTRIES = setOf(
        // EU
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV",
        "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        // EEA, UK, Switzerland
        "IS", "LI", "NO", "GB", "CH",
    )
}
