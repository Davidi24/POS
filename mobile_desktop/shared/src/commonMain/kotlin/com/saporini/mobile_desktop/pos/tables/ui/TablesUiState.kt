package com.saporini.mobile_desktop.pos.tables.ui

import com.saporini.mobile_desktop.pos.tables.domain.model.BranchTableLayout
import com.saporini.mobile_desktop.pos.tables.domain.model.FloorLayout

data class TablesUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val floorLayouts: List<FloorLayout> = emptyList(),
    val planImageBytes: Map<String, ByteArray> = emptyMap(),
    val tableLayout: BranchTableLayout? = null,
    val errorMessage: String? = null
) {
    val hasData: Boolean
        get() = floorLayouts.isNotEmpty() ||
                tableLayout?.tables?.isNotEmpty() == true
}
