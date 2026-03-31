package com.speakmind.app

import com.speakmind.app.di.initKoin
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { AppRoot() }
}
