package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressesTest {
    @Test
    fun privateRanges() {
        assertTrue(LanAddresses.isPrivateIpv4("192.168.2.15"))
        assertTrue(LanAddresses.isPrivateIpv4("10.0.0.2"))
        assertTrue(LanAddresses.isPrivateIpv4("172.16.1.1"))
        assertFalse(LanAddresses.isPrivateIpv4("8.8.8.8"))
        assertFalse(LanAddresses.isPrivateIpv4("169.254.1.1"))
    }

    @Test
    fun subnetSkipsSelf() {
        val hosts = LanAddresses.subnetHosts("192.168.2.142")
        assertEquals(253, hosts.size)
        assertTrue("192.168.2.1" in hosts)
        assertFalse("192.168.2.142" in hosts)
        assertFalse("192.168.2.0" in hosts)
        assertFalse("192.168.2.255" in hosts)
    }

    @Test
    fun wifiBeatsCellular() {
        val wifi = LanAddresses.rank("wlan0", "192.168.2.15")
        val cell = LanAddresses.rank("rmnet0", "10.64.12.3")
        val vpn = LanAddresses.rank("tun0", "10.8.0.2")
        assertTrue(wifi < cell)
        assertTrue(wifi < vpn)
    }
}
