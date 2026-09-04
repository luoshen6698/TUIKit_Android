package io.trtc.tuikit.chat.uikit.components.messageinput.ui

/** Finds the start of the user-visible character immediately before a UTF-16 cursor. */
internal object EmojiTextDeletionPolicy {
    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_START = 0xFE00
    private const val VARIATION_SELECTOR_END = 0xFE0F
    private const val VARIATION_SELECTOR_SUPPLEMENT_START = 0xE0100
    private const val VARIATION_SELECTOR_SUPPLEMENT_END = 0xE01EF
    private const val EMOJI_MODIFIER_START = 0x1F3FB
    private const val EMOJI_MODIFIER_END = 0x1F3FF
    private const val REGIONAL_INDICATOR_START = 0x1F1E6
    private const val REGIONAL_INDICATOR_END = 0x1F1FF

    fun previousClusterStart(text: String, cursor: Int): Int {
        if (cursor <= 0 || text.isEmpty()) return 0

        val safeCursor = cursor.coerceAtMost(text.length)
        var start = previousCodePointStart(text, safeCursor)
        var codePoint = text.codePointAt(start)

        if (isRegionalIndicator(codePoint)) {
            var regionalIndicatorCount = 1
            var scan = start
            while (scan > 0) {
                val previous = previousCodePointStart(text, scan)
                if (!isRegionalIndicator(text.codePointAt(previous))) break
                regionalIndicatorCount += 1
                scan = previous
            }
            return if (regionalIndicatorCount % 2 == 0) {
                previousCodePointStart(text, start)
            } else {
                start
            }
        }

        while (isClusterContinuation(codePoint) && start > 0) {
            start = previousCodePointStart(text, start)
            codePoint = text.codePointAt(start)
        }

        while (start > 0) {
            val joinerStart = previousCodePointStart(text, start)
            if (text.codePointAt(joinerStart) != ZERO_WIDTH_JOINER || joinerStart == 0) break

            start = previousCodePointStart(text, joinerStart)
            codePoint = text.codePointAt(start)
            while (isClusterContinuation(codePoint) && start > 0) {
                start = previousCodePointStart(text, start)
                codePoint = text.codePointAt(start)
            }
        }

        return start
    }

    private fun previousCodePointStart(text: String, end: Int): Int {
        return Character.offsetByCodePoints(text, end, -1)
    }

    private fun isClusterContinuation(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return codePoint in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END ||
            codePoint in VARIATION_SELECTOR_SUPPLEMENT_START..VARIATION_SELECTOR_SUPPLEMENT_END ||
            codePoint in EMOJI_MODIFIER_START..EMOJI_MODIFIER_END ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean {
        return codePoint in REGIONAL_INDICATOR_START..REGIONAL_INDICATOR_END
    }
}
