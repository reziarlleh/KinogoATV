package com.kinogo.atv.player.web

sealed interface PlayerJsCommand {
    data object Play : PlayerJsCommand

    data object Pause : PlayerJsCommand

    data object Toggle : PlayerJsCommand

    data object Stop : PlayerJsCommand

    data object Previous : PlayerJsCommand

    data object Next : PlayerJsCommand

    data class SelectQuality(val index: Int) : PlayerJsCommand {
        init {
            require(index in 0..MAX_OPTION_INDEX) { "Quality index is outside the supported range" }
        }
    }

    data class SelectAudioTrack(val index: Int) : PlayerJsCommand {
        init {
            require(index in 0..MAX_OPTION_INDEX) { "Audio-track index is outside the supported range" }
        }
    }

    /** Index -1 disables subtitles, as documented by PlayerJS. */
    data class SelectSubtitle(val index: Int) : PlayerJsCommand {
        init {
            require(index in -1..MAX_OPTION_INDEX) { "Subtitle index is outside the supported range" }
        }
    }

    /** Opens a playlist entry by its PlayerJS id. The value is data, never JavaScript source. */
    data class FindPlaylistItem(val id: String) : PlayerJsCommand {
        init {
            requireSafePlaylistId(id)
        }
    }

    data class SeekRelative(val seconds: Int) : PlayerJsCommand {
        init {
            require(seconds != 0) { "Relative seek must not be zero" }
            require(seconds in -MAX_SEEK_SECONDS..MAX_SEEK_SECONDS) {
                "Relative seek is outside the supported TV range"
            }
        }
    }

    private companion object {
        const val MAX_SEEK_SECONDS = 10 * 60
        const val MAX_OPTION_INDEX = 255
    }
}

/** Builds only a fixed allowlist of documented PlayerJS API calls. */
object PlayerJsCommandBuilder {
    fun javascript(command: PlayerJsCommand): String {
        val operation = when (command) {
            PlayerJsCommand.Play ->
                "if(api){api.api('play');return true;}" +
                    "if(media){void media.play();return true;}return false;"
            PlayerJsCommand.Pause ->
                "if(api){api.api('pause');return true;}" +
                    "if(media){media.pause();return true;}return false;"
            PlayerJsCommand.Toggle ->
                "if(api){api.api('toggle');return true;}" +
                    "if(media){if(media.paused)void media.play();else media.pause();" +
                    "return true;}return false;"
            PlayerJsCommand.Stop ->
                "if(api){api.api('stop');return true;}" +
                    "if(media){media.pause();media.currentTime=0;return true;}return false;"
            PlayerJsCommand.Previous ->
                "if(api){api.api('prev');return true;}return false;"
            PlayerJsCommand.Next ->
                "if(api){api.api('next');return true;}return false;"
            is PlayerJsCommand.SelectQuality ->
                "if(api){api.api('quality',${command.index});return true;}return false;"
            is PlayerJsCommand.SelectAudioTrack ->
                "if(api){api.api('audiotrack',${command.index});return true;}return false;"
            is PlayerJsCommand.SelectSubtitle ->
                "if(api){api.api('subtitle',${command.index});return true;}return false;"
            is PlayerJsCommand.FindPlaylistItem ->
                "if(api){return api.api('find',${javascriptStringLiteral(command.id)})===true;}" +
                    "return false;"
            is PlayerJsCommand.SeekRelative -> buildString {
                append("const current=Number(api?api.api('time'):(media?media.currentTime:NaN));")
                append("if(!Number.isFinite(current))return false;const target=Math.max(0,current")
                if (command.seconds > 0) append('+')
                append(command.seconds)
                append(");if(api)api.api('seek',target);else media.currentTime=target;")
                append("return true;")
            }
        }
        return buildString {
            append("(()=>{try{const api=(typeof player!=='undefined'&&player&&")
            append("typeof player.api==='function')?player:null;")
            append("const media=document.querySelector('video');")
            append(operation)
            append("}catch(_){return false;}})()")
        }
    }

    private fun javascriptStringLiteral(value: String): String = buildString(value.length + 2) {
        append('\'')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('\'')
    }
}

private fun requireSafePlaylistId(value: String) {
    require(value.isNotBlank()) { "Playlist id must not be blank" }
    require(value == value.trim()) { "Playlist id must not have surrounding whitespace" }
    require(value.length <= 128) { "Playlist id is too long" }
    require(value.none(Char::isISOControl)) { "Playlist id contains control characters" }
    require(!value.looksLikeMediaLocation()) { "Playlist id must not be a media location" }
}

internal fun String.looksLikeMediaLocation(): Boolean {
    val normalized = trimStart().lowercase()
    return normalized.startsWith("http://") ||
        normalized.startsWith("https://") ||
        normalized.startsWith("//") ||
        normalized.startsWith("data:") ||
        normalized.startsWith("blob:")
}
