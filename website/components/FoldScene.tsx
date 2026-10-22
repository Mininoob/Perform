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

    // Narrow screens stack the opening copy above the closed phone. The copy's height depends on
    // the screen, so the phone is placed and sized to fit the space left under it.
    const narrow = window.matchMedia("(max-width: 899px)");
    const hero = el.querySelector<HTMLElement>(`.${styles.hero}`);
    const stage = el.querySelector<HTMLElement>(`.${styles.stage}`);
    const device = el.querySelector<HTMLElement>(`.${styles.device}`);
    const place = () => {
      if (!narrow.matches || !hero || !stage || !device) {
        el.style.removeProperty("--hy");
        el.style.removeProperty("--hscale");
        return;
      }
      const gap = 24;
      const below = 64; // the scroll cue and the phone's shadow
      const top = hero.offsetTop + hero.offsetHeight + gap;
      const room = stage.clientHeight - top - below;
      const height = device.offsetHeight;
      const scale = Math.max(0.3, Math.min(0.72, room / height));
      // On the smallest screens the phone needs the space the scroll cue would take.
      el.dataset.tight = room / height < 0.3 ? "true" : "false";
      const padBottom = parseFloat(getComputedStyle(stage).paddingBottom) || 0;
      const center = (stage.clientHeight - padBottom) / 2;
      el.style.setProperty("--hy", `${top + (scale * height) / 2 - center}px`);
      el.style.setProperty("--hscale", String(1 - scale));
    };

    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    const onResize = () => {
      place();
      onScroll();
    };
    place();
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);
    reduce.addEventListener("change", onScroll);
    narrow.addEventListener("change", onResize);
    // The opening copy changes height once the web font arrives.
    document.fonts?.ready.then(onResize);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onResize);
      reduce.removeEventListener("change", onScroll);
      narrow.removeEventListener("change", onResize);
    };
  }, []);

  return (
    <section ref={scene} className={`${styles.scene} cloth-claret`} aria-labelledby="hero-title" data-ground="claret">
      <div className={styles.sticky}>
        <div className={styles.paperField} aria-hidden="true" />

        <div className={styles.hero}>
          <h1 id="hero-title" className="display">
            Brothers, it&rsquo;s time to be seen&nbsp;reading.
          </h1>
          <p className="lede">
            Perform is the free ebook reader for foldable phones, and for men who&rsquo;ve done the work. Fold it shut and
            the outside screen shows the cover of what you&rsquo;re reading: <em>A Room of One&rsquo;s Own</em>, bell
            hooks, <em>The Yellow Wallpaper</em>. People will notice. Not that that&rsquo;s why we do it. Open it and
            you&rsquo;re right back on your page.
          </p>
          <div className={styles.actions}>
            <a className="button button-primary" href={site.repo}>
              <GitHubIcon />
              Join us on GitHub
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
          <p className={styles.note}>Free and open source, like my heart. Reads EPUB and PDF. Android app, plus a companion app for Windows and Mac. Tote bag not included. Yet.</p>
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
          <span className={styles.captionTitle}>Book Mode.</span> Close the phone and its outside screen shows the cover
          of the book you&rsquo;re reading. Set it face up next to your iced matcha and your film camera. Wait for someone to ask. Say &ldquo;oh, this?
          It&rsquo;s Woolf.&rdquo; Act surprised.
        </p>
        <p className={`${styles.caption} ${styles.cap2}`}>
          <span className={styles.captionTitle}>Open it to read.</span> The big screen shows two pages side by side,
          right where you left off, in the font and colors you picked. Wired headphones in. Laufey first, then Mitski,
          then Phoebe Bridgers when it gets sad. Welcome, brother. You&rsquo;re reading now.
        </p>

        <div className={styles.cue} aria-hidden="true">
          <ArrowDownIcon />
          Scroll to open
        </div>
      </div>
    </section>
  );
}
