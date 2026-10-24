package bg.butcher.pda

import android.app.Application
import bg.butcher.pda.data.AppContainer
import bg.butcher.pda.data.DefaultAppContainer
import bg.butcher.pda.hardware.QsPdaHardware

class MainApplication: Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        QsPdaHardware.init(this)
        container = DefaultAppContainer(this)
    }
}