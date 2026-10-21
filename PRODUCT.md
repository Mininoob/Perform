# Product

## Platform

Android phones (foldables especially), with a companion app for Windows and macOS. iOS is planned but not started.

## Who it's for

People who own their ebooks (EPUB and PDF), keep them on a computer, and read on their phone. It's meant to be installed by other readers, not just me.

There are two jobs on two devices. On the phone you read: in pages or scroll, one page or two, at home or away, and on a foldable you hold the phone like a book. On the computer, Preform Library keeps the library: it puts the files in folders, fixes titles, authors, covers, genres and series, and shares the library privately with your phones.

## What it's for

A personal ebook collection should feel like a library. The books live in one place on the computer and stay in order, and the phone opens any of them, anywhere, at the page you left. It's working when you stop thinking about files because the books are organized, look right, and open straight away.

## What sets it apart

Book Mode on foldables. The outer screen shows the cover of the book you're reading, and opening the phone opens the book. I haven't seen another reader treat a foldable as a book.

A private library without a cloud. Your own computer serves the books to your phones over the home network or Tailscale. Phones pair by QR code and only trust that computer's certificate. There's no account and no third-party server.

Folders become shelves. However you organize the books on the computer is how they appear on the phone.

## Where it runs

The phone is Android. It was developed on a Galaxy Z Fold 8 Ultra and a Z Flip 5, and it gets used in hand, often at night, on the cover screen, the inner screen, in Flex Mode and as two-page spreads.

The computer is Windows or macOS. Preform Library lives in the tray or menu bar and runs in the background. You open its window to organize, pair a phone or change settings.

The library is a folder on disk, with sub-folders as shelves. Preform Library can edit the books (writing information into the files and keeping the originals). Book information can come from Open Library, and only titles, authors and file names are sent there.

## What's built

The phone app is Kotlin and Jetpack Compose on Readium 3.1.2. It reads in pages, scroll, two-page spreads and Flex Mode, handles right-to-left books, and has reading settings (theme, font, size, spacing, margins) for all books or per book. The library has shelves from folders, genres, all books, continue reading, search and sort. It pairs with one or more computers, syncs their catalogs, downloads books on open or keeps a whole library on the phone, and respects a storage limit. You can make folders and move books on the phone, and the computer follows.

Preform Library is Kotlin and Compose Multiplatform with an embedded HTTPS server (Ktor). The organizer has a folder tree, drag and drop, editing of book details, auto-organize with preview and undo, tidy-up checks, genre sorting, import, and the Recycle Bin or Trash. The Devices page pairs phones and connects to other computers' libraries, which you can browse, compare, copy from or mirror.

Words we use: library, shelf (a folder), Book Mode, cover screen, Private library, and Preform Library for the computer app.

Not decided yet: pricing, distribution beyond the Play Store and a signed macOS download, and a website.

## Things that don't change

The reader is called Preform, and the computer app is Preform Library.

Themes stay a choice. There are three light and three dark neutral bases plus an accent colour the reader picks, light and dark can switch by time of day, and there's no orange accent.

The mark is a closed book seen from the front. It's the app icon, the tray and menu-bar icon, and the mark inside the apps.

## What exists to show

The working apps are in `app/` (Android reader), `desktop/` (Preform Library) and `sync-protocol/`. Covers and book details come from the reader's own files and Open Library. There are no marketing images, testimonials, users or press yet, and none should be made up.

## Principles

1. The book comes first. Covers and text lead. The interface gets out of the way while you read and stays quiet while you browse.
2. One library in two places. The computer and the phone show the same library, so names, shelves, themes and marks match on both.
3. Private by design. Books never leave the reader's own devices, apart from small lookups that can be turned off.
4. Nothing gets lost. Every organizing change can be undone, edited files keep their originals, and deleted books go to the bin.
5. Native where it counts. Each platform keeps its own gestures, windows and habits. Preform's identity is in the type, colour, covers and mark.

## Accessibility

Reading has to stay comfortable for long sessions: adjustable text size, font and spacing, light and dark themes, and right-to-left books. There's no standard set beyond each platform's own accessibility support.
