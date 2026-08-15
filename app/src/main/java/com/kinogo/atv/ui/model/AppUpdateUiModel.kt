package com.kinogo.atv.ui.model

enum class AppUpdateUiPhase {
    IDLE,
    CHECKING,
    CURRENT,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR,
}

data class AppUpdateUiModel(
    val currentVersion: String,
    val phase: AppUpdateUiPhase = AppUpdateUiPhase.IDLE,
    val status: String = "Обновления ещё не проверялись",
    val availableVersion: String? = null,
    val actionLabel: String? = "Проверить",
    val actionEnabled: Boolean = true,
)
