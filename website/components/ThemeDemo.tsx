"use client";

import { useState } from "react";
import { openingPages } from "@/lib/books";
import styles from "./ThemeDemo.module.css";

const bases = [
  { id: "mono", name: "Mono", bg: "#FFFFFF", text: "#000000", muted: "#5E5E5E", dark: false },
  { id: "paper", name: "Paper", bg: "#F7F5F0", text: "#1D1F24", muted: "#6A6E76", dark: false },
  { id: "parchment", name: "Parchment", bg: "#F3E9D2", text: "#3B2F25", muted: "#7A6650", dark: false },
  { id: "noir", name: "Noir", bg: "#000000", text: "#FFFFFF", muted: "#8C8C8C", dark: true },
  { id: "ink", name: "Ink", bg: "#131416", text: "#E4E2DD", muted: "#93979E", dark: true },
  { id: "slate", name: "Slate", bg: "#1A1E24", text: "#E1E5EA", muted: "#8B949E", dark: true },
] as const;

const accents = [
  { id: "mono", name: "Mono", light: "#000000", dark: "#FFFFFF" },
  { id: "red", name: "Red", light: "#C62F3A", dark: "#EF6B73" },
  { id: "pink", name: "Pink", light: "#B83C72", dark: "#F28AB4" },
  { id: "yellow", name: "Yellow", light: "#8F7100", dark: "#F2D263" },
  { id: "green", name: "Green", light: "#2F7D3A", dark: "#8FD18A" },
  { id: "teal", name: "Teal", light: "#12786F", dark: "#5FD0C8" },
  { id: "blue", name: "Blue", light: "#2457C5", dark: "#8AB4F8" },
  { id: "indigo", name: "Indigo", light: "#4B4FC4", dark: "#A3A6FF" },
  { id: "violet", name: "Violet", light: "#7A3FC0", dark: "#D29BF0" },
] as const;

const sizes = [
  { id: "small", name: "Smaller", scale: 0.88 },
  { id: "medium", name: "Default", scale: 1 },
  { id: "large", name: "Larger", scale: 1.16 },
] as const;

/** The reading settings, live: six bases, nine accents, three sizes, on a real page. */
export function ThemeDemo() {
  const [base, setBase] = useState<(typeof bases)[number]>(bases[1]);
  const [accent, setAccent] = useState<(typeof accents)[number]>(accents[6]);
  const [size, setSize] = useState<(typeof sizes)[number]>(sizes[1]);
  const tint = base.dark ? accent.dark : accent.light;

  return (
    <div className={styles.demo}>
      <div className={styles.controls}>
        <fieldset className={styles.group}>
          <legend>Page</legend>
          <div className={styles.bases}>
            {bases.map((b) => (
              <label key={b.id} className={styles.base} data-checked={b.id === base.id}>
                <input
                  type="radio"
                  name="base"
                  value={b.id}
                  checked={b.id === base.id}
                  onChange={() => setBase(b)}
                  className="visually-hidden"
                />
                <span className={styles.baseSwatch} style={{ background: b.bg, color: b.text }}>
                  Aa
                </span>
                <span className={styles.baseName}>{b.name}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className={styles.group}>
          <legend>Accent</legend>
          <div className={styles.accents}>
            {accents.map((a) => (
              <label key={a.id} className={styles.accent} data-checked={a.id === accent.id} title={a.name}>
                <input
                  type="radio"
                  name="accent"
                  value={a.id}
                  checked={a.id === accent.id}
                  onChange={() => setAccent(a)}
                  className="visually-hidden"
                />
                <span className={styles.dot} style={{ background: a.light }} />
                <span className="visually-hidden">{a.name}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className={styles.group}>
          <legend>Text size</legend>
          <div className={styles.sizes}>
            {sizes.map((s) => (
              <label key={s.id} className={styles.size} data-checked={s.id === size.id}>
                <input
                  type="radio"
                  name="size"
                  value={s.id}
                  checked={s.id === size.id}
                  onChange={() => setSize(s)}
                  className="visually-hidden"
                />
                <span style={{ fontSize: `${0.75 + sizes.indexOf(s) * 0.18}rem` }}>A</span>
                <span className="visually-hidden">{s.name}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <p className={styles.hint}>Set it once for every book, or differently for one book. Light and dark can switch with the time of day.</p>
      </div>

      <div className={styles.phone} aria-label={`A page in ${base.name} with the ${accent.name} accent`} role="img">
        <div
          className={styles.page}
          style={
            {
              background: base.bg,
              color: base.text,
              "--muted": base.muted,
              "--tint": tint,
              "--scale": size.scale,
            } as React.CSSProperties
          }
        >
          <div className={styles.head}>
            <span>Pride and Prejudice</span>
            <span>9:41</span>
          </div>
          <p className={styles.chapter}>{openingPages.chapter}</p>
          <div className={styles.text}>
            {openingPages.left.map((p, i) => (
              <p key={i}>{p}</p>
            ))}
            {openingPages.right.slice(0, 3).map((p, i) => (
              <p key={`r${i}`}>{p}</p>
            ))}
          </div>
          <div className={styles.foot}>
            <span className={styles.rule}>
              <i />
            </span>
            <span className={styles.percent}>38%</span>
          </div>
        </div>
      </div>
    </div>
  );
}
