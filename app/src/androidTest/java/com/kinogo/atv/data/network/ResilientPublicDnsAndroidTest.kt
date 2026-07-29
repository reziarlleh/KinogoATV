package com.kinogo.atv.data.network

import org.junit.Test

/**
 * Guards code that is initialized by Android's ICU regex implementation.
 *
 * Desktop JVM tests accepted a malformed closing brace that Android rejects, so this must stay an
 * instrumentation test running inside Android rather than another local unit test.
 */
class ResilientPublicDnsAndroidTest {
    @Test
    fun defaultConstructor_initializesOnAndroid() {
        ResilientPublicDns()
    }
}
