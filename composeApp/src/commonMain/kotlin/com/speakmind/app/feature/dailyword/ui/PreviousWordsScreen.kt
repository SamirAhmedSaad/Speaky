package com.speakmind.app.feature.dailyword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.dailyword.data.DailyWordRepository
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.navigation.WordDetailDestination
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

/** Every daily word (and word saved from Lookup) of the recent days, newest first. */
@Serializable
data object PreviousWordsDestination

data class PreviousWordsUiState(
    val words: List<DailyWordData> = emptyList(),
    val isLoading: Boolean = true,
)

class PreviousWordsViewModel(
    private val repository: DailyWordRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreviousWordsUiState())
    val uiState: StateFlow<PreviousWordsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = PreviousWordsUiState(words = repository.getRecentWords(), isLoading = false)
        }
    }

    fun onWordClick(id: Long) = navigationManager.navigate(WordDetailDestination(wordId = id))

    fun onBack() = navigationManager.back()
}

fun NavGraphBuilder.previousWordsScreen() {
    animatedComposable<PreviousWordsDestination> {
        val viewModel = koinViewModel<PreviousWordsViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        PreviousWordsContent(uiState, viewModel::onWordClick, viewModel::onBack)
    }
}

@Composable
private fun PreviousWordsContent(
    uiState: PreviousWordsUiState,
    onWordClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val groups = uiState.words.groupBy { it.sentDate }.toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeakyBackButton(onClick = onBack)
            Text("Previous words", Modifier.weight(1f), style = displayStyle(26.sp), color = colors.textPrimary)
            if (uiState.words.isNotEmpty()) {
                Text("${uiState.words.size} words", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.neonCyan)
            }
            uiState.words.isEmpty() -> Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Your daily words will collect here.",
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                groups.forEach { (date, words) ->
                    item(key = "day-$date") {
                        Text(
                            dayLabel(date, today),
                            modifier = Modifier.padding(top = 6.dp),
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(words, key = { it.id }) { word -> PreviousWordRow(word) { onWordClick(word.id) } }
                }
            }
        }
    }
}

@Composable
private fun PreviousWordRow(word: DailyWordData, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Filled dot = not opened yet.
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (word.isRead) colors.neonCyan.copy(alpha = 0.22f) else colors.neonCyan),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(word.word, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (word.meaning.isNotBlank()) {
                Text(
                    word.meaning,
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Words saved from Word Lookup have no level.
        val hasLevel = word.level.isNotBlank()
        Text(
            if (hasLevel) word.level else "Saved",
            color = if (hasLevel) colors.onGoldSoft else colors.onMintSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (hasLevel) colors.goldSoft else colors.mintSoft)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun dayLabel(sentDate: String, today: LocalDate): String {
    val date = runCatching { LocalDate.parse(sentDate) }.getOrNull() ?: return sentDate.uppercase()
    return when (date) {
        today -> "TODAY"
        today.minus(DatePeriod(days = 1)) -> "YESTERDAY"
        else -> "${date.dayOfWeek.name.take(3)} ${date.dayOfMonth} ${date.month.name.take(3)}"
    }
}
