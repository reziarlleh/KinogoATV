package com.kinogo.atv.data.playback

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialGatewayPlayerDiscoveryTest {
    @Test
    fun `HTML post 117297 maps to gateway post 126815 for Night Business`() = runTest {
        val transport = FixtureTransport(
            searchBody = fixture("gateway_night_business_search.json"),
            playerBody = fixture("gateway_night_business_player.json"),
        )
        val discovery = testDiscovery(transport)

        val result = discovery.discover(nightBusinessLookup())

        assertTrue(result is OfficialPlayerDiscoveryResult.Ready)
        result as OfficialPlayerDiscoveryResult.Ready
        assertEquals(HtmlPostId("117297"), result.htmlPostId)
        assertEquals(GatewayPostId("126815"), result.gatewayPostId)
        assertNotEquals(result.htmlPostId.value, result.gatewayPostId.value)
        assertEquals(2, result.offers.size)
    }

    @Test
    fun `ambiguous exact title and year is rejected before player request`() = runTest {
        val transport = FixtureTransport(
            searchBody = fixture("gateway_ambiguous_search.json"),
            playerBody = fixture("gateway_night_business_player.json"),
        )
        val lookupWithoutDisambiguators = nightBusinessLookup().copy(
            originalTitle = null,
            kinopoiskId = null,
        )

        val result = testDiscovery(transport).discover(lookupWithoutDisambiguators)

        assertEquals(
            OfficialPlayerDiscoveryResult.Rejected(
                HtmlPostId("117297"),
                OfficialPlayerDiscoveryRejection.AMBIGUOUS_MATCH,
            ),
            result,
        )
        assertEquals(1, transport.searchCalls)
        assertEquals(0, transport.playerCalls)
    }

    @Test
    fun `named Cinemar and Collaps tabs are parsed deduplicated and mirrors ignored`() {
        val snapshot = OfficialGatewayJsonParser.parsePlayer(
            fixture("gateway_night_business_player.json"),
        )

        assertEquals(GatewayPostId("126815"), snapshot.gatewayPostId)
        assertTrue(snapshot.hasPlayer)
        assertEquals(
            listOf(OfficialPlayerProvider.CINEMAR, OfficialPlayerProvider.COLLAPS),
            snapshot.offers.map { it.provider },
        )
        assertEquals(2, snapshot.offers.size)
        assertFalse(snapshot.offers.any { "mirror-must-be-ignored" in it.iframeUri.toASCIIString() })
    }

    @Test
    fun `every discovery performs fresh search and player calls`() = runTest {
        val transport = FreshResponseTransport(fixture("gateway_night_business_search.json"))
        val discovery = testDiscovery(transport)

        val first = discovery.discover(nightBusinessLookup()) as OfficialPlayerDiscoveryResult.Ready
        val second = discovery.discover(nightBusinessLookup()) as OfficialPlayerDiscoveryResult.Ready

        assertEquals(2, transport.searchCalls)
        assertEquals(2, transport.playerCalls)
        assertNotEquals(first.offers.single().iframeUrl, second.offers.single().iframeUrl)
        assertFalse(first.toString().contains(first.offers.single().iframeUrl))
    }

    @Test
    fun `original title and Kinopoisk id are exact disambiguators when supplied`() {
        val candidates = OfficialGatewayJsonParser.parseLightSearch(
            fixture("gateway_ambiguous_search.json"),
        )

        assertEquals(
            OfficialGatewayPostMatch.Mapped(GatewayPostId("126815")),
            OfficialGatewayPostMatcher.match(nightBusinessLookup(), candidates),
        )
    }

    private fun nightBusinessLookup() = OfficialPlayerLookup(
        htmlPostId = HtmlPostId("117297"),
        title = "  Ночной   бизнес ",
        year = 2026,
        originalTitle = "The Get Out",
        kinopoiskId = "7104892",
    )

    private fun testDiscovery(transport: OfficialGatewayTransport) =
        OfficialGatewayPlayerDiscovery(
            enabled = true,
            transport = transport,
            destinationValidator = { uri: URI -> require(uri.scheme == "https") },
        )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/playback/$name")).readText()
}

private class FixtureTransport(
    private val searchBody: String,
    private val playerBody: String,
) : OfficialGatewayTransport {
    var searchCalls: Int = 0
    var playerCalls: Int = 0

    override suspend fun get(relativeRoute: String): OfficialGatewayHttpResponse = when {
        "lightsearch" in relativeRoute -> {
            searchCalls++
            OfficialGatewayHttpResponse(200, searchBody)
        }
        relativeRoute.endsWith("/player") -> {
            playerCalls++
            OfficialGatewayHttpResponse(200, playerBody)
        }
        else -> error("Unexpected fixture route")
    }
}

private class FreshResponseTransport(
    private val searchBody: String,
) : OfficialGatewayTransport {
    var searchCalls: Int = 0
    var playerCalls: Int = 0

    override suspend fun get(relativeRoute: String): OfficialGatewayHttpResponse {
        if ("lightsearch" in relativeRoute) {
            searchCalls++
            return OfficialGatewayHttpResponse(200, searchBody)
        }
        require(relativeRoute.endsWith("/player"))
        playerCalls++
        return OfficialGatewayHttpResponse(
            200,
            """
                {
                  "status":"ok",
                  "data":{
                    "post_id":126815,
                    "has_player":true,
                    "tabs":[{
                      "title":"Смотреть онлайн",
                      "balancer":"cinemar",
                      "iframe_url":"https://cinemar.example/embed/night?fresh=$playerCalls"
                    }],
                    "mirrors":[]
                  }
                }
            """.trimIndent(),
        )
    }
}
