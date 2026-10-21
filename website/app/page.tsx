import { books } from "@/lib/books";
import { latestRelease, site, waitlistOpen } from "@/lib/site";
import { DesktopWindow } from "@/components/DesktopWindow";
import { FoldScene } from "@/components/FoldScene";
import { ArrowRightIcon, ComputerIcon, DownloadIcon, GitHubIcon, LockIcon, PhoneIcon } from "@/components/Icons";
import { Mark } from "@/components/Mark";
import { Nav } from "@/components/Nav";
import { PhoneLibrary } from "@/components/PhoneLibrary";
import { ThemeDemo } from "@/components/ThemeDemo";
import { Waitlist } from "@/components/Waitlist";
import styles from "./page.module.css";

export const revalidate = 3600;

/** A section's opening, set like a cover: title low on its field, a rule, then the line beneath. */
function CoverHead({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <div className={styles.head}>
      <h2 id={id} className="display">
        {title}
      </h2>
      <span className={styles.headRule} aria-hidden="true" />
      <p className="lede">{children}</p>
    </div>
  );
}

/** Shelves: a ruled two-column list, term on the left. */
function RuledList({ items }: { items: [string, string][] }) {
  return (
    <dl className={styles.ruled}>
      {items.map(([term, text]) => (
        <div key={term}>
          <dt>{term}</dt>
          <dd>{text}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Privacy: the questions people ask, answered in a ruled table. */
function Answers({ items }: { items: [string, string][] }) {
  return (
    <dl className={styles.answers}>
      {items.map(([q, a]) => (
        <div key={q}>
          <dt>{q}</dt>
          <dd>{a}</dd>
        </div>
      ))}
    </dl>
  );
}

/** The library app: numbered pins on the window, keyed to a legend below it. */
const callouts: { x: number; y: number; title: string; text: string }[] = [
  { x: 10, y: 34, title: "Folders and moves", text: "Drag books between folders. The files move on disk, and every move can be undone." },
  { x: 63.5, y: 13, title: "Auto-organize", text: "File a whole folder into a tidy structure, with a preview first and undo after." },
  { x: 88, y: 66, title: "Book details", text: "Fix titles, authors, series, years, genres, descriptions and covers, written into the file with the original kept." },
  { x: 10, y: 82, title: "Tidy-up checks", text: "Identical copies, the same book twice, files that won’t open, and books missing an author, cover or genre." },
];

const downloads = books.slice(0, 9).map((b) => b.file);

export default async function Home() {
  const release = await latestRelease();
  return (
    <>
      <Nav />
      <main>
        <FoldScene release={release} waitlist={waitlistOpen} />

        <section id="reading" className={`${styles.section} cloth-navy`} data-ground="navy" aria-labelledby="reading-title">
          <div className={styles.inner}>
            <CoverHead id="reading-title" title="Read the way you like to read.">
              Pick a page colour, an accent and a size, and every book opens where you stopped. Try it here.
            </CoverHead>
            <ThemeDemo />
            <p className={styles.runIn}>
              <em>Pages or scroll,</em> as a single page or a two-page spread when the phone is open. <em>Flex Mode</em> for
              reading with the phone half open. <em>Right to left</em> for books written that way. <em>EPUB and PDF,</em> with
              whole-page fit and a page lock for PDFs.
            </p>
            <p className="imprint">
              <span>Preform</span>
              <span>The reading</span>
            </p>
          </div>
        </section>

        <section id="shelves" className={`${styles.section} cloth-bottle`} data-ground="bottle" aria-labelledby="shelves-title">
          <div className={styles.inner}>
            <CoverHead id="shelves-title" title="Folders become shelves.">
              However you keep your books on the computer is how they line up on the phone. A book without cover art gets a
              typeset cover in its genre&rsquo;s cloth, so a messy folder reads like an edited list.
            </CoverHead>

            <div className={styles.beforeAfter}>
              <div className={styles.downloads} aria-label="A downloads folder of ebook files with messy names">
                <p className={styles.panelTitle}>
                  <span>Downloads</span>
                  <span>{downloads.length} files</span>
                </p>
                <ul>
                  {downloads.map((f) => (
                    <li key={f}>
                      <span className={styles.fileGlyph} aria-hidden="true" />
                      {f}
                    </li>
                  ))}
                </ul>
              </div>
              <div className={styles.becomes} aria-hidden="true">
                <ArrowRightIcon />
              </div>
              <PhoneLibrary />
            </div>

            <RuledList
              items={[
                ["Genres", "Genres come from the books and from Open Library, and each gets a shelf of its own."],
                ["Continue reading", "The book you’re in sits at the top, one tap from where you stopped."],
                ["Downloads", "Books download when you open them, or keep a whole library on the phone, within a limit you set."],
                ["Organize on the phone", "Make folders and move books on the phone. The computer moves the real files."],
              ]}
            />
            <p className="imprint">
              <span>Preform</span>
              <span>Shelves</span>
            </p>
          </div>
        </section>

        <section id="library-app" className={`${styles.section} cloth-graphite`} data-ground="graphite" aria-labelledby="app-title">
          <div className={styles.inner}>
            <CoverHead id="app-title" title="A library app for the computer, too.">
              Preform Library keeps your books folder in order and shares it with your phone. It sits in the tray on Windows or
              the menu bar on a Mac; open its window when the books need tidying.
            </CoverHead>
            <div className={styles.windowFrame}>
              <DesktopWindow />
              {callouts.map((c, i) => (
                <span key={c.title} className={styles.pin} style={{ left: `${c.x}%`, top: `${c.y}%` }} aria-hidden="true">
                  {i + 1}
                </span>
              ))}
            </div>
            <ol className={styles.legend}>
              {callouts.map((c) => (
                <li key={c.title}>
                  <p className={styles.legendTitle}>{c.title}</p>
                  <p>{c.text}</p>
                </li>
              ))}
            </ol>
            <p className={styles.runIn}>
              <em>Open Library</em> fills in what a book is missing; only titles, authors and file names are sent, and you can
              turn it off. <em>Nothing is lost:</em> removed books go to the Recycle Bin or Trash.
            </p>
            <p className="imprint">
              <span>Preform Library</span>
              <span>Windows and macOS</span>
            </p>
          </div>
        </section>

        <section id="private" className={`${styles.section} cloth-ochre`} data-ground="ochre" aria-labelledby="private-title">
          <div className={styles.inner}>
            <CoverHead id="private-title" title="No account. No cloud. Your computer.">
              Your computer serves your library straight to your phone, on your home Wi-Fi or over Tailscale when you&rsquo;re
              out. There&rsquo;s no Preform server in between.
            </CoverHead>

            <div className={styles.pairing}>
              <ol className={styles.steps}>
                <li>
                  <p className={styles.stepTitle}>Open Pair a phone</p>
                  <p>In Preform Library on the computer, under Devices.</p>
                </li>
                <li>
                  <p className={styles.stepTitle}>Scan the code</p>
                  <p>Or type its six digits on the phone.</p>
                </li>
                <li>
                  <p className={styles.stepTitle}>Check the match</p>
                  <p>Both screens show the same security code. From then on, the phone trusts only that computer.</p>
                </li>
              </ol>

              <div className={styles.pairPanel} aria-label="An example pairing code and security check">
                <p className={styles.panelTitle}>
                  <span>Pair a phone</span>
                  <span>Example</span>
                </p>
                <p className={styles.code}>418 207</p>
                <p className={styles.checkLabel}>Security check</p>
                <p className={styles.check}>3F9A 7C21</p>
                <div className={styles.devices} aria-hidden="true">
                  <span>
                    <ComputerIcon />
                    PC
                  </span>
                  <i />
                  <span>
                    <PhoneIcon />
                    Phone
                  </span>
                  <i />
                  <span>
                    <ComputerIcon />
                    Mac
                  </span>
                </div>
              </div>
            </div>

            <Answers
              items={[
                [
                  "What if I have a PC and a Mac?",
                  "Pair the phone with both. A book on both shows up once, and the two computers can browse each other, copy books over, or mirror each other.",
                ],
                ["What if two devices disagree?", "Moves and edits carry a time and a device. The most recent change wins everywhere."],
                [
                  "Who else can reach it?",
                  "No one outside your network. The library only answers on private networks, over HTTPS pinned to its own certificate.",
                ],
              ]}
            />
            <p className="imprint">
              <span>Preform</span>
              <span>
                <LockIcon className={styles.inlineIcon} />
                Private by design
              </span>
            </p>
          </div>
        </section>

        <section id="get" className={`${styles.section} ${styles.get} cloth-claret`} data-ground="claret" aria-labelledby="get-title">
          <div className={styles.inner}>
            <CoverHead id="get-title" title="Start with the books you already have.">
              Preform is free and open source under the Apache License 2.0.
            </CoverHead>

            <div className={styles.platforms}>
              <div className={styles.platform}>
                <h3>For your phone</h3>
                <p>Android 8.0 or newer. Book Mode is made for foldables with a cover screen.</p>
                {release?.apk ? (
                  <a className="button button-primary" href={release.apk}>
                    <DownloadIcon />
                    Download the APK
                  </a>
                ) : (
                  <>
                    <p className={styles.pending}>The Android download arrives with the first release. Until then, build it from source.</p>
                    <a className="button button-primary" href={`${site.repo}#building`}>
                      Build from source
                    </a>
                  </>
                )}
              </div>
              <div className={styles.platform}>
                <h3>For your computer</h3>
                <p>Preform Library for Windows and macOS keeps the library and shares it with your phones.</p>
                <div className={styles.platformActions}>
                  {release?.windows ? (
                    <a className="button button-quiet" href={release.windows}>
                      <DownloadIcon />
                      Windows
                    </a>
                  ) : null}
                  {release?.mac ? (
                    <a className="button button-quiet" href={release.mac}>
                      <DownloadIcon />
                      macOS
                    </a>
                  ) : null}
                  {!release?.windows && !release?.mac ? (
                    <a className="button button-quiet" href={`${site.repo}#building`}>
                      Build from source
                    </a>
                  ) : null}
                </div>
              </div>
              <div className={styles.platform}>
                <h3>The source</h3>
                <p>The reader, the library app and the sync protocol, with build instructions.</p>
                <a className="button button-quiet" href={site.repo}>
                  <GitHubIcon />
                  Mininoob/Preform
                </a>
              </div>
            </div>

            <div className={styles.waitlist}>
              {waitlistOpen ? (
                <Waitlist />
              ) : (
                <p className={styles.watch}>
                  To hear about the first release, <a href={site.repo}>watch the repository on GitHub</a>.
                </p>
              )}
            </div>
          </div>
        </section>
      </main>

      <footer className={`${styles.footer} cloth-claret`}>
        <div className={styles.inner}>
          <div className={styles.footRow}>
            <span className={styles.footBrand}>
              <Mark size={22} />
              Preform
            </span>
            <nav aria-label="Footer" className={styles.footLinks}>
              <a href={site.repo}>GitHub</a>
              <a href={site.releases}>Releases</a>
              <a href={`${site.repo}/blob/main/LICENSE`}>License</a>
            </nav>
          </div>
          <p className={styles.footNote}>
            The books on this page are public-domain classics shown in Preform&rsquo;s own typeset covers; the library counts and
            file names are examples. Set in Literata, under the SIL Open Font License.
          </p>
        </div>
      </footer>
    </>
  );
}
