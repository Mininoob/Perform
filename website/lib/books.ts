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
  Feminism: "bottle",
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
  { title: "A Room of One's Own", author: "Virginia Woolf", year: 1929, genre: "Feminism", shelf: "Feminist Classics", progress: 0.38, file: "a_room_of_ones_own (3rd read).epub" },
  { title: "A Vindication of the Rights of Woman", author: "Mary Wollstonecraft", year: 1792, genre: "Feminism", shelf: "Feminist Classics", progress: 0.12, file: "vindication_rights_of_woman.pdf" },
  { title: "The Yellow Wallpaper", author: "Charlotte Perkins Gilman", year: 1892, genre: "Fiction", shelf: "Feminist Classics", progress: 1, file: "the yellow wallpaper (annotated).epub" },
  { title: "Little Women", author: "Louisa May Alcott", year: 1868, genre: "Classics", shelf: "Currently Healing", progress: 1, file: "little_women_FINAL_cried.epub" },
  { title: "Mrs Dalloway", author: "Virginia Woolf", year: 1925, genre: "Classics", shelf: "Classics", progress: 0.64, file: "mrs-dalloway-woolf.epub" },
  { title: "Jane Eyre", author: "Charlotte Brontë", year: 1847, genre: "Romance", shelf: "The Brontës", file: "Jane Eyre - Bronte.epub" },
  { title: "Wuthering Heights", author: "Emily Brontë", year: 1847, genre: "Romance", shelf: "The Brontës", file: "wuthering_heights.epub" },
  { title: "Persuasion", author: "Jane Austen", year: 1817, genre: "Romance", shelf: "Currently Healing", file: "persuasion (for the yearning).epub" },
  { title: "The Awakening", author: "Kate Chopin", year: 1899, genre: "Fiction", shelf: "Currently Healing", file: "the_awakening_chopin.epub" },
  { title: "Herland", author: "Charlotte Perkins Gilman", year: 1915, genre: "Science Fiction", shelf: "Feminist Classics", file: "herland.pdf" },
  { title: "Middlemarch", author: "George Eliot", year: 1871, genre: "Classics", shelf: "Classics", file: "middlemarch (2).epub" },
  { title: "Frankenstein", author: "Mary Shelley", year: 1818, genre: "Science Fiction", shelf: "Classics", progress: 1, file: "frankenstein-1818-text.epub" },
  { title: "Poems", author: "Emily Dickinson", year: 1890, genre: "Poetry", shelf: "Poetry", progress: 0.5, file: "dickinson_poems_series1.epub" },
  { title: "Sonnets from the Portuguese", author: "Elizabeth Barrett Browning", year: 1850, genre: "Poetry", shelf: "Poetry", file: "sonnets-from-the-portuguese.epub" },
  { title: "The Prophet", author: "Kahlil Gibran", year: 1923, genre: "Philosophy", shelf: "Poetry", file: "the_prophet_ON_LOVE.epub" },
];

export function book(title: string): Book {
  const found = books.find((b) => b.title === title);
  if (!found) throw new Error(`No demo book called ${title}`);
  return found;
}

export function yearLabel(year: number): string {
  return year < 0 ? `${-year} BC` : String(year);
}

/** "Woolf", "Alcott": the surname the caption line uses. */
export function surname(author: string): string {
  const parts = author.split(/\s+/);
  return parts[parts.length - 1];
}

/** One piece of a page in the reading spread. */
export type PageBlock = { kind: "para"; text: string };

/** The opening of A Room of One's Own (1929, public domain), for the Fold's cover and reading spread. */
export const frontBook = {
  title: "A Room of One's Own",
  opening: "Chapter One",
  rightHead: "Chapter One",
  left: [
    {
      kind: "para",
      text: "But, you may say, we asked you to speak about women and fiction—what has that got to do with a room of one’s own? I will try to explain. When you asked me to speak about women and fiction I sat down on the banks of a river and began to wonder what the words meant. They might mean simply a few remarks about Fanny Burney; a few more about Jane Austen; a tribute to the Brontës and a sketch of Haworth Parsonage under snow; some witticisms if possible about Miss Mitford; a respectful allusion to George Eliot; a reference to Mrs Gaskell and one would have done. But at second sight the words seemed not so simple.",
    },
  ] as PageBlock[],
  right: [
    {
      kind: "para",
      text: "The title women and fiction might mean, and you may have meant it to mean, women and what they are like, or it might mean women and the fiction that they write; or it might mean women and the fiction that is written about them, or it might mean that somehow all three are inextricably mixed together and you want me to consider them in that light. But when I began to consider the subject in this last way, which seemed the most interesting, I soon saw that it had one fatal drawback. I should never be able to come to a conclusion. I should never be able to fulfil what is, I understand, the first duty of a lecturer to hand you after an hour’s discourse a nugget of pure truth to wrap up between the pages of your notebooks and keep on the mantelpiece for ever. All I could do was to offer you an opinion upon one minor point—a woman must have money and a room of her own if she is to write fiction; and that, as you will see, leaves the great problem of the true nature of woman and the true nature of fiction unsolved.",
    },
  ] as PageBlock[],
};
