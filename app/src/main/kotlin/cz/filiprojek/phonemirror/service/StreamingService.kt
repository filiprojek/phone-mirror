package cz.filiprojek.phonemirror.service

import androidx.lifecycle.LifecycleService

class StreamingService : LifecycleService() {
    companion object {
        const val ACTION_START = "cz.filiprojek.phonemirror.START"
        const val ACTION_STOP = "cz.filiprojek.phonemirror.STOP"
        const val ACTION_ALLOW = "cz.filiprojek.phonemirror.ALLOW"
        const val ACTION_DENY = "cz.filiprojek.phonemirror.DENY"
    }
}
