package com.speakmind.app.di

import com.speakmind.app.feature.ai.di.aiModule
import com.speakmind.app.feature.aisetup.di.aiSetupModule
import com.speakmind.app.feature.article.di.articleModule
import com.speakmind.app.feature.chat.di.chatModule
import com.speakmind.app.feature.download.di.downloadModule
import com.speakmind.app.feature.flashcard.di.flashcardModule
import com.speakmind.app.feature.home.di.homeModule
import com.speakmind.app.feature.story.di.storyModule
import com.speakmind.app.feature.dailyword.di.dailyWordModule
import com.speakmind.app.feature.vocabulary.di.vocabularyModule
import com.speakmind.app.feature.onboarding.di.onboardingModule
import com.speakmind.app.feature.learning.di.learningModule
import com.speakmind.app.feature.community.di.communityModule
import com.speakmind.app.feature.wordlookup.di.wordLookupModule
import com.speakmind.app.feature.voice.di.voiceModule
import com.speakmind.app.feature.vocabgroup.di.vocabGroupModule
import com.speakmind.app.navigation.navigationModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initKoin(config: (KoinApplication.() -> Unit)? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            appModule,
            databaseModule,
            navigationModule,
            aiModule,
            voiceModule,
            learningModule,
            onboardingModule,
            homeModule,
            chatModule,
            downloadModule,
            aiSetupModule,
            flashcardModule,
            vocabularyModule,
            dailyWordModule,
            storyModule,
            articleModule,
            wordLookupModule,
            communityModule,
            vocabGroupModule,
        )
    }
}
