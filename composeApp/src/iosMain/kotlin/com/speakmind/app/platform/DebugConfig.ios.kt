package com.speakmind.app.platform

import platform.Foundation.NSProcessInfo

actual val isDebugMode: Boolean = NSProcessInfo.processInfo.environment["DEBUG"] != null
