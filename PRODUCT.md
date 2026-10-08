# Product

## Platform

adaptive

## Users

Readers of their own ebooks (EPUB and PDF) who keep a personal library on a computer and read on their phone, especially on a foldable. Public product: meant to be installed by other readers (Android app now, iOS later; a Windows and macOS library app alongside).

Two jobs, two devices:

- **Reading** (phone app): open a book and read, in pages or scroll, one or two pages, at home or away; on a foldable, hold the phone like a book.
- **Keeping the library** (PC/Mac app, "Preform Library"): organize the book files into folders, fix titles, authors, covers, genres and series, and share the library privately with the reader's phones.

## Product Purpose

Make a personal ebook collection feel like a real library: one place on the computer where the books live and are kept in order, and a reader on the phone that opens any of them, anywhere, at the page left off. Success is a reader who stops thinking about files: books are organized, look right (covers, titles, shelves), and open instantly on the phone.

## Positioning

- **Book Mode on foldables:** the outer screen shows the book's cover; unfolding the phone opens the book. Nothing else treats a foldable as a book.
- **A private cloud library without a cloud:** the reader's own computer serves the library to their phones over the home network or Tailscale, paired by QR code and pinned to that computer's certificate. No account, no third-party server.
- **Folders become shelves:** how the library is organized on the computer is how it appears on the phone.

## Operating Context

- Phone: Android (developed on Galaxy Z Fold 8 Ultra and Z Flip 5); reading in hand, often at night; cover screen, inner screen, Flex Mode, two-page spreads.
- Computer: Windows and macOS. The library app lives in the tray / menu bar and runs in the background; its window is opened to organize, pair a phone, or change settings.
- Library folder on disk (e.g. `E:\Books`), with sub-folders as shelves. Books may be edited by the library app (information written into the files, originals backed up).
- Book information can come from Open Library (titles, authors, genres, covers, descriptions, years); only titles, authors and file names are sent.

## Capabilities and Constraints

- Phone app: Kotlin, Jetpack Compose, Readium 3.1.2 (EPUB and PDF). Reading modes: pages, scroll, two-page spread, Flex Mode, right-to-left books. Per-book and global reading settings (theme, font, size, spacing, margins). Library with shelves (folders), genres, all books, continue reading, search, sort. Private library pairing, catalog sync, download on open, storage limit.
- Library app: Kotlin, Compose Multiplatform Desktop, embedded HTTPS server (Ktor). Organizer: folder tree, drag-and-drop moves, edit details, auto-organize with preview and undo, tidy-up checks, import, Recycle Bin/Trash. Phones page (pairing, devices, addresses), settings.
- iOS app: planned, not started.
- Terminology: "library", "shelf" (a folder), "Book Mode", "cover screen", "Private library", "Preform Library" (the PC/Mac app).
- Undecided: pricing, distribution channels beyond Play Store and a signed macOS download, a public website.

## Brand Commitments

- Name: **Preform** (the reader) and **Preform Library** (the computer app).
- **Themes stay a choice:** three light and three dark neutral bases plus an accent color the reader picks; light and dark can switch by time of day; no orange accents.
- **The book mark:** a closed book seen from the front, used as the app icon, tray / menu-bar icon and in-app mark.

## Evidence on Hand

- Working apps: `app/` (Android reader), `desktop/` (library app), `sync-protocol/`.
- Real book covers and metadata come from the reader's own files and Open Library; there are no marketing assets, testimonials, users, or press. Do not fabricate them.

## Product Principles

1. **The book is the hero.** Covers and text lead; the interface recedes while reading and stays quiet while browsing.
2. **One library, two places.** The computer and the phone are the same library seen two ways; names, shelves, themes and marks match across them.
3. **Private by construction.** The reader's books never leave their own devices, apart from minimal lookups they can switch off.
4. **Nothing is lost.** Every organizing change can be undone; edited files keep their originals; deletes go to the bin.
5. **Native where it matters.** Each platform keeps its own gestures, windows and conventions; the identity lives in type, color, covers and the mark.

## Accessibility & Inclusion

Reading must stay comfortable for long sessions: adjustable text size, font and spacing, light and dark themes, right-to-left books. No product-specific standard beyond platform accessibility has been set.
