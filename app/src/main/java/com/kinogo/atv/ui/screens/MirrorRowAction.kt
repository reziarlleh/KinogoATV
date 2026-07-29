package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.MirrorStatusUi

internal enum class MirrorRowAction {
    Select,
    ShowDetails,
}

internal fun MirrorStatusUi.rowAction(): MirrorRowAction = when (this) {
    MirrorStatusUi.Available -> MirrorRowAction.Select
    MirrorStatusUi.Active,
    MirrorStatusUi.Quarantined,
    MirrorStatusUi.Error,
    -> MirrorRowAction.ShowDetails
}
