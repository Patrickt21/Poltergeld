package app.poltergeld.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun httpsIsAlwaysAllowed() {
        assertTrue(UrlPolicy.isCleartextAllowed("https://ghostfolio.example.com"))
        assertTrue(UrlPolicy.isCleartextAllowed("HTTPS://ghostfolio.example.com"))
    }

    @Test
    fun httpToPrivateIpv4RangesIsAllowed() {
        assertTrue(UrlPolicy.isCleartextAllowed("http://192.168.1.10:3333"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://10.0.0.5"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://172.16.0.1:8080"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://172.31.255.254"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://127.0.0.1:3333"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://169.254.1.1"))
    }

    @Test
    fun httpToPublicIpv4IsRefused() {
        assertFalse(UrlPolicy.isCleartextAllowed("http://8.8.8.8"))
        assertFalse(UrlPolicy.isCleartextAllowed("http://172.32.0.1"))
        assertFalse(UrlPolicy.isCleartextAllowed("http://11.0.0.1"))
    }

    @Test
    fun httpToLanNamesIsAllowed() {
        assertTrue(UrlPolicy.isCleartextAllowed("http://localhost:3333"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://umbrel.local"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://nas.lan:3333"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://server.home.arpa"))
        assertTrue(UrlPolicy.isCleartextAllowed("http://ghostfolio.fritz.box"))
        // Single-label hostnames only resolve locally.
        assertTrue(UrlPolicy.isCleartextAllowed("http://umbrel:3333"))
    }

    @Test
    fun httpToPublicHostnamesIsRefused() {
        assertFalse(UrlPolicy.isCleartextAllowed("http://ghostfolio.example.com"))
        assertFalse(UrlPolicy.isCleartextAllowed("http://example.com:3333"))
    }

    @Test
    fun ipv6LoopbackAndUlaAreAllowed() {
        assertTrue(UrlPolicy.isCleartextAllowed("http://[::1]:3333"))
        assertTrue(UrlPolicy.isPrivateHost("fd12:3456::1"))
        assertTrue(UrlPolicy.isPrivateHost("fe80::1"))
        assertFalse(UrlPolicy.isPrivateHost("2001:db8::1"))
    }

    @Test
    fun malformedNumbersAreRefused() {
        assertFalse(UrlPolicy.isPrivateHost("192.168.1.999"))
        assertFalse(UrlPolicy.isPrivateHost("10.0.0.x"))
    }
}
