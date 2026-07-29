package com.kinogo.atv.player

/**
 * Swallows the release half of the key press that revealed the HUD.
 *
 * Compose may move focus to Play/Pause between ACTION_DOWN and ACTION_UP. Without this guard the
 * release can activate that newly focused button, turning one physical OK press into two actions.
 */
internal class PlayerKeyReleaseGuard {
    private var armedKeyCode: Int? = null

    fun arm(keyCode: Int) {
        armedKeyCode = keyCode
    }

    fun consumeRelease(keyCode: Int): Boolean {
        if (armedKeyCode != keyCode) return false
        armedKeyCode = null
        return true
    }
}
