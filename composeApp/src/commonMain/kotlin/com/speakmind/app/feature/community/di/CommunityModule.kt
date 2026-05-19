package com.speakmind.app.feature.community.di

import com.speakmind.app.feature.community.ui.chat.PrivateChatViewModel
import com.speakmind.app.feature.community.ui.setup.CommunitySetupViewModel
import com.speakmind.app.feature.community.ui.users.CommunityUsersViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val communityModule = module {
    viewModelOf(::CommunitySetupViewModel)
    viewModelOf(::CommunityUsersViewModel)
    viewModel { params ->
        PrivateChatViewModel(
            communityRepository = get(),
            navigationManager = get(),
            otherUserId = params.get(),
        )
    }
}
