package com.kinogo.atv.player.web

sealed interface PlayerJsCommand {
    data object Play : PlayerJsCommand

    data object Pause : PlayerJsCommand

    data object Toggle : PlayerJsCommand

    data object Stop : PlayerJsCommand

    data object Previous : PlayerJsCommand

    data object Next : PlayerJsCommand

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

}
