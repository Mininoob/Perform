import { openingPages } from "@/lib/books";
import styles from "./Screens.module.css";

/** One page of the reading spread on the open Fold's inner screen. */
export function ReadingPage({ side }: { side: "left" | "right" }) {
  const paragraphs = side === "left" ? openingPages.left : openingPages.right;
  return (
    <div className={styles.screen}>
    <div className={styles.page}>
      <div className={styles.runningHead}>
        <span>{side === "left" ? "Pride and Prejudice" : openingPages.chapter}</span>
        {side === "right" ? <span>9:41</span> : null}
      </div>
      <div className={styles.text}>
        {side === "left" ? <p className={styles.chapter}>{openingPages.chapter}</p> : null}
        {paragraphs.map((p, i) => (
          <p key={i} className={i === 0 && side === "left" ? styles.first : undefined}>
            {p}
          </p>
        ))}
      </div>
      <div className={styles.folio}>
        <span>{side === "left" ? "4" : "5"}</span>
        {side === "right" ? <span className={styles.percent}>1%</span> : null}
      </div>
    </div>
    </div>
  );
}
