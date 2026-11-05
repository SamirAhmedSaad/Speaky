package com.speakmind.app.platform

import platform.Foundation.NSProcessInfo

actual fun bootElapsedMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
