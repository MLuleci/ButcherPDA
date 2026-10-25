package bg.butcher.pda.hardware

import android.content.Context
import android.media.SoundPool
import bg.butcher.pda.R

/** Sounds for scans: a batch was accepted, or the trigger read nothing. */
class ScanFeedback(context: Context) {
    private val sounds = SoundPool.Builder().setMaxStreams(1).build()
    private val acceptedSound = sounds.load(context, R.raw.beep1, 1)
    private val failedSound = sounds.load(context, R.raw.beep3, 1)

    fun accepted() {
        sounds.play(acceptedSound, 1f, 1f, 1, 0, 1f)
    }

    fun failed() {
        sounds.play(failedSound, 1f, 1f, 1, 0, 1f)
    }
}
