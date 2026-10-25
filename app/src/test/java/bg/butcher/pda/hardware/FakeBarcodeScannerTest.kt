package bg.butcher.pda.hardware

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeBarcodeScannerTest {
    @Test
    fun events_onlyArriveWhileActive() = runTest(UnconfinedTestDispatcher()) {
        val scanner = FakeBarcodeScanner()
        val received = mutableListOf<ScanEvent>()
        backgroundScope.launch { scanner.events.collect { received += it } }

        scanner.scan("ignored")
        scanner.noRead()
        scanner.setActive(true)
        scanner.scan("BEEF.0001")
        scanner.noRead()

        assertEquals(listOf(ScanEvent.Code("BEEF.0001"), ScanEvent.NoRead), received)
    }

    @Test
    fun setActive_isCounted() {
        val scanner = FakeBarcodeScanner()

        scanner.setActive(true)
        scanner.setActive(true)
        scanner.setActive(false)
        assertTrue(scanner.isActive)

        scanner.setActive(false)
        scanner.setActive(false)
        assertFalse(scanner.isActive)
        scanner.setActive(true)
        assertTrue(scanner.isActive)
    }
}
