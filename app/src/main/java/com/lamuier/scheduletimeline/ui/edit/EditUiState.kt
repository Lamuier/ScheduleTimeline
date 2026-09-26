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
        val pending = TeamNames.parseInput(teamInput)
        val selected = TeamNames.normalize(teamNames)
        // 演出手动输入仍只保留最后一项。已选中的多个团队（同时段自动合并的结果）
        // 在没有新输入时原样保存，避免打开编辑页再保存时丢掉其余团队。
        return if (eventType == EventType.PERFORMANCE && pending.isNotEmpty()) {
            listOf(pending.last())
        } else {
            TeamNames.normalize(selected + pending)
        }
    }
}

enum class EditValidationError {
    BlankTeam,
    MissingTokutenKind,
    EndBeforeStart,
}
