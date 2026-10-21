package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSURL

// System alarm sound, used unless the app bundles its own find_my_phone.caf.
private const val SYSTEM_ALARM_SOUND = "/System/Library/Audio/UISounds/alarm.caf"

actual class PhoneSoundPlayer {
    private val logger = Logger.withTag("PhoneSoundPlayer")
    private val _isRinging = MutableStateFlow(false)
    actual val isRinging: Flow<Boolean> = _isRinging.asStateFlow()

    private var player: AVAudioPlayer? = null

    actual fun startRinging() {
        if (_isRinging.value) return
        try {
            // Playback: full volume, ignores the silent switch. It still follows the active
            // output route, though — iOS has no public API to force the built-in speaker over a
            // connected headset/Bluetooth sink, so it rings there if one is connected.
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                val session = AVAudioSession.sharedInstance()
                session.setCategory(
                    AVAudioSessionCategoryPlayback,
                    withOptions = AVAudioSessionCategoryOptionDuckOthers,
                    error = err.ptr,
                )
                session.setActive(true, error = err.ptr)
            }
            val url = NSBundle.mainBundle.URLForResource("find_my_phone", withExtension = "caf")
                ?: NSURL.fileURLWithPath(SYSTEM_ALARM_SOUND)
            player = memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                AVAudioPlayer(contentsOfURL = url, error = err.ptr)
            }.apply {
                numberOfLoops = -1
                volume = 1.0f
                prepareToPlay()
                play()
            }
            _isRinging.value = true
        } catch (e: Exception) {
            logger.w(e) { "Failed to start ringing" }
            cleanUp()
        }
    }

    actual fun stopRinging() {
        if (!_isRinging.value) return
        cleanUp()
    }

    private fun cleanUp() {
        player?.stop()
        player = null
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            AVAudioSession.sharedInstance().setActive(false, error = err.ptr)
        }
        _isRinging.value = false
    }
}
