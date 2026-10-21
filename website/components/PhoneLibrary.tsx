import { book, books, surname, yearLabel } from "@/lib/books";
import { TypeCover } from "./TypeCover";
import { GearIcon, PlusIcon, SearchIcon } from "./Icons";
import styles from "./PhoneLibrary.module.css";

const shelves = ["Classics", "Sea Stories", "Essays"];

/** The phone's library screen as the app draws it: the book in hand, then shelves from folders. */
export function PhoneLibrary() {
  const inHand = book("Pride and Prejudice");
  return (
    <div className={styles.phone} role="img" aria-label="Perform's library on a phone: the book in hand, then shelves made from folders">
      <div className={styles.screen}>
        <div className={styles.status}>
          <span>9:41</span>
        </div>
        <div className={styles.bar}>
          <SearchIcon />
          <PlusIcon />
          <GearIcon />
        </div>
        <p className={styles.title}>Library</p>

        <div className={styles.inHand}>
          <div className={styles.handCover}>
            <TypeCover book={inHand} lift="hand" />
          </div>
          <div className={styles.handText}>
            <p className={styles.handTitle}>{inHand.title}</p>
            <p className={styles.handAuthor}>{inHand.author}</p>
            <span className={styles.rule}>
              <i style={{ width: `${(inHand.progress ?? 0) * 100}%` }} />
            </span>
            <p className={styles.handState}>38% · Chapter 22</p>
            <span className={styles.continue}>Continue</span>
          </div>
        </div>

        <div className={styles.tabs}>
          <span data-on="true">Shelves</span>
          <span>Genres</span>
          <span>All books</span>
        </div>

        {shelves.map((name) => {
          const onShelf = books.filter((b) => b.shelf === name);
          return (
            <div key={name} className={styles.shelf}>
              <div className={styles.shelfHead}>
                <span className={styles.shelfName}>{name}</span>
                <span className={styles.count}>{onShelf.length}</span>
                <span className={styles.seeAll}>See all</span>
              </div>
              <div className={styles.row}>
                {onShelf.slice(0, 4).map((b) => (
                  <div key={b.title} className={styles.item}>
                    <TypeCover book={b} />
                    <span className={styles.caption}>{b.title}</span>
                    <span className={styles.furniture}>
                      {surname(b.author)} · {yearLabel(b.year)}
                    </span>
                    {b.progress ? (
                      <span className={styles.rule}>
                        <i style={{ width: `${b.progress * 100}%` }} />
                      </span>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
