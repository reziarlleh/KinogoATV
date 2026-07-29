package com.kinogo.atv.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResilientPublicDnsTest {
    @Test
    fun secureDnsAnswersAreAuthoritativeAndSkipSystemLookup() {
        val secure = InetAddress.getByName("172.66.42.226")
        val system = InetAddress.getByName("188.114.96.1")
        var systemCalls = 0
        val dns = ResilientPublicDns(
            systemLookup = {
                systemCalls++
                listOf(system)
            },
            dohLookup = { listOf(secure) },
            nowMs = { 100L },
            cacheTtlMs = 1_000L,
        )

        assertEquals(listOf(secure), dns.lookup("w.kinogo.solar"))
        assertEquals(0, systemCalls)
    }

    @Test
    fun systemDnsIsUsedWhenSecureDnsIsUnavailable() {
        val system = InetAddress.getByName("188.114.96.1")
        val dns = ResilientPublicDns(
            systemLookup = { listOf(system) },
            dohLookup = { emptyList() },
            nowMs = { 100L },
            cacheTtlMs = 1_000L,
        )

        assertEquals(listOf(system), dns.lookup("fallback.example.org"))
    }

    @Test
    fun cachedAnswerAvoidsRepeatedDnsOverHttpsCalls() {
        var calls = 0
        val secure = InetAddress.getByName("172.66.42.226")
        val dns = ResilientPublicDns(
            systemLookup = { emptyList() },
            dohLookup = {
                calls++
                listOf(secure)
            },
            nowMs = { 100L },
            cacheTtlMs = 1_000L,
        )

        dns.lookup("w.kinogo.solar")
        dns.lookup("W.KINOGO.SOLAR")

        assertEquals(1, calls)
    }

    @Test
    fun privateSecureDnsAnswerRejectsTheWholeResolution() {
        val dns = ResilientPublicDns(
            systemLookup = { listOf(InetAddress.getByName("8.8.8.8")) },
            dohLookup = { listOf(InetAddress.getByName("192.168.1.10")) },
            nowMs = { 100L },
            cacheTtlMs = 1_000L,
        )

        assertThrows(UnknownHostException::class.java) {
            dns.lookup("host.example.org")
        }
    }

    @Test
    fun privateSystemFallbackIsRejected() {
        val dns = ResilientPublicDns(
            systemLookup = { listOf(InetAddress.getByName("192.168.1.10")) },
            dohLookup = { emptyList() },
            nowMs = { 100L },
            cacheTtlMs = 1_000L,
        )

        assertThrows(UnknownHostException::class.java) {
            dns.lookup("local-only.example.org")
        }
    }
}
