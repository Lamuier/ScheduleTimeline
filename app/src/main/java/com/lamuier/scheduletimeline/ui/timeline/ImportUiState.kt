package com.lamuier.scheduletimeline.ui.timeline

import androidx.lifecycle.SavedStateHandle
import com.lamuier.scheduletimeline.data.ImportDraft
import com.lamuier.scheduletimeline.data.ImportIssue
import com.lamuier.scheduletimeline.data.ImportParseResult
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduleExport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val text: String = "",
    val fallbackDayKey: String,
    val inputVersion: Long = 0,
    val parsedVersion: Long? = null,
    val parsing: Boolean = false,
    val drafts: List<ImportDraft> = emptyList(),
    val previewEvents: List<ScheduleEvent> = emptyList(),
    val issues: List<ImportIssue> = emptyList(),
    val selectedIndexes: Set<Int> = emptySet(),
) {
    val canImport: Boolean get() = !parsing && parsedVersion == inputVersion && selectedIndexes.isNotEmpty()
}

/** Text and selection survive process recreation; parsed objects are rebuilt off the main thread. */
internal class ImportStateController(
    private val scope: CoroutineScope,
    private val savedState: SavedStateHandle,
    fallbackDayKey: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val parse: suspend (String, String) -> ImportParseResult = ScheduleExport::parseImportResult,
) {
    private val restoredVersion = (savedState.get<Long>(VERSION) ?: 0L) +
        if (savedState.get<String>(DAY)?.let { it != fallbackDayKey } == true) 1 else 0
    private var restoredSelection = savedState.get<IntArray>(SELECTION)?.takeIf {
        savedState.get<Long>(SELECTION_VERSION) == restoredVersion
    }?.toSet()
    private val initialText = savedState.get<String>(TEXT).orEmpty()
    private val mutableState = MutableStateFlow(ImportUiState(
        text = initialText, fallbackDayKey = fallbackDayKey, inputVersion = restoredVersion,
        parsedVersion = restoredVersion.takeIf { initialText.isEmpty() }, parsing = initialText.isNotEmpty(),
    ))
    val state = mutableState.asStateFlow()
    private var parseJob: Job? = null

    init {
        persistInput()
        if (initialText.isNotEmpty()) parseLatest()
    }

    fun updateText(text: String) {
        val current = mutableState.value
        if (current.text == text) return
        restoredSelection = null
        mutableState.value = current.copy(text = text, inputVersion = current.inputVersion + 1, parsing = true)
        persistInput()
        parseLatest()
    }

    fun updateDay(dayKey: String) {
        val current = mutableState.value
        if (current.fallbackDayKey == dayKey) return
        restoredSelection = null
        mutableState.value = current.copy(fallbackDayKey = dayKey,
            inputVersion = current.inputVersion + 1, parsing = true)
        persistInput()
        parseLatest()
    }

    fun toggle(index: Int) {
        val current = mutableState.value
        if (current.parsing || index !in current.drafts.indices) return
        mutableState.value = current.copy(selectedIndexes = if (index in current.selectedIndexes)
            current.selectedIndexes - index else current.selectedIndexes + index)
        persistSelection()
    }

    fun selectAll(selected: Boolean) {
        val current = mutableState.value
        if (current.parsing) return
        mutableState.value = current.copy(selectedIndexes = if (selected) current.drafts.indices.toSet() else emptySet())
        persistSelection()
    }

    fun selectedDrafts(expectedVersion: Long): List<ImportDraft>? {
        val current = mutableState.value
        if (!current.canImport || current.inputVersion != expectedVersion) return null
        return current.drafts.filterIndexed { index, _ -> index in current.selectedIndexes }
    }

    private fun parseLatest() {
        parseJob?.cancel()
        val input = mutableState.value
        parseJob = scope.launch {
            val result = withContext(dispatcher) { parse(input.text, input.fallbackDayKey) }
            ensureActive()
            val current = mutableState.value
            if (current.inputVersion != input.inputVersion) return@launch
            val events = result.drafts.map { it.event }
            val selected = restoredSelection?.filterTo(linkedSetOf()) { it in events.indices }
                ?: if (events == current.previewEvents) current.selectedIndexes else events.indices.toSet()
            restoredSelection = null
            mutableState.value = current.copy(parsedVersion = input.inputVersion, parsing = false,
                drafts = result.drafts, previewEvents = events, issues = result.issues, selectedIndexes = selected)
            persistSelection()
        }
    }

    private fun persistInput() {
        val current = mutableState.value
        savedState[TEXT] = current.text
        savedState[DAY] = current.fallbackDayKey
        savedState[VERSION] = current.inputVersion
    }

    private fun persistSelection() {
        val current = mutableState.value
        savedState[SELECTION] = current.selectedIndexes.toIntArray()
        savedState[SELECTION_VERSION] = current.parsedVersion
    }

    private companion object {
        const val TEXT = "importText"
        const val DAY = "importDay"
        const val VERSION = "importVersion"
        const val SELECTION = "importSelection"
        const val SELECTION_VERSION = "importSelectionVersion"
    }
}
