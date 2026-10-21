/**
 * The demonstration library: public-domain classics, shown the way Perform shows a book
 * without a cover art file, in one of the series' five cloths chosen by its first genre.
 */

export type Cloth = "claret" | "navy" | "bottle" | "ochre" | "graphite";

export interface Book {
  title: string;
  author: string;
  year: number;
  genre: string;
  /** Folder (shelf) it sits in, in the demo library. */
  shelf: string;
  /** How far it's been read, 0..1. */
  progress?: number;
  /** The messy name it arrived with in a downloads folder. */
  file: string;
}

const clothByGenre: Record<string, Cloth> = {
  Classics: "claret",
  Romance: "claret",
  "Historical Fiction": "claret",
  Drama: "claret",
  Poetry: "claret",
  "Science Fiction": "navy",
  Philosophy: "navy",
  Religion: "navy",
  Psychology: "navy",
  Politics: "navy",
  Fantasy: "bottle",
  "Science & Nature": "bottle",
  Health: "bottle",
  "Self-Help": "bottle",
  History: "ochre",
  Biography: "ochre",
  Travel: "ochre",
  Art: "ochre",
  "Young Adult": "ochre",
  "Children's": "ochre",
  Fiction: "graphite",
  Horror: "graphite",
  Mystery: "graphite",
  Thriller: "graphite",
};

export function clothOf(book: Pick<Book, "genre">): Cloth {
  return clothByGenre[book.genre] ?? "graphite";
}

export const books: Book[] = [
  { title: "Pride and Prejudice", author: "Jane Austen", year: 1813, genre: "Romance", shelf: "Classics", progress: 0.38, file: "pride_and_prejudice (1).epub" },
  { title: "Moby-Dick", author: "Herman Melville", year: 1851, genre: "Fiction", shelf: "Sea Stories", progress: 0.12, file: "MOBY DICK OR THE WHALE.epub" },
  { title: "Frankenstein", author: "Mary Shelley", year: 1818, genre: "Science Fiction", shelf: "Classics", progress: 1, file: "frankenstein-1818-text.epub" },
  { title: "Walden", author: "Henry David Thoreau", year: 1854, genre: "Science & Nature", shelf: "Essays", file: "walden_thoreau_v2.pdf" },
  { title: "The Odyssey", author: "Homer", year: -700, genre: "Classics", shelf: "Classics", file: "odyssey.butler.epub" },
  { title: "Meditations", author: "Marcus Aurelius", year: 180, genre: "Philosophy", shelf: "Essays", progress: 0.64, file: "Meditations (Long trans).epub" },
  { title: "Alice's Adventures in Wonderland", author: "Lewis Carroll", year: 1865, genre: "Children's", shelf: "For the Kids", file: "alice-wonderland.epub" },
  { title: "Treasure Island", author: "Robert Louis Stevenson", year: 1883, genre: "Young Adult", shelf: "Sea Stories", file: "treasure_island_FINAL.epub" },
  { title: "Dracula", author: "Bram Stoker", year: 1897, genre: "Horror", shelf: "Classics", file: "dracula.epub" },
  { title: "The Time Machine", author: "H. G. Wells", year: 1895, genre: "Science Fiction", shelf: "Science Fiction", file: "TheTimeMachine.pdf" },
  { title: "On the Origin of Species", author: "Charles Darwin", year: 1859, genre: "Science & Nature", shelf: "Essays", file: "origin of species 1st ed.pdf" },
  { title: "Middlemarch", author: "George Eliot", year: 1871, genre: "Classics", shelf: "Classics", file: "middlemarch (2).epub" },
  { title: "Leaves of Grass", author: "Walt Whitman", year: 1855, genre: "Poetry", shelf: "Poetry", file: "leaves-of-grass-1855.epub" },
  { title: "The Count of Monte Cristo", author: "Alexandre Dumas", year: 1844, genre: "Historical Fiction", shelf: "Classics", file: "count_of_monte_cristo.epub" },
  { title: "Jane Eyre", author: "Charlotte Brontë", year: 1847, genre: "Romance", shelf: "Classics", file: "Jane Eyre - Bronte.epub" },
  { title: "The Wind in the Willows", author: "Kenneth Grahame", year: 1908, genre: "Children's", shelf: "For the Kids", file: "wind_willows.epub" },
  { title: "The Picture of Dorian Gray", author: "Oscar Wilde", year: 1890, genre: "Fiction", shelf: "Classics", file: "the-picture-of-dorian-gray.epub" },
  { title: "The Republic", author: "Plato", year: -375, genre: "Philosophy", shelf: "Essays", file: "republic_jowett.pdf" },
];

export function book(title: string): Book {
  const found = books.find((b) => b.title === title);
  if (!found) throw new Error(`No demo book called ${title}`);
  return found;
}

export function yearLabel(year: number): string {
  return year < 0 ? `${-year} BC` : String(year);
}

/** "Austen", "Herbert": the surname the caption line uses. */
export function surname(author: string): string {
  const parts = author.split(/\s+/);
  return parts[parts.length - 1];
}

/** The opening of Pride and Prejudice (1813, public domain), for the reading spread. */
export const openingPages = {
  chapter: "Chapter 1",
  left: [
    "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
    "However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters.",
    "“My dear Mr. Bennet,” said his lady to him one day, “have you heard that Netherfield Park is let at last?”",
  ],
  right: [
    "Mr. Bennet replied that he had not.",
    "“But it is,” returned she; “for Mrs. Long has just been here, and she told me all about it.”",
    "Mr. Bennet made no answer.",
    "“Do you not want to know who has taken it?” cried his wife impatiently.",
    "“You want to tell me, and I have no objection to hearing it.”",
    "This was invitation enough.",
  ],
};
