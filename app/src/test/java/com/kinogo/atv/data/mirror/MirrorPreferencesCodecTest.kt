package com.kinogo.atv.data.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorPreferencesCodecTest {
    @Test
    fun codecNormalizesDeduplicatesAndSkipsInvalidEntries() {
        val encoded = MirrorOriginsCodec.encode(
            listOf("kinogo.parts", "https://KINOGO.parts/", "https://kinogo.online"),
        )
        assertEquals(
            listOf("https://kinogo.online", "https://kinogo.parts"),
            MirrorOriginsCodec.decode(encoded + "\nhttp://unsafe.test\nnot a host"),
        )
    }
}
