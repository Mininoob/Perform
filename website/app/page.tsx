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
  { x: 10, y: 34, title: "Folders", text: "Drag books between folders and the real files move with them. You can undo any move." },
  { x: 63.5, y: 13, title: "Auto-organize", text: "Sort a whole messy folder in one go. You see a preview first, and you can undo it after." },
  { x: 88, y: 66, title: "Book details", text: "Fix a title, author, series, year, genre, description or cover. Changes are saved into the file, and the original is kept." },
  { x: 10, y: 82, title: "Tidy-up checks", text: "Finds duplicate files, the same book saved twice, files that won’t open, and books missing an author, cover or genre." },
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
              Pick a page color, an accent color and a text size. Try it out right here. Every book opens where you left off.
            </CoverHead>
            <ThemeDemo />
            <p className={styles.runIn}>
              <em>Turn pages or scroll,</em> one page at a time or two side by side when the phone is open. <em>Flex Mode</em>{" "}
              lets you read with the phone half folded. <em>Right-to-left</em> books work too. <em>EPUB and PDF</em> both
              open, and a PDF page can be fit to the screen and locked in place.
            </p>
            <p className="imprint">
              <span>Perform</span>
              <span>Reading</span>
            </p>
          </div>
        </section>

        <section id="shelves" className={`${styles.section} cloth-bottle`} data-ground="bottle" aria-labelledby="shelves-title">
          <div className={styles.inner}>
            <CoverHead id="shelves-title" title="Folders become shelves.">
              However you&rsquo;ve organized books on your computer, that&rsquo;s how they show up on your phone. Books
              without cover art get a clean, simple cover, so even a messy downloads folder ends up looking tidy.
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
                ["Genres", "Perform works out each book’s genre from the book itself and from Open Library, and every genre gets its own shelf."],
                ["Continue reading", "Whatever you’re reading sits at the top, one tap from where you left off."],
                ["Downloads", "Books download when you open them. Or keep your whole library on the phone, up to a storage limit you choose."],
                ["Organize on the phone", "Make folders and move books around on your phone, and the files on your computer move to match."],
              ]}
            />
            <p className="imprint">
              <span>Perform</span>
              <span>Shelves</span>
            </p>
          </div>
        </section>

        <section id="library-app" className={`${styles.section} cloth-graphite`} data-ground="graphite" aria-labelledby="app-title">
          <div className={styles.inner}>
            <CoverHead id="app-title" title="And an app for your computer.">
              Perform Library keeps your book folder organized and sends your books to your phone. It stays out of the way in
              the tray on Windows or the menu bar on a Mac. Open it whenever you want to tidy up.
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
              <em>Open Library</em> fills in missing details. It only sends titles, authors and file names, and you can turn it
              off. <em>Deleting is safe.</em> Removed books go to the Recycle Bin or Trash, so you can get them back.
            </p>
            <p className="imprint">
              <span>Perform Library</span>
              <span>Windows and macOS</span>
            </p>
          </div>
        </section>

        <section id="private" className={`${styles.section} cloth-ochre`} data-ground="ochre" aria-labelledby="private-title">
          <div className={styles.inner}>
            <CoverHead id="private-title" title="No account, no cloud. Just your computer.">
              Your phone gets books straight from your own computer, over your home Wi-Fi, or through Tailscale when
              you&rsquo;re away. Nothing passes through a Perform server, because there isn&rsquo;t one.
            </CoverHead>

            <div className={styles.pairing}>
              <ol className={styles.steps}>
                <li>
                  <p className={styles.stepTitle}>Click &ldquo;Pair a phone&rdquo;</p>
                  <p>It&rsquo;s under Devices in Perform Library on your computer.</p>
                </li>
                <li>
                  <p className={styles.stepTitle}>Scan the code</p>
                  <p>Or type the six digits into your phone.</p>
                </li>
                <li>
                  <p className={styles.stepTitle}>Check that the codes match</p>
                  <p>Both screens show the same security code. After that, your phone only trusts that computer.</p>
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
                  "Pair your phone with both. A book that’s on both only shows up once. The two computers can also browse each other’s books, copy them across, or stay fully in sync.",
                ],
                ["What if I change something in two places?", "The newest change wins, on every device."],
                [
                  "Can anyone else get to my books?",
                  "Not from outside your network. The library only answers on private networks, and the connection is encrypted and locked to your computer’s own certificate.",
                ],
              ]}
            />
            <p className="imprint">
              <span>Perform</span>
              <span>
                <LockIcon className={styles.inlineIcon} />
                Stays on your devices
              </span>
            </p>
          </div>
        </section>

        <section id="get" className={`${styles.section} ${styles.get} cloth-claret`} data-ground="claret" aria-labelledby="get-title">
          <div className={styles.inner}>
            <CoverHead id="get-title" title="Start with the books you already have.">
              Perform is free and open source, under the Apache 2.0 license.
            </CoverHead>

            <div className={styles.platforms}>
              <div className={styles.platform}>
                <h3>For your phone</h3>
                <p>Needs Android 8.0 or newer. Book Mode is for foldables with an outside screen.</p>
                {release?.apk ? (
                  <a className="button button-primary" href={release.apk}>
                    <DownloadIcon />
                    Download the APK
                  </a>
                ) : (
                  <>
                    <p className={styles.pending}>The Android download comes with the first release. For now, you can build it from the source code.</p>
                    <a className="button button-primary" href={`${site.repo}#building`}>
                      Build from source
                    </a>
                  </>
                )}
              </div>
              <div className={styles.platform}>
                <h3>For your computer</h3>
                <p>Perform Library for Windows and Mac keeps your books and shares them with your phone.</p>
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
                <p>The phone app, the computer app and the sync code, plus instructions for building them.</p>
                <a className="button button-quiet" href={site.repo}>
                  <GitHubIcon />
                  Mininoob/Perform
                </a>
              </div>
            </div>

            <div className={styles.waitlist}>
              {waitlistOpen ? (
                <Waitlist />
              ) : (
                <p className={styles.watch}>
                  Want to know when the first release is out? <a href={site.repo}>Watch the project on GitHub</a>.
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
              Perform
            </span>
            <nav aria-label="Footer" className={styles.footLinks}>
              <a href={site.repo}>GitHub</a>
              <a href={site.releases}>Releases</a>
              <a href={`${site.repo}/blob/main/LICENSE`}>License</a>
            </nav>
          </div>
          <p className={styles.footNote}>
            The books shown here are public-domain classics with Perform&rsquo;s built-in covers. The file names and book
            counts are just examples. Set in Literata, under the SIL Open Font License.
          </p>
        </div>
      </footer>
    </>
  );
}
