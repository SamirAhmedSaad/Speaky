package com.speakmind.app.feature.community.ui.components

import androidx.compose.runtime.Composable

/**
 * While on screen, the window resizes for the keyboard instead of panning,
 * so the chat header stays visible and imePadding() lifts the input bar.
 */
@Composable
expect fun ResizeWindowForKeyboard()
