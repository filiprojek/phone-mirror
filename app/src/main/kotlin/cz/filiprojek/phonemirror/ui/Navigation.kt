package cz.filiprojek.phonemirror.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cz.filiprojek.phonemirror.core.util.ConnectTarget
import cz.filiprojek.phonemirror.service.CameraHub

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data object Connect : Screen
    data object Scan : Screen
    data class Viewer(val target: ConnectTarget) : Screen
}

/** Tiny back stack that survives configuration changes. */
class NavViewModel : ViewModel() {
    var stack by mutableStateOf(listOf(if (CameraHub.running.value) Screen.Camera else Screen.Home))
        private set

    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun replace(screen: Screen) {
        stack = stack.dropLast(1) + screen
    }

    /** Returns false when already at the root (let the system finish the activity). */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }
}
