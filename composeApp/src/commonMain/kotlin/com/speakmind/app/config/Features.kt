package com.speakmind.app.config

/** Features switched off in the UI for now; the code behind them stays in place. */
object Features {
    /** AI chat (Speaky Chat on Home, "Discuss with Sage" on articles): hidden, new users found it confusing. */
    const val AI_CHAT = false

    /** Stories: live Reddit posts, unfiltered and native-level; hidden. */
    const val STORIES = false

    /** My Groups on Home: hand-made word lists, overlaps Saved; hidden to keep Home simple. */
    const val MY_GROUPS = false
}
