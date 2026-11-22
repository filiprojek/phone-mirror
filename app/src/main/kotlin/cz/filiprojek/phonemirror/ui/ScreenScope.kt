package cz.filiprojek.phonemirror.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** Gives [content] its own ViewModelStore, cleared (ViewModel.onCleared) when the screen leaves. */
@Composable
fun ScreenScope(key: Any, content: @Composable () -> Unit) {
    val store = remember(key) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
