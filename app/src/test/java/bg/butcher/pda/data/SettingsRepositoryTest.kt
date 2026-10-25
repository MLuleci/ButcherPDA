package bg.butcher.pda.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
    }
    private val repository by lazy { SettingsRepository(dataStore) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun paperSize_defaultsTo80mm() = runTest {
        assertEquals(PaperSize.MM_80, repository.settings.first().paperSize)
    }

    @Test
    fun paperSize_isSavedWithTheOtherSettings() = runTest {
        val settings = PdaSettings("https://erp.example.bg/", "key:secret", PaperSize.MM_58, labelHeightMm = 30)

        repository.update(settings)

        assertEquals(settings, repository.settings.first())
    }

    @Test
    fun paperSize_unknownValueFallsBackTo80mm() = runTest {
        dataStore.edit { it[stringPreferencesKey("paper_size")] = "MM_110" }

        assertEquals(PaperSize.MM_80, repository.settings.first().paperSize)
    }

    @Test
    fun labelHeight_defaultsTo40mmAndIgnoresOutOfRangeValues() = runTest {
        assertEquals(DEFAULT_LABEL_HEIGHT_MM, repository.settings.first().labelHeightMm)

        dataStore.edit { it[intPreferencesKey("label_height_mm")] = 5 }

        assertEquals(DEFAULT_LABEL_HEIGHT_MM, repository.settings.first().labelHeightMm)
    }
}
