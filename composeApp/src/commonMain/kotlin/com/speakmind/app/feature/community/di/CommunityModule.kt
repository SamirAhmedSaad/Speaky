package com.speakmind.app.feature.community.di

import com.speakmind.app.feature.community.ui.chat.ChannelViewModel
import com.speakmind.app.feature.community.ui.setup.CommunitySetupViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val communityModule = module {
    viewModelOf(::CommunitySetupViewModel)
    viewModelOf(::ChannelViewModel)
}
