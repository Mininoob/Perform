"use client";

import { useEffect, useRef } from "react";
import type { Release } from "@/lib/site";
import { site } from "@/lib/site";
import { CoverScreen } from "./CoverScreen";
import { ReadingPage } from "./ReadingPage";
import { ArrowDownIcon, DownloadIcon, GitHubIcon } from "./Icons";
import styles from "./FoldScene.module.css";

const clamp = (v: number) => Math.min(1, Math.max(0, v));
/** 0 before [a], 1 after [b], eased between. */
const span = (p: number, a: number, b: number) => {
  const t = clamp((p - a) / (b - a));
  return t * t * (3 - 2 * t);
};

/**
 * The opening of the page and its one authored motion: a closed Fold shows the book's
 * cover on its cover screen; scrolling swings the cover leaf open on its hinge into the
 * reading spread, while the claret field turns to the page.
 */
export function FoldScene({ release, waitlist }: { release: Release | null; waitlist: boolean }) {
  const scene = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = scene.current;
    if (!el) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");
    let frame = 0;

    const update = () => {
      frame = 0;
      const rect = el.getBoundingClientRect();
      const travel = el.offsetHeight - window.innerHeight;
      const p = travel > 0 ? clamp(-rect.top / travel) : 0;
      // Reduced motion: the phone is closed, then open, never swinging in between.
      const open = reduce.matches ? (p > 0.42 ? 1 : 0) : span(p, 0.24, 0.62);
      el.style.setProperty("--hero", String(1 - span(p, 0.02, 0.14)));
      el.style.setProperty("--open", String(open));
      el.style.setProperty("--to-paper", String(span(p, 0.46, 0.64)));
      el.style.setProperty("--cap1", String(span(p, 0.1, 0.17) * (1 - span(p, 0.3, 0.38))));
      el.style.setProperty("--cap2", String(span(p, 0.62, 0.7)));
      el.style.setProperty("--cue", String(1 - span(p, 0, 0.05)));
      el.dataset.open = open > 0.5 ? "true" : "false";
      // Once the opening copy has faded, its links leave the tab order and the hit area.
      el.dataset.hero = p > 0.13 ? "gone" : "shown";
      el.dataset.ground = span(p, 0.46, 0.64) > 0.5 ? "paper" : "claret";
    };

    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    reduce.addEventListener("change", onScroll);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      reduce.removeEventListener("change", onScroll);
    };
  }, []);

  return (
    <section ref={scene} className={`${styles.scene} cloth-claret`} aria-labelledby="hero-title" data-ground="claret">
      <div className={styles.sticky}>
        <div className={styles.paperField} aria-hidden="true" />

        <div className={styles.hero}>
          <h1 id="hero-title" className="display">
            Your books, held like&nbsp;books.
          </h1>
          <p className="lede">
            Preform is an ebook reader made for foldable phones. Closed, the cover screen shows the book you&rsquo;re
            reading. Open it, and you&rsquo;re on the page you left.
          </p>
          <div className={styles.actions}>
            <a className="button button-primary" href={site.repo}>
              <GitHubIcon />
              Get it on GitHub
            </a>
            {release?.apk ? (
              <a className="button button-quiet" href={release.apk}>
                <DownloadIcon />
                Download the APK
              </a>
            ) : waitlist ? (
              <a className="button button-quiet" href="#get">
                Get notified
              </a>
            ) : null}
          </div>
          <p className={styles.note}>Free and open source. EPUB and PDF. Android, with a companion app for Windows and macOS.</p>
        </div>

        <div className={styles.stage} aria-hidden="true">
          <div className={styles.floor} />
          <div className={styles.device}>
            <div className={`${styles.half} ${styles.right}`}>
              <div className={styles.glass}>
                <ReadingPage side="right" />
              </div>
            </div>
            <div className={styles.leaf}>
              <div className={`${styles.face} ${styles.front}`}>
                <div className={styles.glass}>
                  <ReadingPage side="left" />
                </div>
              </div>
              <div className={`${styles.face} ${styles.back}`}>
                <div className={`${styles.glass} ${styles.coverGlass}`}>
                  <CoverScreen />
                </div>
              </div>
            </div>
          </div>
        </div>

        <p className={`${styles.caption} ${styles.cap1}`}>
          <span className={styles.captionTitle}>Book Mode.</span> Folded shut, the phone wears the cover of the book
          you&rsquo;re in, the way a book on the table does.
        </p>
        <p className={`${styles.caption} ${styles.cap2}`}>
          <span className={styles.captionTitle}>Open it to read.</span> The inner screen becomes a two-page spread, at
          the exact place you stopped, set in the type and colours you chose.
        </p>

        <div className={styles.cue} aria-hidden="true">
          <ArrowDownIcon />
          Scroll to open
        </div>
      </div>
    </section>
  );
}
