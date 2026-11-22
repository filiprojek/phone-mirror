package cz.filiprojek.phonemirror

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cz.filiprojek.phonemirror.ui.HomeScreen
import cz.filiprojek.phonemirror.ui.NavViewModel
import cz.filiprojek.phonemirror.ui.PhoneMirrorTheme
import cz.filiprojek.phonemirror.ui.Screen
import cz.filiprojek.phonemirror.ui.ScreenScope
import cz.filiprojek.phonemirror.ui.camera.CameraScreen
import cz.filiprojek.phonemirror.ui.connect.ConnectScreen
import cz.filiprojek.phonemirror.ui.connect.ScanScreen
import cz.filiprojek.phonemirror.ui.viewer.ViewerScreen

class MainActivity : ComponentActivity() {
    private val nav: NavViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Requirement: the screen must not lock while the app is open (both roles).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            PhoneMirrorTheme {
                BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
                when (val screen = nav.current) {
                    Screen.Home -> HomeScreen(
                        onCamera = { nav.push(Screen.Camera) },
                        onDisplay = { nav.push(Screen.Connect) },
                    )
                    Screen.Camera -> CameraScreen(onExit = { if (!nav.pop()) finish() })
                    Screen.Connect -> ConnectScreen(
                        onConnect = { nav.push(Screen.Viewer(it)) },
                        onScan = { nav.push(Screen.Scan) },
                        onBack = { nav.pop() },
                    )
                    Screen.Scan -> ScanScreen(onResult = { nav.replace(Screen.Viewer(it)) }, onBack = { nav.pop() })
                    is Screen.Viewer -> ScreenScope(screen) { ViewerScreen(screen.target, onExit = { nav.pop() }) }
                }
            }
        }
    }
}
