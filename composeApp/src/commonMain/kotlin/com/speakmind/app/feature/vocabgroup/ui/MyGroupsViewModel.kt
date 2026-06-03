package com.speakmind.app.feature.vocabgroup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.feature.vocabgroup.data.VocabGroupRepository
import com.speakmind.app.feature.vocabgroup.domain.model.VocabGroup
import com.speakmind.app.navigation.GroupDetailDestination
import com.speakmind.app.navigation.NavigationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MyGroupsUiState(
    val groups: List<VocabGroup> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
)

class MyGroupsViewModel(
    private val repository: VocabGroupRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyGroupsUiState())
    val uiState: StateFlow<MyGroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            val groups = repository.getGroups()
            _uiState.value = _uiState.value.copy(groups = groups, isLoading = false)
        }
    }

    fun onShowCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun onDismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun onCreateGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createGroup(name.trim())
            _uiState.value = _uiState.value.copy(showCreateDialog = false)
            loadGroups()
            navigationManager.navigate(GroupDetailDestination(groupId = id, groupName = name.trim()))
        }
    }

    fun onGroupClicked(group: VocabGroup) {
        navigationManager.navigate(GroupDetailDestination(groupId = group.id, groupName = group.name))
    }

    fun onDeleteGroup(id: Long) {
        viewModelScope.launch {
            repository.deleteGroup(id)
            loadGroups()
        }
    }

    fun refresh() {
        loadGroups()
    }

    fun onBack() {
        navigationManager.back()
    }
}
