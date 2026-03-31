package com.speakmind.app.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface NavCommand {
    data class To(
        val destination: Any,
        val navOptions: (NavOptionsBuilder.() -> Unit)? = null
    ) : NavCommand

    data object Back : NavCommand
    data class PopUpTo(val destination: kotlin.reflect.KClass<*>, val inclusive: Boolean = false) : NavCommand
    data class ClearStackAndNavigate(val destination: Any) : NavCommand
}

@Stable
class NavigationManager {
    private val _commands = MutableSharedFlow<NavCommand>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    fun navigate(destination: Any, navOptions: (NavOptionsBuilder.() -> Unit)? = null) {
        _commands.tryEmit(NavCommand.To(destination, navOptions))
    }

    fun back() {
        _commands.tryEmit(NavCommand.Back)
    }

    fun popUpTo(destination: kotlin.reflect.KClass<*>, inclusive: Boolean = false) {
        _commands.tryEmit(NavCommand.PopUpTo(destination, inclusive))
    }

    fun clearStackAndNavigate(destination: Any) {
        _commands.tryEmit(NavCommand.ClearStackAndNavigate(destination))
    }
}
