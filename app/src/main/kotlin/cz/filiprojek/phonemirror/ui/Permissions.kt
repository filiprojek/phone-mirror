package cz.filiprojek.phonemirror.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Requests [permissions] once; returns whether all non-optional ones are granted. */
@Composable
private fun rememberPermissions(permissions: List<String>): Boolean {
    val context = LocalContext.current
    fun granted() = permissions.filterNot { it in optionalPermissions }
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = granted()
    }
    LaunchedEffect(Unit) {
        val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
    return ok
}

enum class Role { CAMERA, DISPLAY, SCANNER }

/** Permissions the given [role] must request at runtime on Android [sdkInt]. */
fun requiredPermissions(role: Role, sdkInt: Int): List<String> = buildList {
    if (role == Role.CAMERA || role == Role.SCANNER) add(Manifest.permission.CAMERA)
    // Android 17 gates LAN sockets and NSD behind a runtime permission for apps targeting it.
    if (role != Role.SCANNER && sdkInt >= ACCESS_LOCAL_NETWORK_SDK) add(ACCESS_LOCAL_NETWORK)
    if (role == Role.CAMERA && sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/** Permissions whose denial just degrades the experience instead of blocking the role. */
private val optionalPermissions = setOf(Manifest.permission.POST_NOTIFICATIONS)

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ACCESS_LOCAL_NETWORK_SDK = 37

/** Requests everything [role] needs; returns whether all non-optional permissions are granted. */
@Composable
fun rememberRolePermissions(role: Role): Boolean {
    val permissions = remember(role) { requiredPermissions(role, Build.VERSION.SDK_INT) }
    return rememberPermissions(permissions)
}

/** Locks the activity to portrait while this composable is shown. */
@Composable
fun LockPortrait() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
