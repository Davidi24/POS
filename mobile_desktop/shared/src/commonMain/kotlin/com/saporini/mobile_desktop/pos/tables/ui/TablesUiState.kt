package com.saporini.mobile_desktop.pos.tables.ui

import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout

data class TablesUiState(
    val isLoading: Boolean = false,
    // The first load has answered (or failed), so an empty floor really is empty.
    val loaded: Boolean = false,
    val isSaving: Boolean = false,
    val floorLayouts: List<FloorLayout> = emptyList(),
    val planImageBytes: Map<String, ByteArray> = emptyMap(),
    val tableLayout: BranchTableLayout? = null,
    val errorMessage: String? = null,
    // What the system did by itself lately, to see it and return it.
    val specialActions: List<com.saporini.mobile_desktop.pos.tables.domain.model.SpecialCaseAction> = emptyList()
) {
    val hasData: Boolean
        get() = floorLayouts.isNotEmpty() ||
                tableLayout?.tables?.isNotEmpty() == true
}
