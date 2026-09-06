package com.kinogo.atv.data.network

import com.kinogo.atv.BuildConfig

/** Keeps every outbound client identifiable by the exact application version. */
internal fun kinogoUserAgent(component: String): String {
    require(component.isNotBlank())
    return "KinogoATV/${BuildConfig.VERSION_NAME} (Android TV; $component)"
}
