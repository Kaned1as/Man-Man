package com.adonai.manman.parser

/**
 * Helper class to deal with GROFF special characters
 *
 * @author Kanedias
 */
object SpecialsHandler {
    /**
     * Returns appropriate UTF-8 character for requested GROFF special code
     * @param code code to look for
     * @return string of one  UTF-8 character (can't use char here, they're 2-byte almost always)
     */
    fun parseSpecial(code: String?): String {
        return when (code) {
            "-D" -> "Ð"
            "Sd" -> "ð"
            "TP" -> "Þ"
            "Tp" -> "þ"
            "ss" -> "ß"
            "AE" -> "Æ"
            "ae" -> "æ"
            "OE" -> "Œ"
            "oe" -> "œ"
            ".i" -> "ı"
            "'A" -> "Á"
            "'E" -> "É"
            "'I" -> "Í"
            "'O" -> "Ó"
            "'U" -> "Ú"
            "'Y" -> "Ý"
            "'a" -> "á"
            "'e" -> "é"
            "'i" -> "í"
            "'o" -> "ó"
            "'u" -> "ú"
            "'y" -> "ý"
            ":A" -> "Ä"
            ":E" -> "Ë"
            ":I" -> "Ï"
            ":O" -> "Ö"
            ":U" -> "Ü"
            ":Y" -> "Ÿ"
            ":a" -> "ä"
            ":e" -> "ë"
            ":i" -> "ï"
            ":o" -> "ö"
            ":u" -> "ü"
            ":y" -> "ÿ"
            "^A" -> "Â"
            "^E" -> "Ê"
            "^I" -> "Î"
            "^O" -> "Ô"
            "^U" -> "Û"
            "^a" -> "â"
            "^e" -> "ê"
            "^i" -> "î"
            "^o" -> "ô"
            "^u" -> "û"
            "`A" -> "À"
            "`E" -> "È"
            "`I" -> "Ì"
            "`O" -> "Ò"
            "`U" -> "Ù"
            "`a" -> "à"
            "`e" -> "è"
            "`i" -> "ì"
            "`o" -> "ò"
            "`u" -> "ù"
            "~A" -> "Ã"
            "~N" -> "Ñ"
            "~O" -> "Õ"
            "~a" -> "ã"
            "~n" -> "ñ"
            "~o" -> "õ"
            "vS" -> "Š"
            "vs" -> "š"
            "vZ" -> "Ž"
            "vz" -> "ž"
            ",C" -> "Ç"
            ",c" -> "ç"
            "/L" -> "Ł"
            "/l" -> "ł"
            "/O" -> "Ø"
            "/o" -> "ø"
            "oA" -> "Å"
            "oa" -> "å"
            "a\"" -> "\""
            "a-" -> "¯"
            "a." -> "˙"
            "a^" -> "^"
            "aa" -> "'"
            "ga" -> "`"
            "ab" -> "˘"
            "ac" -> "¸"
            "ad" -> "¨"
            "ah" -> "ˇ"
            "ao" -> "˚"
            "a~" -> "~"
            "ho" -> "˛"
            "ha" -> "^"
            "ti" -> "∼"
            "Bq" -> "\""
            "bq" -> "'"
            "lq" -> "\""
            "rq" -> "\""
            "oq" -> "'"
            "cq" -> "’"
            "aq" -> "‘"
            "dq" -> "\""
            "Fo" -> "«"
            "Fc" -> "»"
            "fo" -> "‹"
            "fc" -> "›"
            "r!" -> "¡"
            "r?" -> "¿"
            "em" -> "-"
            "en" -> "-"
            "hy" -> "-"
            "lB" -> "["
            "rB" -> "]"
            "lC" -> "{"
            "rC" -> "}"
            "la" -> "<"
            "ra" -> ">"
            "<-" -> "←"
            "->" -> "→"
            "<>" -> "↔"
            "da" -> "↓"
            "ua" -> "⇑"
            "lA" -> "⇐"
            "rA" -> "⇒"
            "hA" -> "⇔"
            "dA" -> "⇓"
            "uA" -> "⇑"
            "an" -> "─"
            "or" -> "|"
            "ba" -> "|"
            "br" -> "│"
            "ru" -> "_"
            "ul" -> "_"
            "bv" -> "│"
            "bb" -> "¦"
            "sl" -> "/"
            "rs" -> "\\"
            "ci" -> "◯"
            "bu" -> "•"
            "dd" -> "‡"
            "dg" -> "†"
            "lz" -> "◊"
            "sq" -> "□"
            "ps" -> "¶"
            "sc" -> "§"
            "lh" -> "☜"
            "rh" -> "☞"
            "at" -> "@"
            "sh" -> "#"
            "CR" -> "␍"
            "co" -> "©"
            "rg" -> "®"
            "tm" -> "™"
            "Do" -> "$"
            "ct" -> "¢"
            "eu" -> "€"
            "Eu" -> "€"
            "Ye" -> "¥"
            "Po" -> "£"
            "Cs" -> "¤"
            "Fn" -> "ƒ"
            "de" -> "°"
            "%0" -> "‰"
            "fm" -> "′"
            "sd" -> "″"
            "mc" -> "µ"
            "Of" -> "ª"
            "Om" -> "º"
            "AN" -> "∧"
            "OR" -> "∨"
            "no" -> "¬"
            "te" -> "∃"
            "fa" -> "∀"
            "st" -> "∋"
            "3d" -> "∴"
            "tf" -> "∴"
            "12" -> "½"
            "14" -> "¼"
            "34" -> "¾"
            "S1" -> "¹"
            "S2" -> "²"
            "S3" -> "³"
            "pl" -> "+"
            "+-" -> "±"
            "t+-" -> "±"
            "pc" -> "·"
            "md" -> "⋅"
            "mu" -> "×"
            "tmu" -> "×"
            "c*" -> "⊗"
            "c+" -> "⊕"
            "di" -> "÷"
            "tdi" -> "÷"
            "f/" -> "⁄"
            "**" -> "∗"
            "<=" -> "≤"
            ">=" -> "≥"
            "!=" -> "≠"
            "eq" -> "="
            "==" -> "≡"
            "=~" -> "≅"
            "ap" -> "∼"
            "~~" -> "≈"
            "~=" -> "≈"
            "pt" -> "∝"
            "es" -> "∅"
            "mo" -> "∈"
            "nm" -> "∉"
            "nb" -> "⊄"
            "sb" -> "⊂"
            "sp" -> "⊃"
            "ib" -> "⊆"
            "ip" -> "⊇"
            "ca" -> "∩"
            "cu" -> "∪"
            "/_" -> "∠"
            "pp" -> "⊥"
            "is" -> "∫"
            "sum" -> "∑"
            "product" -> "∏"
            "gr" -> "∇"
            "sr" -> "√"
            "rn" -> "‾"
            "if" -> "∞"
            "Ah" -> "ℵ"
            "Im" -> "ℑ"
            "Re" -> "ℜ"
            "wp" -> "℘"
            "pd" -> "∂"
            "*A" -> "Α"
            "*B" -> "Β"
            "*C" -> "Ξ"
            "*D" -> "Δ"
            "*E" -> "Ε"
            "*F" -> "Φ"
            "*G" -> "Γ"
            "*H" -> "Θ"
            "*I" -> "Ι"
            "*K" -> "Κ"
            "*L" -> "Λ"
            "*M" -> "Μ"
            "*N" -> "Ν"
            "*O" -> "Ο"
            "*P" -> "Π"
            "*Q" -> "Ψ"
            "*R" -> "Ρ"
            "*S" -> "Σ"
            "*T" -> "Τ"
            "*U" -> "Υ"
            "*W" -> "Ω"
            "*X" -> "Χ"
            "*Y" -> "Η"
            "*Z" -> "Ζ"
            "*a" -> "α"
            "*b" -> "β"
            "*c" -> "ξ"
            "*d" -> "δ"
            "*e" -> "ε"
            "*f" -> "φ"
            "+f" -> "ϕ"
            "*g" -> "γ"
            "*h" -> "θ"
            "+h" -> "ϑ"
            "*i" -> "ι"
            "*k" -> "κ"
            "*l" -> "λ"
            "*m" -> "μ"
            "*n" -> "ν"
            "*o" -> "ο"
            "*p" -> "π"
            "+p" -> "ϖ"
            "*q" -> "ψ"
            "*r" -> "ρ"
            "*s" -> "σ"
            "*t" -> "τ"
            "*u" -> "υ"
            "*w" -> "ω"
            "*x" -> "χ"
            "*y" -> "η"
            "*z" -> "ζ"
            "ts" -> "ς"
            "CL" -> "♣"
            "SP" -> "♠"
            "HE" -> "♥"
            "DI" -> "♦"
            "mi" -> "-"
            else -> ""
        }
    }
}