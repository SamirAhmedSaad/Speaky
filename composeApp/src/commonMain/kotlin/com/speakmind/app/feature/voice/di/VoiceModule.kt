package com.speakmind.app.feature.voice.di

import com.speakmind.app.feature.voice.platform.SpeechRecognizerEngine
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import org.koin.dsl.module

val voiceModule = module {
    single { SpeechRecognizerEngine() }
    single { TextToSpeechEngine() }
}
