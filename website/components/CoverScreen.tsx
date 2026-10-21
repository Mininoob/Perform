import { book, frontBook } from "@/lib/books";
import { TypeCover } from "./TypeCover";
import styles from "./Screens.module.css";

/** The cover screen in Book Mode: the book you're reading, and nothing else. */
export function CoverScreen() {
  return (
    <div className={styles.screen}>
    <div className={styles.coverScreen}>
      <div className={styles.statusBar} aria-hidden="true">
        <span>9:41</span>
        <span className={styles.signal}>
          <i />
          <i />
          <i />
        </span>
      </div>
      <div className={styles.coverWrap}>
        <TypeCover book={book(frontBook.title)} lift="none" className={styles.fullCover} />
      </div>
      <div className={styles.coverFoot} aria-hidden="true">
        <span>{frontBook.opening}</span>
        <span className={styles.coverProgress}>
          <i style={{ width: "1%" }} />
        </span>
      </div>
    </div>
    </div>
  );
}
