package bg.butcher.pda

import android.app.Application
import bg.butcher.pda.data.AppContainer
import bg.butcher.pda.data.DefaultAppContainer
import bg.butcher.pda.data.PaperType
import bg.butcher.pda.hardware.QsPdaHardware
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainApplication: Application() {
    lateinit var container: AppContainer
        private set

    // As long as the process
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        QsPdaHardware.init(this)
        container = DefaultAppContainer(this)
        scope.launch {
            container.settingsRepository.settings
                .map { it.paperType == PaperType.LABELS }
                .distinctUntilChanged()
                .collect(QsPdaHardware::setLabelMode)
        }
    }
}
