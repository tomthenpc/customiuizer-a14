package tv.withaibuild.customiuizer.mods.utils.gesture

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarGestureEffectExecutorTest {

    private val expectedFlags = (1 shl 12) or
        AudioManager.FLAG_SHOW_UI or
        AudioManager.FLAG_ALLOW_RINGER_MODES or
        AudioManager.FLAG_PLAY_SOUND or
        AudioManager.FLAG_VIBRATE

    @Test
    fun legacyVolumeFlagsPreserved() {
        assertEquals(expectedFlags, StatusBarGestureEffectExecutor.VOLUME_FLAGS)
    }
}
