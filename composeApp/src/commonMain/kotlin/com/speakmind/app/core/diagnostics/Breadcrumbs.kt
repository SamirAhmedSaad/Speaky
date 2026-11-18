package com.speakmind.app.core.diagnostics

/**
 * Small trail of what the app was doing, attached to crash and ANR reports (Crashlytics logs and
 * custom keys on Android). Shared code calls these; the Android app plugs in the real reporter.
 * Never throws and never blocks: safe to call from any thread, as often as needed.
 */
object Breadcrumbs {

    interface Sink {
        fun log(message: String)
        fun key(name: String, value: String)
        fun screen(name: String)
    }

    var sink: Sink? = null

    /** A step worth seeing in the next crash/ANR report, e.g. "call: leave". */
    fun log(message: String) {
        runCatching { sink?.log(message) }
    }

    /** Latest value of a piece of state, e.g. key("in_call", "true"). */
    fun key(name: String, value: String) {
        runCatching { sink?.key(name, value) }
    }

    /** The screen now on top (from navigation). */
    private var lastScreen: String? = null

    fun screen(name: String) {
        if (name == lastScreen) return
        lastScreen = name
        runCatching {
            sink?.key("screen", name)
            sink?.screen(name)
            sink?.log("screen: $name")
        }
    }

    /** Wraps background work (alarm, push, boot) so a report shows it started and whether it ended. */
    inline fun <T> work(name: String, block: () -> T): T {
        log("$name: start")
        try {
            return block().also { log("$name: done") }
        } catch (e: Throwable) {
            log("$name: failed ${e::class.simpleName}")
            throw e
        }
    }
}
