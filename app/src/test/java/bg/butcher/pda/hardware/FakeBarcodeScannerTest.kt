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
    fun scans_onlyArriveWhileActive() = runTest(UnconfinedTestDispatcher()) {
        val scanner = FakeBarcodeScanner()
        val received = mutableListOf<String>()
        backgroundScope.launch { scanner.scans.collect { received += it } }

        scanner.scan("ignored")
        scanner.setActive(true)
        scanner.scan("BEEF.0001")

        assertEquals(listOf("BEEF.0001"), received)
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
