package com.speakmind.app.feature.community.ui.components

import androidx.compose.runtime.Composable

interface ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher
