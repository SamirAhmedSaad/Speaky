package com.speakmind.app.feature.voice.platform

import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual object MicAccess : KoinComponent {
    actual fun isGranted(): Boolean = get<MicPermissionRequester>().isGranted(get<Context>())

    actual fun request() {
        get<MicPermissionRequester>().requestIfNeeded(get<Context>())
    }
}
