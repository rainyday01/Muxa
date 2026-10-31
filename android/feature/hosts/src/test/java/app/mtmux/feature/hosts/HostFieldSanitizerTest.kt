package app.mtmux.feature.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFieldSanitizerTest {
    @Test
    fun splitsPasswordGluedToIpv4() {
        val p = HostFieldSanitizer.parseHostname("203.0.113.32Secret~")
        assertEquals("203.0.113.32", p.host)
        assertEquals("Secret~", p.password)
    }

    @Test
    fun multilineHostThenPassword() {
        val p = HostFieldSanitizer.parseHostname("203.0.113.32\nSecret~")
        assertEquals("203.0.113.32", p.host)
        assertEquals("Secret~", p.password)
        assertTrue(HostFieldSanitizer.isPlausibleHost(p.host))
    }

    @Test
    fun userAtHost() {
        val p = HostFieldSanitizer.parseHostname("ubuntu@198.51.100.143")
        assertEquals("198.51.100.143", p.host)
        assertEquals("ubuntu", p.username)
    }

    @Test
    fun cleanIpOk() {
        val p = HostFieldSanitizer.parseHostname("198.51.100.143")
        assertEquals("198.51.100.143", p.host)
        assertNull(p.password)
        assertTrue(HostFieldSanitizer.isPlausibleHost(p.host))
    }

    @Test
    fun hiddenNewlineMakesRawImplausibleButParseCleans() {
        val raw = "198.51.100.143\nsecret"
        assertFalse(HostFieldSanitizer.isPlausibleHost(raw))
        val p = HostFieldSanitizer.parseHostname(raw)
        assertTrue(HostFieldSanitizer.isPlausibleHost(p.host))
        assertEquals("secret", p.password)
    }
}
