package com.adonai.manman.parser

import android.util.Log
import com.adonai.manman.Utils
import com.adonai.manman.parser.HtmlEscaper.escapeHtml
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import java.io.BufferedReader
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

/**
 * Simple (quick & draft) TROFF to HTML parser
 * <br></br>
 * <br></br>
 * Contains almost all code required for man page parsing. While TROFF represents highly complicated format,
 * the amount of specific code in man is limited to titles, names, descriptions, character specials, font style.
 * Thus this converter focuses on man contents and does not implement such things as registers/evaluations/tables etc.
 *
 * @author Kanedias
 */
@Suppress("EnumEntryName")
class Man2Html(private val source: BufferedReader) {

    private enum class FontState {
        NORMAL,
        BOLD,
        ITALIC
    }

    private enum class Command {
        th(true),  // Set the title of the man page to `title` and the section to `section`
        dt(true),  // Set tabs every 0.5 inches
        fl,  // Command-line options, prepend every word with dash
        ar,  // Command-line argument
        op,  // Optional argument (often paired with fl)
        it,  // call macros on next line
        bl,  // start of options indent
        el,  // end of options indent
        sh(true), pp(true), lp(true), p(true),  // Paragraph
        nm, nd,  // old name descriptors
        rs,  // Relative margin indent start
        re,  // Relative margin indent end
        tp(true),  // Paragraph with hanging tag
        ip(true),  // Indented paragraph with optional hanging tag
        b, i, br, bi, ri, rb, ir, ib,  // font directives
        ie, nh, ad, sp(true),  // conditionals and stuff...
        nf, fi, // start and end of preformatted text ("no fill lines")
        so; // include another page

        // stop/start output filling (works like <pre> tag)
        var stopsIndentation = false

        constructor()

        constructor(stopsIndentation: Boolean) {
            this.stopsIndentation = stopsIndentation
        }
    }

    private val result = StringBuilder()

    // state variables
    private var fontState = FontState.NORMAL
    private var previousCommand: Command? = null
    private var previousLine: String? = null
    private var manpageName: String? = null
    private var insideParagraph = false
    private var insideSection = false
    private var insidePreformatted = false
    private var linesBeforeIndent = -1 // 0 == we're indenting right now

    /**
     * Retrieve HTML from buffer. This method does actual line-by-line parsing of TROFF format
     * @return String containing HTML-formatted man page
     * @throws IOException on reading error
     */
    @get:Throws(IOException::class)
    val html: String
        get() {
            doParse()
            return postprocessInDocLinks(result).html()
        }

    /**
     * @see .getHtml
     */
    @get:Throws(IOException::class)
    val doc: Document
        get() {
            doParse()
            return postprocessInDocLinks(result)
        }

    @Throws(IOException::class)
    private fun doParse() {
        result.append("<html><body>")
        var line = ""
        while (source.readLine()?.apply { line = this } != null) {
            while (line.endsWith("\\")) { // take next line too
                var nextLine: String
                if (source.readLine().also { nextLine = it } != null) {
                    line = line.substring(0, line.length - 2) + nextLine
                }
            }
            if (line.startsWith("'") || line.startsWith(".\\") || line.startsWith("\\\\")) continue  // beginning of message or comment, skip...
            if (isControl(line)) {
                evaluateCommand(line)
            } else {
                result.append(" ").append(parseTextField(line, false))
            }
            handleSpecialConditions()
            previousLine = line
        }
        if (insideParagraph) result.append("</p>")
        result.append("</body></html>")
    }

    /**
     * Handle some special conditions while line-parsing.
     * For example, .TP directive causes the line **after** next line to be indented
     *
     */
    private fun handleSpecialConditions() {
        if (linesBeforeIndent > 0) {
            when (--linesBeforeIndent) {
                1 -> result.append("<dl><dt>")
                0 -> result.append("</dt><dd>")
            }
        }
    }

    /**
     * Does what encountered command requires. Output is written to [.result]
     * @param line whole line containing command + arguments
     */
    private fun evaluateCommand(line: String) {
        if (line.length < 2) return  // less than dot + 1 chars - it can't be command

        // let's try to extract command from the line
        val firstWord: String
        val lineAfterCommand: String
        val firstSpace = line.indexOf(" ")
        if (firstSpace != -1) {
            firstWord = line.substring(1, firstSpace) // word before first space
            lineAfterCommand = line.substring(firstSpace + 1)
        } else {
            firstWord = line.substring(1) // till the end of line
            lineAfterCommand = ""
        }
        try {
            val command = Command.valueOf(firstWord.lowercase())
            if (command.stopsIndentation) {
                if (linesBeforeIndent == 0) { // we were indenting right now, reset
                    result.append("</dd></dl>")
                    linesBeforeIndent = -1
                }
            }
            executeCommand(command, lineAfterCommand)
            previousCommand = command
        } catch (iae: IllegalArgumentException) {
            Log.w(Utils.MM_TAG, "Man2Html - unimplemented control", iae)
            // skip...
        }
    }

    private fun executeCommand(command: Command, lineAfterCommand: String) {
        var lineAfterCommand: String? = lineAfterCommand
        when (command) {
            Command.so -> {
                val refArgs = parseQuotedCommandArguments(lineAfterCommand)
                val refFilename = refArgs[0].substringAfter('/')
                val refSection = refFilename.substringAfter('.')
                val refName = refFilename.substringBefore('.')
                // this commands to include another page, but we need to somehow present it as a manpage
                result.append("<div class='man-page'>")
                result.append("<div id='NOTE' class='section'>")
                result.append("<h2><a href='#NOTE'>NOTE</a></h2> This manpage is a reference to another. ")
                result.append("See <b><a href='/${refArgs[0]}'>$refName ($refSection)</a></b>.")
                result.append("</div>")
                result.append("</div>")

            }
            Command.th, Command.dt -> {
                val titleArgs = parseQuotedCommandArguments(lineAfterCommand)
                if (titleArgs.isNotEmpty()) {  // take only name of command
                    result.append("<div class='man-page'>") // it'd be better to close it somehow...
                    result.append("<h1>").append(parseTextField(titleArgs[0], false)).append("</h1>")
                }
            }
            Command.pp, Command.lp, Command.p, Command.sp -> {
                if (insideParagraph) {
                    result.append("</p>")
                }
                insideParagraph = true
                result.append("<p>")
            }
            Command.sh -> {
                if (insideSection) {
                    result.append("</div>")
                }
                val subHeaderArgs = parseQuotedCommandArguments(lineAfterCommand)
                if (!subHeaderArgs.isEmpty()) {
                    val shName = parseTextField(subHeaderArgs[0], true)
                    result.append("<div id='$shName' class='section'>")
                            .append("<h2>")
                            .append("<a href='#$shName'>$shName</a>")
                            .append("</h2>")
                }
                insideSection = true
            }
            Command.fl -> {
                val options = parseTextField(lineAfterCommand, true)
                val wordMatcher = Pattern.compile("\\w+")
                val wMatcher = wordMatcher.matcher(options)
                val optionsDashed = wMatcher.replaceAll("-$0")
                result.append(" ").append(optionsDashed)
            }
            Command.op -> {
                result.append(" [")
                val options = parseTextField(lineAfterCommand, true)
                var dashedOption = false
                var argument = false
                for (option in options.split(" ".toRegex()).toTypedArray()) {
                    if (option.equals(Command.fl.name, ignoreCase = true)) {
                        dashedOption = true
                        continue
                    }
                    if (option.equals(Command.ar.name, ignoreCase = true)) {
                        argument = true
                        continue
                    }
                    result.append(if (argument) "<i>" else "")
                            .append(if (dashedOption) "-" else "")
                            .append(option)
                            .append(if (argument) "</i>" else "")
                    argument = false
                    dashedOption = argument
                }
                result.append("]")
            }
            Command.it -> {
                result.append("<dl><dd>")
                evaluateCommand(".$lineAfterCommand")
                result.append("</dd></dl>")
            }
            Command.bl, Command.rs -> result.append("<dl><dd>")
            Command.el, Command.re -> result.append("</dd></dl>")
            Command.bi -> {
                result.append(" ").append("<b><i>")
                if (insidePreformatted && lineAfterCommand!!.contains("\"")) { // function specification?
                    val args = parseQuotedCommandArguments(lineAfterCommand)
                    for (arg in args) {
                        result.append(arg)
                    }
                    result.append("</i></b>").append("\n")
                } else {
                    result.append(parseTextField(lineAfterCommand, true))
                    result.append("</i></b>").append(" ")
                }
            }
            Command.nm -> {
                run {
                    val wordMatcher = Pattern.compile("\\w+")
                    val wMatcher = wordMatcher.matcher(lineAfterCommand)
                    val commandNameFound = wMatcher.find()
                    if (commandNameFound && !manpageName.isNullOrBlank()) {
                        manpageName = wMatcher.group()
                    }
                    if (isControl(previousLine)) {
                        result.append("<br/>")
                    }
                    if (!commandNameFound && !manpageName.isNullOrBlank()) {
                        lineAfterCommand = manpageName
                    }
                }
                result.append(" ").append("<b>").append(parseTextField(lineAfterCommand, true)).append("</b>").append(" ")
            }
            Command.b -> result.append(" ").append("<b>").append(parseTextField(lineAfterCommand, true)).append("</b>").append(" ")
            Command.ar, Command.i -> result.append(" ").append("<i>").append(parseTextField(lineAfterCommand, true)).append("</i>").append(" ")
            Command.ri, Command.rb, Command.ir, Command.ib -> result.append(" ").append(parseTextField(lineAfterCommand, true)).append(" ")
            Command.br -> {
                if (lineAfterCommand!!.isEmpty()) {
                    // line break
                    result.append("<br/>")
                } else {
                    val words = parseTextField(lineAfterCommand, true).split(" ".toRegex()).toTypedArray()
                    if (words.size == 2 && words[1].matches("\\(\\d\\w?\\).*".toRegex())) {
                        // special case, it's a "see also reference", put it into anchor
                        val sectionMatch = Regex("\\((\\d\\w?)\\)(.*)").find(words[1])!!
                        val section = sectionMatch.groups[1]!!.value
                        val leftover = sectionMatch.groups[2]!!.value

                        result
                            .append(" ")
                            .append("<a href='/man${section}/${words[0]}.${section}'>")
                            .append("<b>${words[0]}</b>($section)")
                            .append("</a>")
                            .append(leftover)

                        return
                    }

                    // not a reference, continue

                    // first word is bold...
                    result.append(" ").append("<b>").append(words[0]).append("</b>")

                    // others are regular
                    var i = 1
                    while (i < words.size) {
                        result.append(" ").append(words[i])
                        ++i
                    }
                }
            }
            Command.tp -> linesBeforeIndent = 2
            Command.ip -> {
                if (lineAfterCommand!!.startsWith("\"")) { // quoted arg
                    if (!lineAfterCommand!!.startsWith("\"\"")) { // not empty (hack?)
                        val notationArgs = parseQuotedCommandArguments(lineAfterCommand)
                        if (notationArgs.isNotEmpty()) {
                            result.append("<dl><dt>").append(parseTextField(notationArgs[0], true)).append("</dt><dd>")
                        }
                    } else {
                        result.append("<dl><dd>")
                    }
                } else {
                    result.append("<dl><dt>").append(parseTextField(lineAfterCommand, true)).append("</dt><dd>")
                }
                linesBeforeIndent = 0
            }
            Command.nf -> {
                result.append("<pre>")
                insidePreformatted = true
            }
            Command.fi -> {
                insidePreformatted = false
                result.append("</pre>")
                result.append(" ").append(parseTextField(lineAfterCommand, true))
            }
            Command.nd -> result.append(" - ").append(parseTextField(lineAfterCommand, true))
        }
    }

    /**
     * Parses quoted command arguments, such as
     * <br></br>
     * <br></br>
     * `"YAOURT" "8" "2014\-06\-06" → [YAOURT, 8, 2014\-06\-06] `
     * @param line string after command name and space
     * @return list of arguments without quote symbols
     */
    private fun parseQuotedCommandArguments(line: String?): List<String> {
        val splitStrings = line!!.split("\"".toRegex()).toTypedArray()
        val results: MutableList<String> = ArrayList(splitStrings.size)
        for (str in splitStrings) {
            if (str.isEmpty() || str.isBlank())
                continue

            results.add(str)
        }
        return results
    }

    /**
     * Post-process already ready output. Adds mankier.com-like links for page, cleans html output
     * like closing tags, pretty-print etc. This is the only function dependant on Jsoup, get rid
     * of it if you want clean experience without mankier.com-related changes.
     * @param sb string builde containing ready for post-processing page.
     * @return String representing ready page.
     */
    private fun postprocessInDocLinks(sb: StringBuilder): Document {
        // process OPTIONS section
        val doc = Jsoup.parse(sb.toString())
        val options = doc.select("dl > dt b:matches($OPTION_PATTERN)")
        val availableOptions: MutableSet<String> = HashSet(options.size)
        for (option in options) {
            val anchor = Element(Tag.valueOf("a"), doc.baseUri())
            anchor.attr("id", option.ownText())
            anchor.attr("href", "#" + option.ownText())
            anchor.addClass("in-doc")
            anchor.appendChild(option.clone())
            option.replaceWith(anchor)
            availableOptions.add(option.ownText())
        }

        // process other references (don't put id on them)
        val optionMentions = doc.select("b:matches($OPTION_PATTERN)")
        for (option in optionMentions) {
            if (options.contains(option)) {
                // this element is a main description and already has a link
                continue
            }

            if (availableOptions.contains(option.ownText())) {
                val anchor = Element(Tag.valueOf("a"), doc.baseUri())
                anchor.attr("href", "#" + option.ownText())
                anchor.addClass("in-doc")
                anchor.appendChild(option.clone())
                option.replaceWith(anchor)
            }
        }

        return doc
    }

    /**
     * Parses text line and replaces all the GROFF escape symbols with appropriate UTF-8 characters.
     * Also handles font change.
     * @param text escaped line
     * @return GROFF-unescaped string convenient for html inserting
     */
    private fun parseTextField(text: String?, withinCommand: Boolean): String {
        val length = text!!.length
        val output = StringBuilder(length) // real html result
        val tempTextBuffer = StringBuilder(100) // temporary holder for html-escaping
        var insideQuote = false
        var previousChar = 0.toChar()
        var i = 0
        while (i < length) {
            val c = text[i]
            if (c == '"') {
                insideQuote = !insideQuote
                ++i
                continue
            }
            if (!insideQuote && withinCommand && previousChar == ' ' && c == ' ') {
                ++i
                continue
            }
            if (c == '\\' && length > i + 1) { // escape directive/character and not last in line
                output.append(escapeHtml(tempTextBuffer))
                tempTextBuffer.setLength(0) // append temporary text
                val firstEscapeChar = text[++i]
                when (firstEscapeChar) {
                    'f' -> if (length > i + 1) {
                        // get rid of the old one...
                        when (fontState) {
                            FontState.BOLD -> output.append("</b>")
                            FontState.ITALIC -> output.append("</i>")
                        }
                        when (text[++i]) {
                            'B' -> {
                                fontState = FontState.BOLD
                                output.append("<b>")
                            }
                            'I' -> {
                                fontState = FontState.ITALIC
                                output.append("<i>")
                            }
                            'R', 'P' -> fontState = FontState.NORMAL
                            '1', '2', '3' -> {
                            }
                        }
                    }
                    '(' -> if (length > i + 2) {
                        val code = text.substring(i + 1, i + 3)
                        tempTextBuffer.append(SpecialsHandler.parseSpecial(code))
                        i += 2
                    }
                    '[' -> {
                        val closingBracketIndex = text.indexOf(']', i)
                        if (closingBracketIndex != -1) {
                            val code = text.substring(i + 1, closingBracketIndex)
                            tempTextBuffer.append(SpecialsHandler.parseSpecial(code))
                            i = closingBracketIndex
                        }
                    }
                    '&', '^' -> {
                    }
                    '*' -> i += 3
                    else -> tempTextBuffer.append(firstEscapeChar)
                }
            } else {
                tempTextBuffer.append(c)
            }
            previousChar = c
            ++i
        }
        output.append(escapeHtml(tempTextBuffer)) // add all from temp buffer if remaining
        if (insidePreformatted) { // newlines should be preserved
            output.append("\n")
        }
        return output.toString()
    }

    /**
     * GROFF control statements start with dot or asterisk
     * @param line line to check
     * @return true if this line is control statement, false if it's just a text
     */
    private fun isControl(line: String?): Boolean {
        return line!!.startsWith(".") || line.startsWith("'")
    }

    companion object {
        private const val OPTION_PATTERN = "^--?[a-zA-Z-=]+$"
    }
}