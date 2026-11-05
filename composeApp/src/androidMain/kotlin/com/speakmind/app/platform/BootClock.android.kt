package com.speakmind.app.platform

import android.os.SystemClock

actual fun bootElapsedMillis(): Long = SystemClock.elapsedRealtime()
