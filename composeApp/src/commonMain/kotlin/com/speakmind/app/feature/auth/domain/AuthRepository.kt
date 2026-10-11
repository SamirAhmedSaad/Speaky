package com.speakmind.app.feature.auth.domain

import com.speakmind.app.core.domain.model.AuthResult
import com.speakmind.app.core.domain.model.AuthState
import com.speakmind.app.core.domain.model.User
import com.speakmind.app.core.network.BackendService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val backendService: BackendService,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: Flow<AuthState> = _authState.asStateFlow()

    private var cachedUser: User? = null

    suspend fun checkSession() {
        val user = backendService.getCurrentUser()
        if (user != null && user.id != "offline") {
            cachedUser = user
            _authState.value = AuthState.Authenticated(user)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String): AuthResult {
        val result = backendService.signUp(email, password, displayName)
        if (result is AuthResult.Success) {
            cachedUser = result.user
            _authState.value = AuthState.Authenticated(result.user)
        }
        return result
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        val result = backendService.signIn(email, password)
        if (result is AuthResult.Success) {
            cachedUser = result.user
            _authState.value = AuthState.Authenticated(result.user)
        }
        return result
    }

    suspend fun signOut() {
        backendService.signOut()
        cachedUser = null
        _authState.value = AuthState.Unauthenticated
    }

    fun continueOffline() {
        cachedUser = User(id = "offline", email = "", displayName = "Guest")
        _authState.value = AuthState.Authenticated(cachedUser!!)
    }

    fun getCurrentUser(): User? = cachedUser
}
