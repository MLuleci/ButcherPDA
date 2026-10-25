package bg.butcher.pda.hardware

import android.content.Context
import android.media.SoundPool
import bg.butcher.pda.R

/** Beeps when a scanned batch is accepted. */
class ScanFeedback(context: Context) {
    private val sounds = SoundPool.Builder().setMaxStreams(1).build()
    private val beep = sounds.load(context, R.raw.beep, 1)

    fun accepted() {
        sounds.play(beep, 1f, 1f, 1, 0, 1f)
    }
}
