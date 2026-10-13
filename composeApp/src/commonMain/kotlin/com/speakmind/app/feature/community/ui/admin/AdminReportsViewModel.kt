package com.speakmind.app.feature.community.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.community.data.model.AdminReport
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.feature.community.data.repository.CommunityRepository
import com.speakmind.app.feature.community.data.repository.friendlyCommunityError
import com.speakmind.app.navigation.NavigationManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MODERATORS_TAB = "moderators"
val REPORT_FILTERS = listOf(
    "open" to "Open", "actioned" to "Banned", "dismissed" to "Dismissed", "all" to "All", MODERATORS_TAB to "Moderators",
)

data class AdminReportsUiState(
    val status: String = "open",
    val reports: List<AdminReport> = emptyList(),
    val moderators: List<CommunityUser> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val banTarget: AdminReport? = null,
    val info: String? = null,
    val error: String? = null,
)

/** Admin-only list of user reports with ban / dismiss actions. */
class AdminReportsViewModel(
    private val repository: CommunityRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReportsUiState())
    val uiState: StateFlow<AdminReportsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onStatusSelected(status: String) {
        _uiState.update { it.copy(status = status, reports = emptyList()) }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                if (_uiState.value.status == MODERATORS_TAB) {
                    val moderators = repository.adminModerators()
                    _uiState.update { it.copy(moderators = moderators, isLoading = false) }
                } else {
                    val reports = repository.adminReports(_uiState.value.status, PAGE, after = null)
                    _uiState.update { it.copy(reports = reports, isLoading = false, hasMore = reports.size >= PAGE) }
                }
            } catch (e: Exception) {
                Napier.w("adminReports failed", e)
                _uiState.update { it.copy(isLoading = false, error = friendlyCommunityError(e, "Couldn't load reports.")) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.status == MODERATORS_TAB || state.isLoading || state.isLoadingMore || !state.hasMore) return
        val last = state.reports.lastOrNull() ?: return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repository.adminReports(state.status, PAGE, after = last)
                _uiState.update {
                    it.copy(reports = (it.reports + page).distinctBy { r -> r.id }, hasMore = page.size >= PAGE, isLoadingMore = false)
                }
            } catch (e: Exception) {
                Napier.w("adminReports page failed", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onDismissReport(report: AdminReport) = act("Report dismissed.") {
        repository.adminSetReportStatus(report.id, "dismissed")
    }

    fun onBanRequested(report: AdminReport) = _uiState.update { it.copy(banTarget = report) }
    fun onBanCancelled() = _uiState.update { it.copy(banTarget = null) }

    fun onConfirmBan(removeMessages: Boolean) {
        val target = _uiState.value.banTarget ?: return
        _uiState.update { it.copy(banTarget = null) }
        act("${target.reportedNickname} is banned.") {
            repository.adminBan(target.reportedUserId, target.reason, removeMessages)
        }
    }

    fun onUnban(report: AdminReport) = act("${report.reportedNickname} is unbanned.") {
        repository.adminUnban(report.reportedUserId)
    }

    private fun act(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _uiState.update { it.copy(info = success) }
                load()
            } catch (e: Exception) {
                Napier.w("admin action failed", e)
                _uiState.update { it.copy(error = friendlyCommunityError(e, "Action failed.")) }
            }
        }
    }

    fun onRemoveModerator(user: CommunityUser) = act("${user.nickname} is no longer a moderator.") {
        repository.adminSetModerator(user.uid, false)
    }

    fun onMessageDismissed() = _uiState.update { it.copy(info = null, error = null) }
    fun onBack() = navigationManager.back()

    private companion object {
        const val PAGE = 30
    }
}
