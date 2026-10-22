import { book, books, yearLabel } from "@/lib/books";
import { TypeCover } from "./TypeCover";
import { Mark } from "./Mark";
import { ComputerIcon, FolderIcon, PhoneIcon, SearchIcon } from "./Icons";
import styles from "./DesktopWindow.module.css";

const folders = ["Classics", "Currently Healing", "Feminist Classics", "Poetry", "The Brontës"];

/** Perform Library's window: sidebar, cover grid and the details panel, on the Ink base. */
export function DesktopWindow() {
  const selected = book("Middlemarch");
  const grid = [...books].sort((a, b) => a.title.localeCompare(b.title)).slice(0, 12);
  return (
    <div className={styles.window} role="img" aria-label="Perform Library on a computer: folders in the sidebar, a grid of covers, and one book's details">
      <div className={styles.titlebar}>
        <span className={styles.brand}>
          <Mark size={16} />
          Perform Library
        </span>
        <span className={styles.caption}>
          <i />
          <i />
          <i />
        </span>
      </div>
      <div className={styles.body}>
        <aside className={styles.sidebar}>
          <p className={styles.group}>Library</p>
          <p className={styles.item} data-on="true">
            All books <span>{books.length}</span>
          </p>
          <p className={styles.item}>Recently added</p>
          <p className={styles.group}>Folders</p>
          {folders.map((f) => (
            <p key={f} className={styles.item}>
              <FolderIcon />
              {f}
              <span>{books.filter((b) => b.shelf === f).length}</span>
            </p>
          ))}
          <p className={styles.group}>Other libraries</p>
          <p className={styles.item}>
            <ComputerIcon />
            Studio Mac <i className={styles.online} /> <span>214</span>
          </p>
          <p className={styles.group}>Checks</p>
          <p className={styles.item}>No genre <span>2</span></p>
          <div className={styles.sideFoot}>
            <p className={styles.item}>
              <PhoneIcon />
              Devices <span>2</span>
            </p>
          </div>
        </aside>

        <div className={styles.main}>
          <div className={styles.toolbar}>
            <span className={styles.pageTitle}>All books</span>
            <span className={styles.muted}>{books.length} books</span>
          </div>
          <div className={styles.tools}>
            <span className={styles.search}>
              <SearchIcon />
              Search title, author, file
            </span>
            <span>Title</span>
            <span>Organize…</span>
            <span className={styles.add}>Add books</span>
          </div>
          <div className={styles.grid}>
            {grid.map((b) => (
              <div key={b.title} className={styles.card} data-on={b.title === selected.title}>
                <TypeCover book={b} />
                <span className={styles.cardTitle}>{b.title}</span>
                <span className={styles.cardMeta}>
                  {b.author} · {yearLabel(b.year)}
                </span>
              </div>
            ))}
          </div>
        </div>

        <aside className={styles.details}>
          <div className={styles.bigCover}>
            <TypeCover book={selected} lift="hand" />
          </div>
          <p className={styles.detailTitle}>{selected.title}</p>
          <p className={styles.detailAuthor}>{selected.author}</p>
          <p className={styles.muted}>First published {selected.year}</p>
          <p className={styles.genres}>Classics · Fiction</p>
          <p className={styles.description}>A Study of Provincial Life. Published under a man&rsquo;s name by Mary Ann Evans, which we should all sit with.</p>
          <dl className={styles.facts}>
            <dt>Folder</dt>
            <dd>Classics</dd>
            <dt>File</dt>
            <dd>middlemarch.epub</dd>
            <dt>Format</dt>
            <dd>EPUB</dd>
          </dl>
          <div className={styles.detailActions}>
            <span className={styles.edit}>Edit</span>
            <span className={styles.reveal}>Show in Explorer</span>
          </div>
        </aside>
      </div>
    </div>
  );
}
