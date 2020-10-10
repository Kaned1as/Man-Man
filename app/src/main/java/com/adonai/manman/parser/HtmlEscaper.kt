package com.adonai.manman.parser

/**
 * Compat class from API 16 for escapeHtml method
 *
 * @see android.text.Html
 */
object HtmlEscaper {
    /**
     * Returns an HTML escaped representation of the given plain text.
     */
    @JvmStatic
    fun escapeHtml(text: CharSequence): String {
        val out = StringBuilder()
        withinStyle(out, text, 0, text.length)
        return out.toString()
    }

    private fun withinStyle(out: StringBuilder, text: CharSequence, start: Int, end: Int) {
        var i = start
        while (i < end) {
            val c = text[i]
            if (c == '<') {
                out.append("&lt;")
            } else if (c == '>') {
                out.append("&gt;")
            } else if (c == '&') {
                out.append("&amp;")
            } else if (c.toInt() in 0xD800..0xDFFF) {
                if (c.toInt() < 0xDC00 && i + 1 < end) {
                    val d = text[i + 1]
                    if (d.toInt() in 0xDC00..0xDFFF) {
                        i++
                        val codepoint = 0x010000 or (c.toInt() - 0xD800 shl 10) or d.toInt() - 0xDC00
                        out.append("&#").append(codepoint).append(";")
                    }
                }
            } else if (c.toInt() > 0x7E || c < ' ') {
                out.append("&#").append(c.toInt()).append(";")
            } else if (c == ' ') {
                while (i + 1 < end && text[i + 1] == ' ') {
                    out.append("&nbsp;")
                    i++
                }
                out.append(' ')
            } else {
                out.append(c)
            }
            i++
        }
    }
}