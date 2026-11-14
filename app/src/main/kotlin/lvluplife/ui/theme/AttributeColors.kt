package lvluplife.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Presentation colors per attribute code (feature 002, FR-001). They carry no rules: the code is always
 * written next to the color (FR-003). Text colors are meant for white cards, where they reach 4.5:1.
 */
data class AttributeColor(val fill: Color, val text: Color)

object AttributeColors {
    private val byCode = mapOf(
        "STR" to AttributeColor(Color(0xFFE5484D), Color(0xFFC62A2F)),
        "DEX" to AttributeColor(Color(0xFF12A594), Color(0xFF0B7F72)),
        "CON" to AttributeColor(Color(0xFFE38B0B), Color(0xFFA85E00)),
        "INT" to AttributeColor(Color(0xFF7C5CE0), Color(0xFF6346C8)),
        "WIT" to AttributeColor(Color(0xFF2F7DE1), Color(0xFF1F63BE)),
        "MEN" to AttributeColor(Color(0xFFD6409F), Color(0xFFB02A80)),
    )

    fun of(code: String): AttributeColor = byCode.getValue(code)
}
