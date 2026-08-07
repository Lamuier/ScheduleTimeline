package com.lamuier.scheduletimeline.ui.edit

import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.TeamNames
import com.lamuier.scheduletimeline.data.TokutenKind

data class EditUiState(
    val loadedId: Long = 0L,
    /** 已确认选择的团队；演出为单选，特典可多选。 */
    val teamNames: List<String> = emptyList(),
    /** 尚未加入选择集的手动输入。保存时也会自动合并。 */
    val teamInput: String = "",
    val eventType: EventType = EventType.PERFORMANCE,
    val tokutenKind: TokutenKind = TokutenKind.PARALLEL,
    /** 可选场次说明。 */
    val title: String = "",
    val location: String = "",
    val startMinutes: Int = 14 * 60,
    val endMinutes: Int = 15 * 60,
    val note: String = "",
    val error: EditValidationError? = null,
    val isNew: Boolean = true,
) {
    fun effectiveTeamNames(): List<String> {
        val normalized = TeamNames.normalize(teamNames + TeamNames.parseInput(teamInput))
        return if (eventType == EventType.PERFORMANCE) normalized.takeLast(1) else normalized
    }
}

enum class EditValidationError {
    BlankTeam,
    MissingTokutenKind,
    EndBeforeStart,
}
