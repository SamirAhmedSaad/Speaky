package com.speakmind.app.platform

/** Milliseconds since the device booted. Keeps counting while asleep and can't be changed by the user. */
expect fun bootElapsedMillis(): Long
