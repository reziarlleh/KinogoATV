package com.kinogo.atv.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterUrlPolicyTest {
    @Test
    fun `normalizes safe HTTPS URLs and removes fragments`() {
        assertEquals(
            "https://cdn.example.org/posters/My%20Film.jpg?size=tv",
            PosterUrlPolicy.normalizeOrNull(
                "  https://cdn.example.org/posters/My Film.jpg?size=tv#preview  ",
            ),
        )
    }

    @Test
    fun `rejects non HTTPS credentials and malformed values`() {
        listOf(
            null,
            "",
            "http://cdn.example.org/poster.jpg",
            "https://user:secret@cdn.example.org/poster.jpg",
            "not a URL",
        ).forEach { value ->
            assertNull(value, PosterUrlPolicy.normalizeOrNull(value))
        }
    }
}
