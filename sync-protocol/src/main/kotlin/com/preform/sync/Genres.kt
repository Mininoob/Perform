package com.preform.sync

/**
 * Turns the free-form subjects books carry ("FICTION / Fantasy / Epic", "Love stories",
 * "Detective and mystery stories") into a short, fixed list of genres, so every book
 * lands on a shelf someone would recognise.
 *
 * Shared by the PC and the phone so both sort a book the same way.
 */
object Genres {

    const val UNCATEGORIZED = "Uncategorized"

    /** Most specific first: "science fiction" must win over "science", "historical fiction" over "history". */
    private val rules: List<Pair<String, Regex>> = listOf(
        "Science Fiction" to words("science fiction", "sci-fi", "scifi", "dystopia", "dystopian", "space opera", "cyberpunk", "time travel"),
        "Fantasy" to words("fantasy", "fantasy fiction", "magic", "dragons", "wizards", "fairy tales"),
        "Mystery & Crime" to words("mystery", "mysteries", "detective", "detectives", "crime", "whodunit", "private investigators"),
        "Thriller" to words("thriller", "thrillers", "suspense", "espionage", "spy stories", "spies"),
        "Horror" to words("horror", "ghost stories", "ghosts", "vampires", "zombies", "supernatural"),
        "Romance" to words("romance", "romances", "love stories", "romantic"),
        "Historical Fiction" to words("historical fiction", "historical novel", "historical novels"),
        "Comics & Graphic Novels" to words("comics", "comic books", "graphic novel", "graphic novels", "manga"),
        "Young Adult" to words("young adult", "young adult fiction", "teen"),
        "Children's" to words("juvenile fiction", "juvenile literature", "children's", "childrens", "picture books"),
        "Poetry" to words("poetry", "poems", "verse"),
        "Drama" to words("drama", "plays", "theater", "theatre"),
        "Classics" to words("classics", "classic literature", "classic fiction"),
        "Biography & Memoir" to words("biography", "autobiography", "memoir", "memoirs", "biographies"),
        "History" to words("history", "historical", "civilization", "ancient", "medieval", "world war"),
        "Religion & Spirituality" to words("religion", "bible", "christianity", "christian", "spirituality", "theology", "judaism", "islam", "buddhism", "hinduism"),
        "Philosophy" to words("philosophy", "ethics", "metaphysics"),
        "Psychology" to words("psychology", "mental health"),
        "Self-Help" to words("self-help", "self help", "personal development", "motivational", "success"),
        "Business & Economics" to words("business", "economics", "finance", "investing", "management", "marketing", "entrepreneurship"),
        "Politics & Society" to words("politics", "political science", "government", "sociology", "social science", "current events"),
        "Technology & Computing" to words("computers", "computer science", "programming", "software", "technology", "engineering", "artificial intelligence"),
        "Science & Nature" to words("science", "physics", "chemistry", "biology", "astronomy", "mathematics", "nature", "evolution"),
        "Health & Fitness" to words("health", "fitness", "medicine", "medical", "nutrition", "diet"),
        "Cooking" to words("cooking", "cookbooks", "cookery", "recipes", "baking", "food"),
        "Art & Design" to words("art", "arts", "design", "photography", "architecture", "music", "painting"),
        "Travel" to words("travel", "guidebooks", "voyages and travels"),
        "Education & Reference" to words("reference", "dictionaries", "dictionary", "textbooks", "study guides", "language", "encyclopedias"),
        // Last: only when nothing more specific matched.
        "Fiction" to words("fiction", "novel", "novels", "literary fiction", "short stories"),
    )

    val ALL: List<String> = rules.map { it.first }

    /**
     * Up to [max] genres for a book, from its subjects. Each subject counts toward its
     * most specific genre; the most-voted genres win, ties going to the more specific.
     * Plain "Fiction" is only kept when nothing more specific turned up.
     */
    fun classify(subjects: List<String>, max: Int = 2): List<String> {
        val votes = HashMap<String, Int>()
        subjects.asSequence()
            // BISAC subjects put the useful part last: "FICTION / Fantasy / Epic".
            .flatMap { subject -> subject.split('/', '|', ';', '>').map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf(subject) }.asReversed() }
            .map { it.lowercase() }
            .forEach { part ->
                rules.firstOrNull { (_, pattern) -> pattern.containsMatchIn(part) }?.let { (genre, _) ->
                    votes[genre] = (votes[genre] ?: 0) + 1
                }
            }
        val ranked = votes.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { ALL.indexOf(it.key) })
            .map { it.key }
        val specific = ranked.filter { it != "Fiction" }
        return (specific.ifEmpty { ranked }).take(max)
    }

    private fun words(vararg terms: String): Regex =
        Regex(terms.joinToString("|", prefix = "\\b(", postfix = ")\\b") { Regex.escape(it) })
}
