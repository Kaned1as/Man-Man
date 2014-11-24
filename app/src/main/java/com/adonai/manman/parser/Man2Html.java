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
        ITALIC
    }

    private enum Command {
        TH, SH, PP, RS, RE, // headers, titles
        B, I,               // font directives
        ie, el, nh, ad, sp  // conditionals and stuff...
    }

    private BufferedReader source;
    private StringBuilder result = new StringBuilder();

    private FontState fontState;
    private int fontSize;
    private boolean insideParagraph = false;


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
                result.append(parseTextField(line));
            }
        }
        if(insideParagraph)
            result.append("</p>");
        result.append("</body></html>");
        return result.toString();
    }

    /**
     * Does what encountered command requires. Output is written to {@link #result}
     * @param line whole line containing command + arguments
     */
    private void evaluateCommand(String line) {
        if(line.startsWith("'") || line.startsWith(".\\"))
            return; // beginning of message or comment, skip...

        if(line.length() < 3)
            return; // less than dot + 2 chars - t can't be command

        try {
            Command command = Command.valueOf(line.substring(1, 3));
            List<String> args = parseQuotedCommandArguments(line.substring(3));
            switch (command) {
                case TH:
                    if(!args.isEmpty()) {
                        result.append("<h1>").append(parseTextField(args.get(0))).append("</h1>");
                    }
                    break;
                case PP:
                    if(insideParagraph)
                        result.append("</p>");

                    insideParagraph = true;
                    result.append("<p>");
                case SH:
                    if(!args.isEmpty()) {
                        result.append("<h2>").append(parseTextField(args.get(0))).append("</h2>");
                    }
                    break;
                case RS:
                    result.append("<dl><dd>");
                    break;
                case RE:
                    result.append("</dd></dl>");
                    break;
                case sp:
                    result.append("<br/>");
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
                                    fontState = FontState.BOLD;
                                    output.append("<b>");
                                    break;
                                case 'I':
                                    fontState = FontState.ITALIC;
                                    output.append("<i>");
                                case 'R':
                                    switch (fontState) {
                                        case BOLD:
                                            output.append("</b>");
                                            break;
                                        case ITALIC:
                                            output.append("</i>");
                                    }
                                    fontState = FontState.NORMAL;
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
