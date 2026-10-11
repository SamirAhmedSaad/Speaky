package com.speakmind.app.feature.community.di

import com.speakmind.app.feature.community.ui.auth.CommunityAuthViewModel
import com.speakmind.app.feature.community.ui.hub.CommunityHubViewModel
import com.speakmind.app.feature.community.ui.room.RoomChatViewModel
import com.speakmind.app.feature.community.ui.setup.CommunitySetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val communityModule = module {
    viewModelOf(::CommunitySetupViewModel)
    viewModelOf(::CommunityAuthViewModel)
    viewModelOf(::CommunityHubViewModel)
    viewModel { params -> RoomChatViewModel(params.get(), params.get(), get(), get(), get(), get()) }
}
