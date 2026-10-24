package bg.butcher.pda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsValidationTest {
    @Test
    fun normaliseBaseUrl_acceptsHttps() {
        assertEquals("https://erp.example.com/", normaliseBaseUrl("https://erp.example.com").toString())
        assertEquals("https://erp.example.com/erp", normaliseBaseUrl("  https://erp.example.com/erp \n").toString())
        assertEquals("https://erp.example.com:8443/", normaliseBaseUrl("https://erp.example.com:8443/").toString())
    }

    @Test
    fun normaliseBaseUrl_addsSchemeToBareHost() {
        assertEquals("https://erp.example.com/", normaliseBaseUrl("erp.example.com").toString())
    }

    @Test
    fun normaliseBaseUrl_rejectsOtherSchemes() {
        assertNull(normaliseBaseUrl("http://erp.example.com"))
        assertNull(normaliseBaseUrl("ftp://erp.example.com"))
        assertNull(normaliseBaseUrl("file:///sdcard"))
    }

    @Test
    fun normaliseBaseUrl_rejectsInvalid() {
        assertNull(normaliseBaseUrl(""))
        assertNull(normaliseBaseUrl("   "))
        assertNull(normaliseBaseUrl("https://"))
    }

    @Test
    fun normaliseApiKey_acceptsKeyAndSecret() {
        assertEquals("0fbc2ce7bdf9806:717146d27791748", normaliseApiKey("0fbc2ce7bdf9806:717146d27791748"))
        assertEquals("key:secret", normaliseApiKey("  key:secret\n"))
    }

    @Test
    fun normaliseApiKey_rejectsInvalid() {
        assertNull(normaliseApiKey(""))
        assertNull(normaliseApiKey("key"))
        assertNull(normaliseApiKey("key:"))
        assertNull(normaliseApiKey(":secret"))
        assertNull(normaliseApiKey("key:secret:extra"))
        assertNull(normaliseApiKey("key: secret"))
    }
}
