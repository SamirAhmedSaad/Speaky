package com.speakmind.app.feature.learning.di

import com.speakmind.app.feature.learning.data.MistakeTracker
import com.speakmind.app.feature.learning.domain.AdaptiveDifficultyManager
import com.speakmind.app.feature.learning.domain.StreakTracker
import org.koin.dsl.module

val learningModule = module {
    single { StreakTracker(get()) }
    single { MistakeTracker(get()) }
    single { AdaptiveDifficultyManager(get()) }
}
