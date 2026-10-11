package com.speakmind.app.feature.community.di

import com.speakmind.app.feature.community.ui.admin.AdminReportsViewModel
import com.speakmind.app.feature.community.ui.hub.CommunityHubViewModel
import com.speakmind.app.feature.community.ui.people.PeopleViewModel
import com.speakmind.app.feature.community.ui.room.RoomChatViewModel
import com.speakmind.app.feature.community.ui.setup.CommunitySetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val communityModule = module {
    viewModelOf(::CommunitySetupViewModel)
    viewModelOf(::CommunityHubViewModel)
    viewModelOf(::PeopleViewModel)
    viewModelOf(::AdminReportsViewModel)
    viewModel { params -> RoomChatViewModel(params.get(), params.get(), get(), get(), get(), get()) }
}
