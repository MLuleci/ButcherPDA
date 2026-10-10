package bg.butcher.pda

import android.app.Application
import bg.butcher.pda.data.AppContainer
import bg.butcher.pda.data.DefaultAppContainer

class MainApplication: Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}