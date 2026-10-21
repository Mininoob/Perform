# Perform

Perform is an ebook reader for Android that treats a foldable phone like a book, plus Perform Library, a small Windows and macOS app that keeps your book files in order and shares them with your phone. There's no account and no cloud service. Your computer is the library, and your phone talks to it directly over your home network (or Tailscale when you're out).

It reads EPUB and PDF.

## What it does

On the phone:

- Pages or scroll, one page or a two-page spread, right-to-left books, and Flex Mode on foldables.
- Book Mode: with the phone closed, the cover screen shows the book's cover. Open the phone and you're on the page you left.
- Theme, font, size, spacing and margins, set for all books or one at a time. Six neutral bases (three light, three dark) and an accent colour you pick, with an optional switch between light and dark by time of day.
- A library that shows your folders as shelves, with Genres and All books views, search and sorting.
- Pairing with one or more computers by QR code. Books download when you open them, or you can keep every book from a given computer on the phone, within a storage limit you set.
- Making folders and moving books around on the phone. The computer moves the real files on the next sync.

In Perform Library on the computer:

- It sits in the tray (Windows) or the menu bar (macOS) and serves your books folder to your phones.
- A full organizer window: folders, drag and drop, editing a book's title, author, series, year, genres, description and cover, auto-organize with a preview and undo, tidy-up checks (duplicates, missing authors or covers and so on), and the Recycle Bin or Trash instead of deleting.
- Optional lookups on Open Library to fill in missing titles, authors, genres, covers, descriptions and years. Only titles, authors and file names are sent. You can turn this off.
- Writing that information back into the book files, keeping the original of every file it changes.
- Seeing other computers running Perform Library: browse their shelves, see what they have that you don't, copy books over, or mirror two libraries so they stay the same.

## How the pieces talk

Each computer runs a small HTTPS server on port 47474 that only answers on private networks. Pairing a phone means scanning a QR code (or typing a 6-digit code) that carries the computer's certificate fingerprint, so the phone only ever trusts that one computer. Both sides show a short security code you can compare. A book's identity is the SHA-256 of its file, so the same book is recognised everywhere, even after its file is moved or renamed.

Folder moves and edits carry a timestamp and the device that made them. When two devices disagree, the most recent change wins on both. Removals always go to the Recycle Bin or Trash, and a phone never deletes anything on a computer.

## Repository layout

- `app/` is the Android reader (Kotlin, Jetpack Compose, Readium 3, Room).
- `desktop/` is Perform Library (Kotlin, Compose Multiplatform, Ktor, SQLite).
- `sync-protocol/` holds the data types and rules both apps share.
- `design/` has the bundled Literata font files and their licence.
- `PRODUCT.md` and `DESIGN.md` describe who it's for and how it should look.

## Building

You need JDK 17 or newer. The phone app also needs the Android SDK (compile SDK 36, runs on Android 8.0 / API 26 and up).

Build and install the phone app:

```bash
./gradlew :app:installDebug
```

Run Perform Library from source without touching the Android build:

```bash
./gradlew :desktop:run -PdesktopOnly
```

Package it for the current platform (an installer on Windows, a .dmg on macOS):

```bash
./gradlew :desktop:packageDistributionForCurrentOS -PdesktopOnly
```

On a Mac, `desktop/macos/build.sh` runs the tests and builds a signed, notarized .dmg. The setup it needs (a Developer ID certificate and notarization credentials in your keychain) is described at the top of the script. Use `desktop/macos/build.sh --unsigned` for a quick local build.

Run the tests:

```bash
./gradlew :sync-protocol:test :desktop:test -PdesktopOnly
./gradlew :app:testDebugUnitTest
```

## Trying it without your real books

Perform Library reads two environment variables, which keep a test run away from your normal settings and library:

```bash
PERFORM_DATA_DIR=/tmp/perform-test PERFORM_LIBRARY=/path/to/some/test/books ./gradlew :desktop:run -PdesktopOnly
```

`PERFORM_LIBRARY` only applies the first time a data folder is created. After that the library folder is whatever the app's settings say.

## Licence

Perform is released under the Apache License 2.0. See [LICENSE](LICENSE).

The Literata typeface is copyright The Literata Project Authors and is licensed under the SIL Open Font License 1.1 (see `design/fonts/OFL.txt`). Readium, Ktor, Compose and the other libraries keep their own licences.
