package com.adonai.manman.parser;

import android.support.annotation.NonNull;

import org.jsoup.helper.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple (quick & draft) TROFF to HTML parser
 * <br/>
 * <br/>
 * Contains almost all code required for man page parsing. While TROFF represents highly complicated format,
 * the amount of specific code in man is limited to titles, names, descriptions, character specials, font style.
 * Thus this converter focuses on man contents and does not implement such things as registers/evaluations/tables etc.
 *
 * @author Adonai
 */
public class Man2Html {

    private enum FontState {
        NORMAL,
        BOLD,
        ITALIC,
    }

    private enum Command {
        TH(true), SH(true), PP(true), RS, RE,       // headers, titles
        TP(true), IP(true),                         // special triggers
        B, I, BR, BI,                               // font directives
        fi, ie, el, nh, ad, sp(true);               // conditionals and stuff...

        private boolean stopsIndentation;

        Command() {
        }

        Command(boolean stopsIndentation) {
            this.stopsIndentation = stopsIndentation;
        }

        public boolean stopsIndentation() {
            return stopsIndentation;
        }
    }

    private BufferedReader source;
    private StringBuilder result = new StringBuilder();

    private FontState fontState;
    private boolean insideParagraph;
    private int linesBeforeIndent = -1; // 0 == we're indenting right now

    public Man2Html(BufferedReader source) {
        this.source = source;
    }

    /**
     * Retrieve HTML from buffer. This method does actual line-by-line parsing of TROFF format
     * @return String containing HTML-formatted man page
     * @throws IOException on reading error
     */
    @NonNull
    public String getHtml() throws IOException {
        result.append("<html><body>");
        String line;
        while((line = source.readLine()) != null) {
            if(isControl(line)) {
                evaluateCommand(line);
            } else {
                result.append(" ").append(parseTextField(line));
            }
            handleSpecialConditions();
        }
        if(insideParagraph)
            result.append("</p>");
        result.append("</body></html>");
        return result.toString();
    }

    private void handleSpecialConditions() {
        if(linesBeforeIndent > 0) {
            switch (--linesBeforeIndent) {
                case 1:
                    result.append("<dl><dt>");
                    break;
                case 0:
                    result.append("</dt><dd>");
                    break;
            }
        }
    }

    /**
     * Does what encountered command requires. Output is written to {@link #result}
     * @param line whole line containing command + arguments
     */
    private void evaluateCommand(String line) {
        if(line.startsWith("'") || line.startsWith(".\\"))
            return; // beginning of message or comment, skip...

        if(line.length() < 2)
            return; // less than dot + 1 chars - it can't be command

        // let's try to extract command from the line
        String firstWord;
        String lineAfterCommand;

        int firstSpace = line.indexOf(" ");
        if(firstSpace != -1) {
            firstWord = line.substring(1, firstSpace); // word before first space
            lineAfterCommand = line.substring(firstSpace + 1);
        } else {
            firstWord = line.substring(1); // till the end of line
            lineAfterCommand = "";
        }

        try {
            Command command = Command.valueOf(firstWord);
            if(command.stopsIndentation) {
                if(linesBeforeIndent == 0) { // we were indenting right now, reset
                    result.append("</dd></dl>");
                    linesBeforeIndent = -1;
                }
            }
            switch (command) {
                case TH: // table header
                    List<String> titleArgs = parseQuotedCommandArguments(lineAfterCommand);
                    if(!titleArgs.isEmpty()) {  // take only name of command
                        result.append("<h1>").append(parseTextField(titleArgs.get(0))).append("</h1>");
                    }
                    break;
                case PP: // paragraph
                case sp: // line break
                    if(insideParagraph) {
                        result.append("</p>");
                    }

                    insideParagraph = true;
                    result.append("<p>");
                    break;
                case SH: // sub header
                    List<String> subHeaderArgs = parseQuotedCommandArguments(lineAfterCommand);
                    if(!subHeaderArgs.isEmpty()) {
                        result.append("<h2>").append(parseTextField(subHeaderArgs.get(0))).append("</h2>");
                    }
                    break;
                case RS: // indent start
                    result.append("<dl><dd>");
                    break;
                case RE: // indent end
                    result.append("</dd></dl>");
                    break;
                case BI:
                    result.append(" ").append("<b><i>").append(parseTextField(lineAfterCommand)).append("</i></b>").append(" ");
                    break;
                case B: // bold
                    result.append(" ").append("<b>").append(parseTextField(lineAfterCommand)).append("</b>").append(" ");
                    break;
                case I: // italic
                    result.append(" ").append("<i>").append(parseTextField(lineAfterCommand)).append("</i>").append(" ");
                    break;
                case BR:
                    String[] words = lineAfterCommand.split(" ");
                    // first word is bold...
                    result.append(" ").append("<b>").append(parseTextField(words[0])).append("</b>");

                    // others are regular
                    for(int i = 1; i < words.length; ++i) {
                        result.append(" ").append(parseTextField(words[i]));
                    }
                    break;
                case TP: // indent after next line
                    linesBeforeIndent = 2;
                    break;
                case IP: // notation
                    if(lineAfterCommand.startsWith("\"")) { // quoted arg
                        if(!lineAfterCommand.startsWith("\"\"")) { // not empty (hack?)
                            List<String> notationArgs = parseQuotedCommandArguments(lineAfterCommand);
                            if (!notationArgs.isEmpty()) {
                                result.append("<dl><dt>").append(parseTextField(notationArgs.get(0))).append("</dt><dd>");
                            }
                        } else {
                            result.append("<dl><dd>");
                        }
                    } else {
                        result.append("<dl><dt>").append(parseTextField(lineAfterCommand)).append("</dt><dd>");
                    }
                    linesBeforeIndent = 0;
                    break;
                case fi: //re-enable margins
                    result.append(" ").append(parseTextField(lineAfterCommand));
                    break;
            }
        } catch (IllegalArgumentException iae) {
            // unimplemented control, skip...
        }
    }

    /**
     * Parses quoted command arguments, such as
     * <br/>
     * <br/>
     * {@code "YAOURT" "8" "2014\-06\-06" → [YAOURT, 8, 2014\-06\-06] }
     * @param line string after command name and space
     * @return list of arguments without quote symbols
     */
    @NonNull
    private List<String> parseQuotedCommandArguments(String line) {
        String[] splitStrings = line.split("\"");
        List<String> results = new ArrayList<>(splitStrings.length);
        for(String str : splitStrings) {
            if(str.isEmpty() || StringUtil.isBlank(str))
                continue;
            results.add(str);
        }
        return results;
    }

    @NonNull
    private String parseTextField(String text) {
        int length = text.length();
        StringBuilder output = new StringBuilder(length);      // real html result
        StringBuilder tempTextBuffer = new StringBuilder(100); // temporary holder for html-escaping
        for(int i = 0; i < length; ++i) {
            char c = text.charAt(i);
            if(c == '\\' && length > i + 1) { // escape directive/character and not last in line
                output.append(HtmlEscaper.escapeHtml(tempTextBuffer));
                tempTextBuffer.setLength(0);  // append temporary text

                char firstEscapeChar = text.charAt(++i);
                switch (firstEscapeChar) {
                    case 'f':    // change font
                        if(length > i + 1) {
                            switch (text.charAt(++i)) {
                                case 'B':
                                    if(fontState == FontState.ITALIC) {
                                        output.append("</i>");
                                    }
                                    fontState = FontState.BOLD;
                                    output.append("<b>");
                                    break;
                                case 'I':
                                    if(fontState == FontState.BOLD) {
                                        output.append("</b>");
                                    }
                                    fontState = FontState.ITALIC;
                                    output.append("<i>");
                                    break;
                                case 'R':
                                case 'P':
                                    switch (fontState) {
                                        case BOLD:
                                            output.append("</b>");
                                            break;
                                        case ITALIC:
                                            output.append("</i>");
                                            break;
                                    }
                                    fontState = FontState.NORMAL;
                                    break;
                                case '1': // nothing to do for now...
                                case '2':
                                case '3':
                                    break;
                            }
                        }
                        break;
                    case '(': // we're in trouble, it's special...
                        if(length > i + 2) {
                            String code = text.substring(i + 1, i + 3);
                            tempTextBuffer.append(SpecialsHandler.parseSpecial(code));
                            i += 2;
                        }
                        break;
                    case '[': // variable-length special
                        int closingBracketIndex = text.indexOf(']', i);
                        if(closingBracketIndex != -1) {
                            String code = text.substring(i + 1, closingBracketIndex);
                            tempTextBuffer.append(SpecialsHandler.parseSpecial(code));
                            i = closingBracketIndex;
                        }
                        break;
                    case '&': // non-printable zero-width, skip
                        break;
                    default:
                        tempTextBuffer.append(firstEscapeChar);
                }
            } else {
                tempTextBuffer.append(c);
            }
        }
        output.append(HtmlEscaper.escapeHtml(tempTextBuffer)); // add all from temp buffer if remaining
        return output.toString();
    }

    private boolean isControl(String line) {
        return line.startsWith(".") || line.startsWith("'");
    }
}
