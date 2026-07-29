package com.kinogo.atv.data.library

import com.kinogo.atv.domain.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlLibraryParserTest {
    @Test
    fun parsesIndependentFavoriteAndExclusiveWatchStatus() {
        val state = HtmlLibraryParser.parseDetails(
            """
            <div id="fav-id-115576"><a onclick="doFavorites('minus', '115576', 0)">♥</a></div>
            <div class="js-mylist" data-id="115576">
              <button data-folder="watch">Смотрю</button>
              <button class="is-active" data-folder="done">Смотрел</button>
              <button data-folder="todo">Буду</button>
            </div>
            """.trimIndent(),
        )

        assertEquals(WatchStatus.WATCHED, state.watchStatus)
        assertEquals(true, state.favorite)
    }

    @Test
    fun guestStateDoesNotPretendFavoriteIsFalse() {
        val state = HtmlLibraryParser.parseDetails(
            "<div class=addFav title='Необходимо зарегистрироваться для добавления в избранное'></div>",
        )

        assertNull(state.favorite)
        assertNull(state.watchStatus)
    }

    @Test
    fun myListGuestJsonRequestsRelogin() {
        val result = HtmlLibraryParser.parseMyListMutation(
            """{"success":false,"error":"Необходимо авторизоваться","data":null}""",
        )

        assertFalse(result.successful)
        assertTrue(result.authenticationRequired)
    }

    @Test
    fun successfulMyListJsonHasNoError() {
        val result = HtmlLibraryParser.parseMyListMutation("""{"success":true,"data":null}""")

        assertTrue(result.successful)
        assertFalse(result.authenticationRequired)
    }
}
