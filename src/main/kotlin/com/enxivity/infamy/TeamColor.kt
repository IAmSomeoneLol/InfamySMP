package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object TeamColor {

    val COLORS = linkedMapOf<String, TextColor>()
    val COLOR_TO_LEGACY = mutableMapOf<String, String>()
    val SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    init {
        registerVanilla("white", NamedTextColor.WHITE, "&f")
        registerVanilla("yellow", NamedTextColor.YELLOW, "&e")
        registerVanilla("gold", NamedTextColor.GOLD, "&6")
        registerVanilla("red", NamedTextColor.RED, "&c")
        registerVanilla("dark_red", NamedTextColor.DARK_RED, "&4")
        registerVanilla("light_purple", NamedTextColor.LIGHT_PURPLE, "&d")
        registerVanilla("dark_purple", NamedTextColor.DARK_PURPLE, "&5")
        registerVanilla("blue", NamedTextColor.BLUE, "&9")
        registerVanilla("dark_blue", NamedTextColor.DARK_BLUE, "&1")
        registerVanilla("aqua", NamedTextColor.AQUA, "&b")
        registerVanilla("dark_aqua", NamedTextColor.DARK_AQUA, "&3")
        registerVanilla("green", NamedTextColor.GREEN, "&a")
        registerVanilla("dark_green", NamedTextColor.DARK_GREEN, "&2")
        registerVanilla("gray", NamedTextColor.GRAY, "&7")
        registerVanilla("dark_gray", NamedTextColor.DARK_GRAY, "&8")
        registerVanilla("black", NamedTextColor.BLACK, "&0")
        registerHex("ruby", 0xE0, 0x11, 0x5F)
        registerHex("sapphire", 0x0F, 0x52, 0xBA)
        registerHex("emerald", 0x50, 0xC8, 0x78)
        registerHex("amethyst", 0x99, 0x66, 0xCC)
        registerHex("turquoise", 0x40, 0xE0, 0xD0)
        registerHex("jade", 0x00, 0xA8, 0x6B)
        registerHex("aquamarine", 0x7F, 0xFF, 0xD4)
        registerHex("topaz", 0xFF, 0xC8, 0x7C)
        registerHex("amber", 0xFF, 0xBF, 0x00)
        registerHex("garnet", 0x73, 0x36, 0x35)
        registerHex("rose", 0xFF, 0x66, 0x99)
        registerHex("cherry", 0xDE, 0x31, 0x63)
        registerHex("crimson", 0xDC, 0x14, 0x3C)
        registerHex("scarlet", 0xFF, 0x24, 0x00)
        registerHex("coral", 0xFF, 0x7F, 0x50)
        registerHex("salmon", 0xFA, 0x80, 0x72)
        registerHex("peach", 0xFF, 0xE5, 0xB4)
        registerHex("orange", 0xFF, 0x8C, 0x00)
        registerHex("tangerine", 0xF2, 0x85, 0x00)
        registerHex("sunset", 0xFD, 0x5E, 0x53)
        registerHex("lemon", 0xFF, 0xF4, 0x4F)
        registerHex("banana", 0xFF, 0xE1, 0x35)
        registerHex("lime", 0x32, 0xCD, 0x32)
        registerHex("mint", 0x98, 0xFF, 0x98)
        registerHex("sage", 0xBC, 0xB8, 0x8A)
        registerHex("olive", 0x80, 0x80, 0x00)
        registerHex("forest", 0x22, 0x8B, 0x22)
        registerHex("teal", 0x00, 0x80, 0x80)
        registerHex("cyan", 0x00, 0xFF, 0xFF)
        registerHex("sky", 0x87, 0xCE, 0xEB)
        registerHex("cerulean", 0x00, 0x7B, 0xA7)
        registerHex("navy", 0x00, 0x00, 0x80)
        registerHex("indigo", 0x4B, 0x00, 0x82)
        registerHex("lavender", 0xE6, 0xE6, 0xFA)
        registerHex("lilac", 0xC8, 0xA2, 0xC8)
        registerHex("violet", 0x8A, 0x2B, 0xE2)
        registerHex("plum", 0x8E, 0x45, 0x85)
        registerHex("magenta", 0xFF, 0x00, 0xFF)
        registerHex("bubblegum", 0xFF, 0xC1, 0xCC)
        registerHex("brown", 0x8B, 0x45, 0x13)
        registerHex("coffee", 0x6F, 0x4E, 0x37)
        registerHex("chocolate", 0x7B, 0x3F, 0x00)
        registerHex("caramel", 0xAF, 0x6E, 0x4D)
        registerHex("bronze", 0xCD, 0x7F, 0x32)
        registerHex("silver", 0xC0, 0xC0, 0xC0)
        registerHex("platinum", 0xE5, 0xE4, 0xE2)
        registerHex("charcoal", 0x36, 0x45, 0x4F)
        registerHex("rust", 0xB7, 0x41, 0x0E)
        registerHex("pastel_pink", 0xFF, 0xD1, 0xDC)
        registerHex("pastel_blue", 0xAE, 0xC6, 0xCF)
        registerHex("pastel_green", 0x77, 0xDD, 0x77)
        registerHex("pastel_yellow", 0xFD, 0xFD, 0x96)
        registerHex("pastel_purple", 0xB3, 0x9E, 0xB5)
        registerHex("pastel_orange", 0xFF, 0xB3, 0x47)
        registerHex("neon_green", 0x39, 0xFF, 0x14)
        registerHex("neon_blue", 0x1F, 0x51, 0xFF)
        registerHex("neon_pink", 0xFF, 0x10, 0xF0)
        registerHex("neon_yellow", 0xE7, 0xFE, 0x00)
        registerHex("neon_cyan", 0x0F, 0xF0, 0xFC)
    }

    private fun registerVanilla(name: String, color: TextColor, code: String) {
        COLORS[name] = color
        COLOR_TO_LEGACY[name] = code
    }

    private fun registerHex(name: String, r: Int, g: Int, b: Int) {
        val color = TextColor.color(r, g, b)
        COLORS[name] = color
        COLOR_TO_LEGACY[name] = "&#" + color.asHexString().substring(1).uppercase()
    }

    fun getColorNames(): List<String> = ArrayList(COLORS.keys)

    fun getLegacyFormat(nameOrHex: String): String? {
        val lower = nameOrHex.lowercase()
        if (COLOR_TO_LEGACY.containsKey(lower)) {
            return COLOR_TO_LEGACY[lower]
        }
        if (lower.startsWith("#") && lower.length == 7) {
            return "&#" + lower.substring(1).uppercase()
        }
        return null
    }

    fun parseColor(input: String?): TextColor {
        if (input == null || input.isEmpty()) return NamedTextColor.WHITE
        val hexMatch = Regex("#([0-9a-fA-F]{6})").find(input)
        if (hexMatch != null) {
            val hex = TextColor.fromHexString("#" + hexMatch.groupValues[1])
            if (hex != null) return hex
        }

        val clean = input.lowercase().replace("§", "").replace("&", "")
        return when (clean) {
            "0" -> NamedTextColor.BLACK
            "1" -> NamedTextColor.DARK_BLUE
            "2" -> NamedTextColor.DARK_GREEN
            "3" -> NamedTextColor.DARK_AQUA
            "4" -> NamedTextColor.DARK_RED
            "5" -> NamedTextColor.DARK_PURPLE
            "6" -> NamedTextColor.GOLD
            "7" -> NamedTextColor.GRAY
            "8" -> NamedTextColor.DARK_GRAY
            "9" -> NamedTextColor.BLUE
            "a" -> NamedTextColor.GREEN
            "b" -> NamedTextColor.AQUA
            "c" -> NamedTextColor.RED
            "d" -> NamedTextColor.LIGHT_PURPLE
            "e" -> NamedTextColor.YELLOW
            "f" -> NamedTextColor.WHITE
            else -> COLORS[clean] ?: NamedTextColor.WHITE
        }
    }

    fun deserialize(text: String): Component {
        return SERIALIZER.deserialize(text)
    }
}