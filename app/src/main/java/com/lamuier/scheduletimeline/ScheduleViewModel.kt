package com.lamuier.scheduletimeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamuier.scheduletimeline.data.NearestScheduleHint
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduleExport
import com.lamuier.scheduletimeline.data.ScheduleRepository
import com.lamuier.scheduletimeline.data.TeamNames
import com.lamuier.scheduletimeline.data.ThemeMode
import com.lamuier.scheduletimeline.data.ThemePreferences
import com.lamuier.scheduletimeline.data.TimelineBuilder
import com.lamuier.scheduletimeline.data.TimelineItem
import com.lamuier.scheduletimeline.data.TokutenKind
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.Category
import com.lamuier.scheduletimeline.data.teamNames
import com.lamuier.scheduletimeline.ui.edit.EditUiState
import com.lamuier.scheduletimeline.ui.edit.EditValidationError

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ScheduleDayState(
    val date: LocalDate,
    val events: List<ScheduleEvent>,
)

/** 分页日期流缓存上限：覆盖视口相邻页与近期回访即可，避免无限累积订阅。 */
private const val DAY_FLOW_CACHE_CAPACITY = 12

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(
    application: Application,
    private val repository: ScheduleRepository,
    private val themePreferences: ThemePreferences,
) : AndroidViewModel(application) {

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themePreferences.mode
    val notificationsEnabled: StateFlow<Boolean> =
        (application as ScheduleApplication).notificationPreferences.enabled
    val liveUpdatesAlwaysOn: StateFlow<Boolean> =
        (application as ScheduleApplication).notificationPreferences.alwaysOn

    val dayState: StateFlow<ScheduleDayState> = _currentDate
        .flatMapLatest { date ->
            repository.observeDay(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .map { events -> ScheduleDayState(date, events) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ScheduleDayState(LocalDate.now(), emptyList()),
        )

    val events: StateFlow<List<ScheduleEvent>> = dayState
        .map { it.events }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timelineItems: StateFlow<List<TimelineItem>> = dayState
        .map { TimelineBuilder.build(it.events) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 团队候选（复用 categories 表）。 */
    val teams: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editUiState = MutableStateFlow(EditUiState())
    val editUiState: StateFlow<EditUiState> = _editUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            refreshNotifications()
        }
    }

    fun changeDate(date: LocalDate) {
        _currentDate.value = date
    }

    /**
     * 任意日期的事件流，供分页时间轴的每一页独立订阅。
     * 按日期缓存共享 StateFlow（LRU 保留最近访问的若干天）：
     * 分页回访时 `value` 即上次已加载的数据，避免每次重组都从空态闪变到真实内容。
     * WhileSubscribed 保证页面滑出后上游查询自动停止，`.value` 仍保留最后结果。
     */
    private val dayFlows = object : LinkedHashMap<LocalDate, StateFlow<ScheduleDayState>>(
        DAY_FLOW_CACHE_CAPACITY, 0.75f, true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<LocalDate, StateFlow<ScheduleDayState>>?,
        ): Boolean = size > DAY_FLOW_CACHE_CAPACITY
    }

    fun observeDayState(date: LocalDate): StateFlow<ScheduleDayState> =
        dayFlows.getOrPut(date) {
            repository.observeDay(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .map { events -> ScheduleDayState(date, events) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    ScheduleDayState(date, emptyList()),
                )
        }

    fun shiftDate(days: Long) {
        _currentDate.value = _currentDate.value.plusDays(days)
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setMode(mode)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        (getApplication<ScheduleApplication>()).notificationPreferences.setEnabled(enabled)
        refreshNotifications()
    }

    fun setLiveUpdatesAlwaysOn(enabled: Boolean) {
        val app = getApplication<ScheduleApplication>()
        app.notificationPreferences.setAlwaysOn(enabled)
        if (enabled) app.notificationPreferences.setEnabled(true)
        refreshNotifications()
    }

    fun deleteTeam(name: String) {
        viewModelScope.launch { repository.deleteTeam(name) }
        _editUiState.update { state ->
            val pending = TeamNames.parseInput(state.teamInput).filterNot { it == name }
            state.copy(
                teamNames = state.teamNames.filterNot { it == name },
                teamInput = pending.joinToString(" / "),
            )
        }
    }

    fun batchImport(csvText: String) {
        viewModelScope.launch {
            val drafts = ScheduleExport.parseImportDrafts(csvText, fallbackDayKey = currentDayKey())
            repository.importDrafts(drafts)
            refreshNotifications()
        }
    }

    fun clearAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAll()
            refreshNotifications()
            onDone()
        }
    }

    fun clearDayData(date: LocalDate, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearDay(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            refreshNotifications()
            onDone()
        }
    }

    suspend fun exportCurrentDayCsv(): String {
        val events = repository.eventsForDay(currentDayKey())
        return ScheduleExport.toCsv(events)
    }

    fun prepareEdit(eventId: Long?) {
        viewModelScope.launch {
            if (eventId == null) {
                _editUiState.value = EditUiState(isNew = true)
                return@launch
            }
            val existing = repository.get(eventId) ?: run {
                _editUiState.value = EditUiState(isNew = true)
                return@launch
            }
            val type = EventType.fromStorage(existing.eventType)
            _editUiState.value = EditUiState(
                loadedId = existing.id,
                teamNames = existing.teamNames.ifEmpty {
                    listOf(existing.title).filter { it.isNotBlank() }
                },
                eventType = type,
                tokutenKind = TokutenKind.fromStorage(existing.tokutenKind) ?: TokutenKind.PARALLEL,
                title = existing.title,
                location = existing.location,
                startMinutes = existing.startMinutes,
                endMinutes = existing.endMinutes,
                note = existing.note,
                isNew = false,
            )
        }
    }

    fun updateEdit(transform: (EditUiState) -> EditUiState) {
        _editUiState.update { transform(it).copy(error = null) }
    }

    fun setEditEventType(type: EventType) {
        _editUiState.update { state ->
            val selected = if (type == EventType.PERFORMANCE) {
                state.effectiveTeamNames().take(1)
            } else {
                state.effectiveTeamNames()
            }
            state.copy(
                eventType = type,
                teamNames = selected,
                teamInput = "",
                error = null,
            )
        }
    }

    fun setEditTeamInput(value: String) {
        _editUiState.update { it.copy(teamInput = value, error = null) }
    }

    fun toggleEditTeam(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        _editUiState.update { state ->
            val next = if (state.eventType == EventType.PERFORMANCE) {
                if (state.teamNames.singleOrNull() == normalized) emptyList() else listOf(normalized)
            } else {
                state.teamNames.toMutableList().apply {
                    if (normalized in this) remove(normalized) else add(normalized)
                }
            }
            state.copy(teamNames = TeamNames.normalize(next), error = null)
        }
    }

    fun addEditTeamInput() {
        _editUiState.update { state ->
            val additions = TeamNames.parseInput(state.teamInput)
            if (additions.isEmpty()) return@update state
            val next = if (state.eventType == EventType.PERFORMANCE) {
                listOf(additions.first())
            } else {
                TeamNames.normalize(state.teamNames + additions)
            }
            state.copy(teamNames = next, teamInput = "", error = null)
        }
    }

    fun setStartMinutes(minutes: Int) {
        _editUiState.update { state ->
            val duration = state.endMinutes - state.startMinutes
            state.copy(
                startMinutes = minutes,
                endMinutes = (minutes + duration).coerceAtMost(24 * 60 - 1),
                error = null,
            )
        }
    }

    fun setEndMinutes(minutes: Int) {
        _editUiState.update { it.copy(endMinutes = minutes, error = null) }
    }

    fun saveEdit(onDone: () -> Unit = {}) {
        val state = _editUiState.value
        val teamNames = state.effectiveTeamNames()
        val error = when {
            teamNames.isEmpty() -> EditValidationError.BlankTeam
            state.eventType == EventType.TOKUTEN &&
                TokutenKind.fromStorage(state.tokutenKind.storage) == null ->
                EditValidationError.MissingTokutenKind
            state.endMinutes <= state.startMinutes -> EditValidationError.EndBeforeStart
            else -> null
        }
        if (error != null) {
            _editUiState.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            repository.save(
                ScheduleEvent(
                    id = state.loadedId,
                    team = TeamNames.encode(teamNames),
                    eventType = state.eventType.storage,
                    tokutenKind = if (state.eventType == EventType.TOKUTEN) {
                        state.tokutenKind.storage
                    } else {
                        ""
                    },
                    linkedPerformanceId = null,
                    title = state.title.trim(),
                    category = "",
                    location = state.location.trim(),
                    startMinutes = state.startMinutes,
                    endMinutes = state.endMinutes,
                    note = state.note.trim(),
                    dayKey = currentDayKey(),
                ),
            )
            refreshNotifications()
            onDone()
        }
    }

    fun delete(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.delete(id)
            refreshNotifications()
            onDone()
        }
    }

    suspend fun findNearestScheduleHint(): NearestScheduleHint? {
        return ScheduleExport.nearestScheduleHint(
            from = _currentDate.value,
            dayKeys = repository.distinctDayKeys(),
        )
    }

    private fun currentDayKey(): String =
        _currentDate.value.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun refreshNotifications() {
        getApplication<ScheduleApplication>().refreshNotifications()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            val app = application as ScheduleApplication
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScheduleViewModel(app, app.repository, app.themePreferences) as T
                }
            }
        }
    }
}
