package com.speakmind.app.feature.community.di

import com.speakmind.app.feature.community.ui.admin.AdminChatViewModel
import com.speakmind.app.feature.community.ui.admin.AdminChatsViewModel
import com.speakmind.app.feature.community.ui.admin.AdminReportsViewModel
import com.speakmind.app.feature.community.ui.hub.CommunityHubViewModel
import com.speakmind.app.feature.community.ui.people.PeopleViewModel
import com.speakmind.app.feature.community.ui.room.RoomChatViewModel
import com.speakmind.app.feature.community.ui.setup.CommunitySetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val communityModule = module {
    single { com.speakmind.app.feature.community.call.HubCallRepository(get()) }
    single { com.speakmind.app.feature.community.call.HubCallManager(get()) }
    viewModel { com.speakmind.app.feature.community.call.AdminCallsViewModel(get(), get(), get()) }
    single { com.speakmind.app.feature.community.admin.AdminSubscribersRepository(get()) }
    viewModel { com.speakmind.app.feature.community.admin.AdminSubscribersViewModel(get(), get()) }
    viewModelOf(::CommunitySetupViewModel)
    viewModelOf(::CommunityHubViewModel)
    viewModelOf(::PeopleViewModel)
    viewModelOf(::AdminReportsViewModel)
    viewModelOf(::AdminChatsViewModel)
    viewModel { params -> AdminChatViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> RoomChatViewModel(params.get(), params.get(), get(), get(), get(), get()) }
}
