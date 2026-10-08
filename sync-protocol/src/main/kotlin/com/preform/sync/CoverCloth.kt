package com.preform.sync

/**
 * The two colours of a typeset cover, chosen by genre like the cloth of a series: the
 * ground, and the ink set on it. Shared so a book without a cover looks the same on the
 * phone, the cover screen, and the PC.
 */
data class Cloth(val ground: Long, val ink: Long)

object CoverCloth {

    private val CLARET = Cloth(0xFF7A1F2B, 0xFFF1E6D4)
    private val NAVY = Cloth(0xFF1E3A5F, 0xFFE9E4D8)
    private val BOTTLE = Cloth(0xFF2E5E4E, 0xFFEDE8DC)
    private val OCHRE = Cloth(0xFFD9C27A, 0xFF1B1B1B)
    private val GRAPHITE = Cloth(0xFF2A2B2E, 0xFFE9E5DC)

    private val byGenre = mapOf(
        "Classics" to CLARET,
        "Fiction" to GRAPHITE,
        "Literary Fiction" to GRAPHITE,
        "Romance" to CLARET,
        "Historical Fiction" to CLARET,
        "Drama" to CLARET,
        "Poetry" to CLARET,
        "Fantasy" to BOTTLE,
        "Horror" to GRAPHITE,
        "Mystery & Crime" to GRAPHITE,
        "Thriller" to GRAPHITE,
        "Science Fiction" to NAVY,
        "Philosophy" to NAVY,
        "Religion & Spirituality" to NAVY,
        "Psychology" to NAVY,
        "Politics & Society" to NAVY,
        "History" to OCHRE,
        "Biography & Memoir" to OCHRE,
        "Travel" to OCHRE,
        "Art & Design" to OCHRE,
        "Cooking" to OCHRE,
        "Science & Nature" to BOTTLE,
        "Health & Fitness" to BOTTLE,
        "Technology & Computing" to GRAPHITE,
        "Business & Economics" to GRAPHITE,
        "Education & Reference" to GRAPHITE,
        "Self-Help" to BOTTLE,
        "Young Adult" to OCHRE,
        "Children's" to OCHRE,
        "Comics & Graphic Novels" to OCHRE,
    )

    /**
     * The cloth for a book's first genre; books with no genre get one picked steadily from
     * their title, so they vary without changing between launches or devices.
     */
    fun of(genres: List<String>, title: String): Cloth =
        genres.firstNotNullOfOrNull { byGenre[it] }
            ?: listOf(CLARET, NAVY, BOTTLE, GRAPHITE)[(title.hashCode() and Int.MAX_VALUE) % 4]
}
