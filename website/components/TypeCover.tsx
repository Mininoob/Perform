import { clothOf, type Book } from "@/lib/books";
import styles from "./TypeCover.module.css";

/**
 * Preform's typeset cover: the cloth ground, the author in tracked capitals at the head
 * over a hairline, the title in Literata italic set low, and the imprint at the foot.
 * Every measure scales with the cover's width, so a thumbnail and a full cover are one design.
 */
export function TypeCover({
  book,
  className,
  lift = "rest",
}: {
  book: Pick<Book, "title" | "author" | "genre">;
  className?: string;
  lift?: "none" | "rest" | "hand";
}) {
  const cloth = clothOf(book);
  // The title is sized so its longest word never breaks.
  const longest = Math.max(...book.title.split(/\s+/).map((w) => w.length));
  const titleSize = Math.min(16.3, 80 / (longest * 0.56));
  return (
    <div
      className={[styles.cover, styles[cloth], styles[lift], className].filter(Boolean).join(" ")}
      role="img"
      aria-label={`${book.title} by ${book.author}`}
    >
      <div className={styles.inner}>
      <div className={styles.head}>
        <span className={styles.author}>{book.author}</span>
        <span className={styles.rule} />
      </div>
      <span className={styles.title} style={{ fontSize: `${titleSize}cqw` }}>
        {book.title}
      </span>
      <span className={styles.imprint}>Preform</span>
      </div>
    </div>
  );
}
