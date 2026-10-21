import { frontBook, type PlayBlock } from "@/lib/books";
import styles from "./Screens.module.css";

function Block({ block, first }: { block: PlayBlock; first: boolean }) {
  switch (block.kind) {
    case "verse":
      return (
        <div className={`${styles.verse} ${first ? styles.first : ""}`}>
          {block.lines.map((line) => (
            <span key={line}>{line}</span>
          ))}
        </div>
      );
    case "heading":
      return <p className={styles.sceneHead}>{block.text}</p>;
    case "direction":
      return <p className={styles.direction}>{block.text}</p>;
    case "speech":
      return (
        <p className={styles.speech}>
          <span className={styles.speaker}>{block.speaker}.</span> {block.text}
        </p>
      );
  }
}

/** One page of the reading spread on the open Fold's inner screen. */
export function ReadingPage({ side }: { side: "left" | "right" }) {
  const blocks = side === "left" ? frontBook.left : frontBook.right;
  return (
    <div className={styles.screen}>
    <div className={styles.page}>
      <div className={styles.runningHead}>
        <span>{side === "left" ? frontBook.title : frontBook.rightHead}</span>
        {side === "right" ? <span>9:41</span> : null}
      </div>
      <div className={styles.text}>
        {side === "left" ? <p className={styles.chapter}>{frontBook.opening}</p> : null}
        {blocks.map((b, i) => (
          <Block key={i} block={b} first={i === 0 && side === "left"} />
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
