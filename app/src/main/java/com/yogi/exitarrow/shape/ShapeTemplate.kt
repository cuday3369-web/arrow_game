package com.yogi.exitarrow.shape

enum class ShapeCategory {
    ANIMAL,
    OBJECT,
    ALPHABET,
    NUMBER,
    GEOMETRIC,
    SYMBOL
}

enum class ShapeKind {
    HEART,
    DIAMOND,
    STAR,
    BUTTERFLY,
    FISH,
    ELEPHANT,
    ROOSTER,
    TREE,
    HOUSE,
    CAR,
    ROCKET,
    ALPHABET,
    NUMBER
}

enum class ShapeFormation {
    /** Keep only cells close to the silhouette boundary. */
    OUTLINE,

    /** Allow arrow paths throughout the complete silhouette. */
    FILLED,

    /** Keep the complete silhouette and bias paths toward its contour. */
    HYBRID
}

data class ShapeTemplate(
    val id: String,
    val displayName: String,
    val category: ShapeCategory,
    val kind: ShapeKind,
    val glyph: String? = null
)
