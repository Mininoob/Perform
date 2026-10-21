"use client";

import { useEffect, useState } from "react";
import { site } from "@/lib/site";
import { Mark } from "./Mark";
import { GitHubIcon } from "./Icons";
import styles from "./Nav.module.css";

const links = [
  { href: "#reading", label: "Reading" },
  { href: "#shelves", label: "Shelves" },
  { href: "#library-app", label: "Library app" },
  { href: "#private", label: "Privacy" },
];

/** The nav takes the cloth of whatever section is under it. */
export function Nav() {
  const [ground, setGround] = useState("claret");
  const [solid, setSolid] = useState(false);

  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      setSolid(window.scrollY > 8);
      const sections = document.querySelectorAll<HTMLElement>("[data-ground]");
      for (const section of sections) {
        const rect = section.getBoundingClientRect();
        if (rect.top <= 36 && rect.bottom > 36) {
          setGround(section.dataset.ground ?? "claret");
          break;
        }
      }
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    // The opening scene changes its own ground (claret to paper) as it opens.
    const watcher = new MutationObserver(onScroll);
    document.querySelectorAll("[data-ground]").forEach((el) => watcher.observe(el, { attributeFilter: ["data-ground"] }));
    return () => {
      watcher.disconnect();
      cancelAnimationFrame(frame);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  return (
    <header className={`${styles.nav} cloth-${ground}`} data-solid={solid}>
      <a className={styles.brand} href="#top" aria-label="Perform, back to the top">
        <Mark size={26} />
        <span>Perform</span>
      </a>
      <nav aria-label="Sections" className={styles.links}>
        {links.map((l) => (
          <a key={l.href} href={l.href}>
            {l.label}
          </a>
        ))}
      </nav>
      <a className={`button button-quiet ${styles.cta}`} href={site.repo}>
        <GitHubIcon />
        GitHub
      </a>
    </header>
  );
}
