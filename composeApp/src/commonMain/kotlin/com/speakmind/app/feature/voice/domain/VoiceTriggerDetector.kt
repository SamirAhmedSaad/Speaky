package com.speakmind.app.feature.voice.domain

sealed class VoiceTrigger {
    data object SaveThis : VoiceTrigger()
    data object RepeatThat : VoiceTrigger()
    data object RepeatSlowly : VoiceTrigger()
    data class WhatDoesMean(val word: String) : VoiceTrigger()
    data class HowDoYouSpell(val word: String) : VoiceTrigger()
    data object GiveMeExample : VoiceTrigger()
    data object WasThatCorrect : VoiceTrigger()
    data object SlowDown : VoiceTrigger()
    data object SpeedUp : VoiceTrigger()
    data object NeedHelp : VoiceTrigger()
    data object NoTrigger : VoiceTrigger()
}

object VoiceTriggerDetector {

    fun detect(transcript: String): VoiceTrigger {
        val lower = transcript.lowercase().trim()

        return when {
            // Save triggers
            lower.contains("save this") || lower.contains("add to my cards") ||
            lower.contains("i want to remember this") -> VoiceTrigger.SaveThis

            // Repeat triggers
            lower.contains("repeat that slowly") || lower.contains("say that again slowly") ->
                VoiceTrigger.RepeatSlowly
            lower.contains("repeat that") || lower.contains("say that again") ||
            lower.contains("one more time") -> VoiceTrigger.RepeatThat

            // Meaning trigger
            lower.startsWith("what does") && lower.contains("mean") -> {
                val word = extractWordFromMeaning(lower)
                if (word.isNotEmpty()) VoiceTrigger.WhatDoesMean(word)
                else VoiceTrigger.NoTrigger
            }

            // Spelling trigger
            lower.startsWith("how do you spell") || lower.startsWith("how to spell") -> {
                val word = extractWordFromSpelling(lower)
                if (word.isNotEmpty()) VoiceTrigger.HowDoYouSpell(word)
                else VoiceTrigger.NoTrigger
            }

            // Example trigger
            lower.contains("give me an example") || lower.contains("give me examples") ||
            lower.contains("example please") -> VoiceTrigger.GiveMeExample

            // Correction check
            lower.contains("was that correct") || lower.contains("is that right") ||
            lower.contains("did i say that right") -> VoiceTrigger.WasThatCorrect

            // Speed triggers
            lower.contains("too fast") || lower.contains("slow down") ||
            lower.contains("slower please") -> VoiceTrigger.SlowDown
            lower.contains("too slow") || lower.contains("speed up") ||
            lower.contains("faster please") -> VoiceTrigger.SpeedUp

            // Help triggers
            lower.contains("i don't know") || lower.contains("i'm stuck") ||
            lower.contains("help me") || lower.contains("i need help") ->
                VoiceTrigger.NeedHelp

            else -> VoiceTrigger.NoTrigger
        }
    }

    private fun extractWordFromMeaning(text: String): String {
        // "what does [word] mean"
        val pattern = Regex("""what does\s+(.+?)\s+mean""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun extractWordFromSpelling(text: String): String {
        // "how do you spell [word]" or "how to spell [word]"
        val pattern = Regex("""how (?:do you|to) spell\s+(.+)""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }
}
