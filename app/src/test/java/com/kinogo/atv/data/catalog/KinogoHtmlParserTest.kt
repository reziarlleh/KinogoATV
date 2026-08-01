package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogDefaultSort
import com.kinogo.atv.domain.CatalogSortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KinogoHtmlParserTest {
    private val parser = KinogoHtmlParser()

    @Test
    fun parsesCatalogCardsAndFindsEarlyNextPage() {
        val page = parser.parseCatalog(CATALOG_HTML, ORIGIN, page = 2)

        assertEquals(2, page.currentPage)
        assertEquals(3, page.nextPage)
        assertEquals(1, page.items.size)
        with(page.items.single()) {
            assertEquals("115576", id)
            assertEquals("/serialy/115576-istoriya.html", relativePath)
            assertEquals("История его служанки (1 сезон)", title)
            assertEquals("https://kinogo.parts/uploads/poster.webp", posterUrl)
            assertEquals(2026, year)
            assertEquals(ContentType.SERIES, type)
            assertEquals(7.4, ratings.kinopoisk)
            assertEquals(6.8, ratings.imdb)
            assertEquals("WEB-DL 1080", qualityBadge)
            assertEquals("1 сезон 1-19 серия", episodeBadge)
        }
    }

    @Test
    fun parsesCurrentXSortControlsAndOnlyAllowlistedCategories() {
        val parsed = parser.parseCatalog(
            fixture("catalog_controls.html"),
            ORIGIN,
            page = 1,
        )

        with(parsed.controls) {
            assertEquals(CatalogDefaultSort.entries, sortOptions.mapNotNull { it.value })
            assertEquals(listOf("Netflix", "Marvel"), collectionOptions.map { it.value })
            assertEquals(listOf("2026", "2025"), yearOptions.map { it.value })
            assertEquals(listOf("США", "Россия"), countryOptions.map { it.value })
            assertEquals(CatalogCategory.entries, categories)
            assertEquals(CatalogDefaultSort.TOP_3_DAYS, activeFilters.defaultSort)
            assertEquals(CatalogSortDirection.DESC, activeFilters.sortDirection)
            assertEquals("Netflix", activeFilters.collection?.value)
            assertEquals(2026, activeFilters.year)
            assertEquals("США", activeFilters.country?.value)
        }
    }

    @Test
    fun preservesPageSpecificBlankSortAndAscendingSelection() {
        val controls = parser.parseCatalogControls(
            """
                <html><body>
                  <ul class="xsort-ul" data-field="defaultsort">
                    <li data-val="" class="current">по умолчанию</li>
                    <li data-val="date" class="xasc">по дате</li>
                  </ul>
                </body></html>
            """.trimIndent(),
            ORIGIN,
        )

        assertEquals(null, controls.sortOptions.first().value)
        assertEquals("по умолчанию", controls.sortOptions.first().title)
        assertEquals(null, controls.activeFilters.defaultSort)
        assertEquals(CatalogSortDirection.DESC, controls.activeFilters.sortDirection)

        val ascending = parser.parseCatalogControls(
            """
                <html><body>
                  <div class="xsort-div">
                    <div class="xsort-selected"><span class="xasc">по дате</span></div>
                    <ul class="xsort-ul" data-field="defaultsort">
                      <li data-val="date" class="current xasc">по дате</li>
                    </ul>
                  </div>
                </body></html>
            """.trimIndent(),
            ORIGIN,
        )
        assertEquals(CatalogDefaultSort.DATE, ascending.activeFilters.defaultSort)
        assertEquals(CatalogSortDirection.ASC, ascending.activeFilters.sortDirection)
    }

    @Test
    fun parsesDetailsMetadataDescriptionAndEmbedCandidates() {
        val page = parser.parseDetails(
            html = DETAILS_HTML,
            origin = ORIGIN,
            relativePath = "/filmy/54321-test-film.html#ignored",
        )

        with(page.catalogItem) {
            assertEquals("54321", id)
            assertEquals("/filmy/54321-test-film.html", relativePath)
            assertEquals("Тестовый фильм", title)
            assertEquals("The Test Film", originalTitle)
            assertEquals("https://kinogo.parts/uploads/full-poster.jpg", posterUrl)
            assertEquals(2025, year)
            assertEquals(ContentType.MOVIE, type)
            assertEquals(8.1, ratings.kinopoisk)
            assertEquals(7.7, ratings.imdb)
            assertEquals("WEB-DL 1080p", qualityBadge)
            assertEquals("1 сезон", episodeBadge)
        }
        assertEquals("Первый абзац.\n\nВторой абзац.", page.description)
        assertEquals(listOf("США", "Великобритания"), page.countries)
        assertEquals(listOf("Драма", "Триллер"), page.genres)
        assertEquals(listOf("Иван Иванов", "Анна Смирнова"), page.directors)
        assertEquals(listOf("Актёр Один", "Актриса Два"), page.cast)
        assertEquals(127, page.durationMinutes)
        assertEquals(2, page.playerEmbeds.size)
        assertEquals("Основной", page.playerEmbeds[0].label)
        assertEquals("0", page.playerEmbeds[0].providerId)
        assertEquals("https://player.example/embed/one", page.playerEmbeds[0].embedUrl)
        assertEquals("Резервный плеер", page.playerEmbeds[1].label)
        assertEquals("https://backup.example/embed/two", page.playerEmbeds[1].url)
        assertEquals(null, page.playerNotice)
        assertTrue(page.metadata.containsKey("Продолжительность"))
    }

    @Test
    fun preservesServerPlaybackNoticeWhenNoEmbedIsAvailable() {
        val page = parser.parseDetails(
            """
                <div id="dle-content"><article id="post-115555" class="fullStory">
                  <h1>Клинки хранителей</h1>
                  <div class="sectionPlayer">
                    <div class="player-empty">
                      К сожалению данное видео недоступно в вашей стране.<br>
                      Для просмотра необходимо включить <b>VPN</b>
                    </div>
                  </div>
                </article></div>
            """.trimIndent(),
            ORIGIN,
            "/filmy/115555-klinki-hranitelej.html",
        )

        assertTrue(page.playerEmbeds.isEmpty())
        assertEquals(
            "К сожалению данное видео недоступно в вашей стране. " +
                "Для просмотра необходимо включить VPN",
            page.playerNotice,
        )
    }

    @Test
    fun unsupportedOrUnsafePageShapeIsRejected() {
        assertThrows(CatalogParseException::class.java) {
            parser.parseCatalog("<html><body></body></html>", ORIGIN, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseDetails(DETAILS_HTML, ORIGIN, "https://evil.example/content")
        }

        assertThrows(CatalogParseException::class.java) {
            parser.parseCatalog(
                """
                    <div id="dle-content">
                      <article class="shortStory">
                        <h2><a href="https://evil.example/1-film.html">Чужая карточка</a></h2>
                      </article>
                    </div>
                """.trimIndent(),
                ORIGIN,
                1,
            )
        }
    }

    @Test
    fun parsesLiveMetadataFallbacksAndAnimeSeriesPath() {
        val parsed = parser.parseCatalog(
            """
                <div id="dle-content">
                  <article class="shortStory">
                    <h2><a href="/anime-serialy/777-show.html#777">Аниме-сериал</a></h2>
                    <div class="sPoster"><img src="/uploads/anime.jpg"></div>
                    <div class="sInfo">
                      <span><b>Добавлено:</b>2 сезон 4 серия</span>
                      <span><b>Качество:</b>WEBRip 720p</span>
                    </div>
                  </article>
                </div>
            """.trimIndent(),
            ORIGIN,
            1,
        ).items.single()

        assertEquals(ContentType.ANIME, parsed.type)
        assertEquals("2 сезон 4 серия", parsed.episodeBadge)
        assertEquals("WEBRip 720p", parsed.qualityBadge)
    }

    @Test
    fun readsFilmDescriptionFDopChildrenAndMetadataRatings() {
        val parsed = parser.parseDetails(
            """
                <div id="dle-content"><article id="post-99" class="fullStory">
                  <h1>Другая версия шаблона</h1>
                  <div class="fullRatings">
                    <div><b>Кинопоиск:</b> 6,9</div>
                    <div><b>IMDb:</b> 7.2</div>
                  </div>
                  <div class="filmDescription">
                    <p>Описание.</p>
                    <div class="fDop">
                      <div><b>Длительность:</b> 48 мин</div>
                      <div><b>Качество:</b> HDTV 1080i</div>
                    </div>
                  </div>
                </article></div>
            """.trimIndent(),
            ORIGIN,
            "/anime-serialy/99-show.html",
        )

        assertEquals(6.9, parsed.catalogItem.ratings.kinopoisk)
        assertEquals(7.2, parsed.catalogItem.ratings.imdb)
        assertEquals(48, parsed.durationMinutes)
        assertEquals("HDTV 1080i", parsed.catalogItem.qualityBadge)
        assertEquals(ContentType.ANIME, parsed.catalogItem.type)
    }

    private companion object {
        const val ORIGIN = "https://kinogo.parts"

        val CATALOG_HTML = """
            <!doctype html>
            <html><body>
              <div id="dle-content">
                <article class="shortStory">
                  <h2><a href="https://kinogo.parts/serialy/115576-istoriya.html#115576">
                    История его служанки (1 сезон)
                  </a></h2>
                  <div class="sPoster">
                    <img data-src="/uploads/poster.webp" src="data:image/gif;base64,stub">
                    <div class="lenta"><div class="cont">1 сезон 1-19 серия</div></div>
                  </div>
                  <div class="sInfo">
                    <span><b>Год выпуска:</b><a>2026</a></span>
                    <span><b>Жанр:</b><a>Сериалы</a> / <a>Мелодрамы</a></span>
                  </div>
                  <div class="mRatings">
                    <span class="kp">КП: 7.4</span>
                    <span class="imdb">IMDB: 6.8</span>
                  </div>
                  <div class="quAl">WEB-DL 1080</div>
                </article>
                <article class="shortStory">
                  <h2><a href="https://evil.example/serialy/999-copy.html">Копия</a></h2>
                </article>
              </div>
              <div class="pagiNation">
                <a href="https://kinogo.parts/page/1/">1</a>
                <span>2</span>
                <a href="https://kinogo.parts/page/3/">3</a>
                <a href="https://kinogo.parts/page/3/">Позже</a>
              </div>
            </body></html>
        """.trimIndent()

        val DETAILS_HTML = """
            <!doctype html>
            <html><body><div id="dle-content">
              <article id="post-54321" class="fullStory">
                <h1>Тестовый фильм</h1>
                <div class="sPoster"><img src="/uploads/full-poster.jpg"></div>
                <div class="sInfo">
                  <span><b>Год выпуска:</b>2025</span>
                  <span><b>Зарубежное название:</b>The Test Film</span>
                  <span><b>Страна:</b>США / Великобритания</span>
                  <span><b>Жанр:</b>Драма / Триллер</span>
                  <div class="fullRatings">
                    <div><b>КиноПоиск:</b>8.1</div>
                    <div><b>IMDB:</b>7.7</div>
                  </div>
                  <div class="castInfo">
                    <span><b>Режиссер:</b>Иван Иванов, Анна Смирнова</span>
                    <span><b>Актеры:</b>Актёр Один, Актриса Два</span>
                  </div>
                </div>
                <div class="filmDescription">
                  <p>Первый <strong>абзац</strong>.</p>
                  <p>Второй абзац.</p>
                </div>
                <div class="filmDop">
                  <div class="fDop"><div class="fDop-l">Сезон:</div><div class="fDop-r">1 сезон</div></div>
                  <div class="fDop"><div class="fDop-l">Продолжительность:</div><div class="fDop-r">127 мин</div></div>
                  <div class="fDop"><div class="fDop-l">Качество:</div><div class="fDop-r">WEB-DL 1080p</div></div>
                </div>
                <ul class="js-player-tabs">
                  <li data-src="https://player.example/embed/one" data-provider="0">Основной</li>
                  <li data-src="javascript:alert(1)">Небезопасный</li>
                </ul>
                <div class="player-container">
                  <iframe src="https://player.example/embed/one" title="Дубликат"></iframe>
                  <iframe src="https://backup.example/embed/two" title="Резервный плеер"></iframe>
                </div>
              </article>
            </div></body></html>
        """.trimIndent()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/catalog/$name")).readText()
}
