package com.speakmind.app.navigation

import org.koin.core.module.Module
import org.koin.dsl.module

val navigationModule: Module = module {
    single { NavigationManager() }
}
