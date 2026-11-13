package com.yogi.exitarrow.shape

/** Built-in shape catalogue. Templates are vector/rule based and require no image assets. */
object ShapeLibrary {
    private val baseTemplates = listOf(
        ShapeTemplate("heart", "Heart", ShapeCategory.GEOMETRIC, ShapeKind.HEART),
        ShapeTemplate("diamond", "Diamond", ShapeCategory.GEOMETRIC, ShapeKind.DIAMOND),
        ShapeTemplate("star", "Star", ShapeCategory.GEOMETRIC, ShapeKind.STAR),
        ShapeTemplate("butterfly", "Butterfly", ShapeCategory.ANIMAL, ShapeKind.BUTTERFLY),
        ShapeTemplate("fish", "Fish", ShapeCategory.ANIMAL, ShapeKind.FISH),
        ShapeTemplate("elephant", "Elephant", ShapeCategory.ANIMAL, ShapeKind.ELEPHANT),
        ShapeTemplate("rooster", "Rooster", ShapeCategory.ANIMAL, ShapeKind.ROOSTER),
        ShapeTemplate("tree", "Tree", ShapeCategory.OBJECT, ShapeKind.TREE),
        ShapeTemplate("house", "House", ShapeCategory.OBJECT, ShapeKind.HOUSE),
        ShapeTemplate("car", "Car", ShapeCategory.OBJECT, ShapeKind.CAR),
        ShapeTemplate("rocket", "Rocket", ShapeCategory.OBJECT, ShapeKind.ROCKET)
    )

    private val letterRows = mapOf(
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
        'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to listOf("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
        'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
        'J' to listOf("00111", "00010", "00010", "00010", "10010", "10010", "01100"),
        'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to listOf("10001", "11001", "11001", "10101", "10011", "10011", "10001"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to listOf("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
        'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111")
    )

    private val digitRows = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
        '6' to listOf("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00001", "01110")
    )

    val all: List<ShapeTemplate> = buildList {
        addAll(baseTemplates)
        letterRows.keys.sorted().forEach { glyph ->
            add(ShapeTemplate("letter_${glyph.lowercaseChar()}", "Letter $glyph", ShapeCategory.ALPHABET, ShapeKind.ALPHABET, glyph.toString()))
        }
        digitRows.keys.sorted().forEach { glyph ->
            add(ShapeTemplate("number_$glyph", "Number $glyph", ShapeCategory.NUMBER, ShapeKind.NUMBER, glyph.toString()))
        }
    }

    fun templateForLevel(level: Int): ShapeTemplate {
        val safeLevel = level.coerceAtLeast(1)
        return all[(safeLevel - 1) % all.size]
    }

    fun templateById(id: String): ShapeTemplate? = all.firstOrNull { it.id == id }

    fun glyphRows(template: ShapeTemplate): List<String>? {
        val glyph = template.glyph?.firstOrNull() ?: return null
        return when (template.kind) {
            ShapeKind.ALPHABET -> letterRows[glyph]
            ShapeKind.NUMBER -> digitRows[glyph]
            else -> null
        }
    }
}
