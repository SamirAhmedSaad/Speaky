package com.speakmind.app.feature.community.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher {
    return remember {
        object : ImagePickerLauncher {
            override fun launch() {} // Photo upload not supported on iOS
        }
    }
}
