package com.speakmind.app.ads

import android.app.Activity
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.aakira.napier.Napier

object ConsentManager {

    // Set to true only during local testing to force the consent form to appear
    private const val DEBUG_CONSENT = false

    fun gatherConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder().apply {
            if (DEBUG_CONSENT) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build()
                )
            }
        }.build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Info updated — load and show form if needed
                if (consentInfo.isConsentFormAvailable &&
                    consentInfo.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                ) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) {
                            Napier.w { "Consent form error: ${formError.message}" }
                        }
                        onComplete()
                    }
                } else {
                    onComplete()
                }
            },
            { requestError ->
                Napier.w { "Consent info update failed: ${requestError.message}" }
                // Proceed without consent on error — ads will still show (non-personalized)
                onComplete()
            }
        )
    }

    fun canRequestAds(activity: Activity): Boolean {
        val status = UserMessagingPlatform.getConsentInformation(activity).consentStatus
        return status == ConsentInformation.ConsentStatus.OBTAINED ||
            status == ConsentInformation.ConsentStatus.NOT_REQUIRED
    }
}
