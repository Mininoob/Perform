---
name: Preform
description: A personal ebook library set as one designed book series, on the phone and on the computer.
colors:
  primary: "#8AB4F8"
  on-primary: "#0A1A33"
  mono-background: "#FFFFFF"
  mono-raised: "#F1F1F1"
  mono-text: "#000000"
  mono-muted: "#5E5E5E"
  paper-background: "#F7F5F0"
  paper-raised: "#ECE8E0"
  paper-text: "#1D1F24"
  paper-muted: "#6A6E76"
  parchment-background: "#F3E9D2"
  parchment-raised: "#E9DCBD"
  parchment-text: "#3B2F25"
  parchment-muted: "#7A6650"
  noir-background: "#000000"
  noir-raised: "#121212"
  noir-text: "#FFFFFF"
  noir-muted: "#8C8C8C"
  ink-background: "#131416"
  ink-raised: "#1C1E21"
  ink-text: "#E4E2DD"
  ink-muted: "#93979E"
  slate-background: "#1A1E24"
  slate-raised: "#232830"
  slate-text: "#E1E5EA"
  slate-muted: "#8B949E"
  accent-mono-on-light: "#000000"
  accent-red-on-light: "#C62F3A"
  accent-pink-on-light: "#B83C72"
  accent-yellow-on-light: "#8F7100"
  accent-green-on-light: "#2F7D3A"
  accent-teal-on-light: "#12786F"
  accent-blue-on-light: "#2457C5"
  accent-indigo-on-light: "#4B4FC4"
  accent-violet-on-light: "#7A3FC0"
  accent-mono-on-dark: "#FFFFFF"
  accent-red-on-dark: "#EF6B73"
  accent-pink-on-dark: "#F28AB4"
  accent-yellow-on-dark: "#F2D263"
  accent-green-on-dark: "#8FD18A"
  accent-teal-on-dark: "#5FD0C8"
  accent-blue-on-dark: "#8AB4F8"
  accent-indigo-on-dark: "#A3A6FF"
  accent-violet-on-dark: "#D29BF0"
  cloth-claret: "#7A1F2B"
  cloth-claret-ink: "#F1E6D4"
  cloth-navy: "#1E3A5F"
  cloth-navy-ink: "#E9E4D8"
  cloth-bottle: "#2E5E4E"
  cloth-bottle-ink: "#EDE8DC"
  cloth-ochre: "#D9C27A"
  cloth-ochre-ink: "#1B1B1B"
  cloth-graphite: "#2A2B2E"
  cloth-graphite-ink: "#E9E5DC"
  mark-spine: "#5E1621"
  mark-pages: "#E9E1D2"
typography:
  page-title:
    fontFamily: "Literata, serif"
    fontSize: "28sp"
    fontWeight: 400
    lineHeight: "36sp"
    letterSpacing: "-0.2sp"
    fontVariation: "'opsz' 36"
  book-in-hand:
    fontFamily: "Literata, serif"
    fontSize: "28sp"
    fontWeight: 400
    lineHeight: "36sp"
    letterSpacing: "-0.2sp"
    fontVariation: "'opsz' 36"
  sheet-title:
    fontFamily: "Literata, serif"
    fontSize: "24sp"
    fontWeight: 400
    lineHeight: "32sp"
    fontVariation: "'opsz' 36"
  shelf-title:
    fontFamily: "Literata, serif"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: "28sp"
    fontVariation: "'opsz' 36"
  author:
    fontFamily: "Literata, serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    fontVariation: "'opsz' 12"
  caption-title:
    fontFamily: "Literata, serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    fontVariation: "'opsz' 12"
  description:
    fontFamily: "Literata, serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    fontVariation: "'opsz' 12"
  cover-title:
    fontFamily: "Literata, serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: 1.08
    fontVariation: "'opsz' 36"
  cover-author:
    fontFamily: "Roboto, Segoe UI, SF Pro, sans-serif"
    fontSize: "6.5sp"
    letterSpacing: "1.3sp"
  label:
    fontFamily: "Roboto, Segoe UI, SF Pro, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
  furniture:
    fontFamily: "Roboto, Segoe UI, SF Pro, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.5sp"
  desktop-control:
    fontFamily: "Segoe UI, SF Pro, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
rounded:
  cover: "3dp"
  cover-large: "4dp"
  nav-row: "8dp"
  well: "12dp"
  dialog: "16dp"
  search: "20dp"
  pill: "9999dp"
spacing:
  page-margin: "20dp"
  cover-gap: "14dp"
  row-gap: "24dp"
  caption-gap: "10dp"
  rule-gap: "6dp"
  in-hand-gap: "20dp"
  panel: "24dp"
  dialog: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "10dp 24dp"
  button-quiet:
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "10dp 12dp"
  progress-rule:
    backgroundColor: "{colors.primary}"
    height: "2dp"
  cover-thumb:
    rounded: "{rounded.cover}"
    width: "96dp"
  cover-in-hand:
    rounded: "{rounded.cover-large}"
    width: "132dp"
  cover-sheet:
    rounded: "{rounded.cover-large}"
    width: "112dp"
  view-tab:
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
  view-tab-selected:
    textColor: "{colors.ink-text}"
    typography: "{typography.label}"
  desktop-search:
    backgroundColor: "{colors.ink-raised}"
    textColor: "{colors.ink-text}"
    typography: "{typography.desktop-control}"
    rounded: "{rounded.search}"
    padding: "8dp 12dp"
  desktop-nav-row:
    textColor: "{colors.ink-text}"
    rounded: "{rounded.nav-row}"
    padding: "7dp 10dp 7dp 8dp"
  desktop-dialog:
    backgroundColor: "{colors.ink-raised}"
    rounded: "{rounded.dialog}"
    padding: "28dp"
---

# Design System: Preform

## Overview

**Creative North Star: "The Great Ideas Series"**

The library is one designed book series, after David Pearson's typographic Penguin covers. Real covers lead; any book without one is given a typeset two-colour cover in its genre's cloth, so a messy folder reads as an edited list. The interface around the books is quiet: a reader-chosen neutral base, one serif voice for books, the platform sans for everything that is a control, and hairlines instead of containers.

Each book is one object shown in three folds: the cover with its caption (shelf rows and grids), the book in hand (largest scale, top of the phone library), and the full card (phone bottom sheet, desktop details panel). The same covers, cloths, type and furniture line appear on the Android phone and in Preform Library on Windows and macOS; each platform keeps its own chrome (Material top bar and sheets on the phone, a native-feeling sidebar and dialogs on the desktop).

Density is low on the phone (one book in hand, then sideways shelf rows) and moderate on the desktop (strict cover grid, sidebar, details panel). The reader's accent marks the way forward and almost nothing else.

**Key Characteristics:**
- Six neutral bases (three light, three dark) and nine accents, chosen by the reader; Ink with Blue is the default.
- Five cover cloths, exactly: claret, navy, bottle, ochre, graphite.
- Literata (variable, bundled) for titles, authors, descriptions and covers; platform sans for controls and data.
- Reading state is a 2dp rule in the accent, everywhere.
- Hairline dividers, no cards, no badges, no texture.
- The mark is the closed book bound in claret, set like a typeset cover.

## Colors

The palette is a reader's choice of neutral page plus one accent; the only fixed colours are the five cloths and the claret of the mark.

### Primary
- **The Reader's Accent** (`{colors.primary}`, default Blue on a dark base): the way forward. The Read/Continue button, the progress rule's fill, the reading seek thumb, desktop selection outlines, the desktop "Add books" action, checked boxes and selected genres in editing. Each accent has a deeper shade for light bases (`accent-*-on-light`) and a lighter shade for dark bases (`accent-*-on-dark`); light shades carry white text, dark shades carry a near-black tint of their own hue. Mono is black on light bases and white on dark ones. No orange accent exists, by brand commitment.

### Neutral
Each base defines four values: page (`*-background`), raised surface for sheets, dialogs and the desktop sidebar/panels (`*-raised`), text (`*-text`), and muted (`*-muted`).
- **Mono** (white page, black text), **Paper** (warm off-white, blue-black text), **Parchment** (sand page, brown-black text): the light bases.
- **Noir** (true black), **Ink** (near-black, warm grey text), **Slate** (blue-grey): the dark bases.
- **Muted** carries authors, counts, the furniture line, section labels, unselected tabs, and every secondary action.
- **Hairlines** are the text colour at low alpha over the page: 14% on the phone (dividers, the progress rule's track), 12% light / 10% dark on the desktop. The reader seek track uses 18% of the chrome content colour.
- **Selection fill** (desktop sidebar row): the accent at 14% (light) / 18% (dark) over the raised surface.
- **Danger and OK** on the desktop reuse the red and green accent shades for the base's lightness (remove actions, the sharing status dot).

### Cover Cloths
Shared by both apps from one source, assigned by a book's first genre (titles without a genre pick steadily from claret, navy, bottle, graphite):
- **Claret** with cream ink: Classics, Romance, Historical Fiction, Drama, Poetry. Also the cloth of the mark.
- **Navy** with cream ink: Science Fiction, Philosophy, Religion, Psychology, Politics.
- **Bottle** with cream ink: Fantasy, Science & Nature, Health, Self-Help.
- **Ochre** with near-black ink: History, Biography, Travel, Art, Cooking, Young Adult, Children's, Comics.
- **Graphite** with cream ink: Fiction, Literary Fiction, Horror, Mystery, Thriller, Technology, Business, Reference.

### Named Rules
**The Way Forward Rule.** The accent marks only what moves the reader on: the book in hand's button, reading progress, selection, and the primary action of a view. Secondary text actions (See all, sort, Cancel) and section labels are muted.

**The Five Cloths Rule.** Typeset covers use exactly the five cloths, two colours each. No sixth cloth, no gradients, no accent-coloured covers.

## Typography

**Display Font:** Literata, variable, optical size 36 (bundled; fallback serif)
**Body Font:** Literata, variable, optical size 12, for book text set in the interface (caption titles, authors, descriptions)
**Label Font:** the platform sans (Roboto on Android, Segoe UI on Windows, SF on macOS); system monospace for file paths and addresses on the desktop

**Character:** a book face that reads as the series' imprint against a plain native sans. Literata belongs to books; the sans belongs to the machine.

### Hierarchy
- **Page title** (Literata display 400, 28sp phone / 26sp desktop, slight negative tracking): "Library", a shelf or genre name inside it, desktop page titles.
- **Book in hand** (Literata display 400, 28sp): the book being read, beside its 132dp cover, up to three lines.
- **Sheet / panel / dialog title** (Literata display 400, 24sp phone sheet, 22sp desktop panel and dialogs).
- **Shelf title** (Literata display 400, 22sp): shelf and group names, followed by the count in muted label type.
- **Author** (Literata italic, 16sp phone, 15sp desktop panel, muted): always italic, under a title.
- **Caption title** (Literata 400, 14sp, one line on phone; 14/18sp on desktop): under every cover.
- **Description** (Literata 400, 14sp phone; desktop panel uses the sans at 13/19sp).
- **Label** (sans 500, 14sp): buttons, tabs, counts, status lines. Desktop controls run at 13sp.
- **Furniture** (sans 500, 11sp phone / 12sp desktop, muted): the author · year line under a cover.
- **Cover lettering** (inside the typeset cover only, scaled with cover width from a 92dp reference): author in sans capitals at 6.5 × scale, tracked 1.3 × scale; title in Literata display italic up to 15 × scale with 1.08 line height, sized so the longest word never breaks; "PREFORM" imprint in tracked sans capitals at 5.5 × scale.

### Named Rules
**The Two Voices Rule.** Literata sets anything that is a book (titles, authors, shelf names, descriptions, covers) and page titles; the platform sans sets anything that is a control or data. Never a serif button, never a sans book title.

**The Italic Title Rule.** On a typeset cover, the title is Literata italic, set low; the author runs in tracked capitals along a hairline at the head. These capitals live on the cover only.

## Layout

The phone page has a single 20dp margin that every title, row and grid aligns to; shelf rows bleed to the screen edges and scroll sideways with 14dp between covers. Vertical rhythm in the library is 24dp between rows. Order from the top: large title with search, add and settings; the book in hand (132dp cover, 20dp gap, title, italic author, 18dp, progress rule, 8dp, status line, 18dp, button); the Shelves / Genres / All books tabs over a full-width hairline; then shelf rows (title, count, "See all" on the right, 6dp, covers) and the loose-book grid with its count and sort. The grid uses adaptive columns with a 96dp minimum so its covers sit at the shelf-row scale.

The desktop window is three columns: a raised sidebar (mark and "Preform Library", Library / Folders / Genres groups with counts, Phones and Settings at the foot, a sharing status line), the page (toolbar with serif title, book count, search, sort, Organize…, Add books, refresh; then a strict cover grid of 150dp adaptive cells), and a details panel (24dp padding, large cover at 78% width, the full card). Dialogs are 28dp-padded raised surfaces.

**The One Scale Rule.** Within a surface, every cover in a row or grid is the same size; only the book in hand and the full card are larger. Covers are always 2:3.

**The Furniture Line Rule.** Under every cover, in this order and place: the title (serif, one line), then author surname · year (muted sans; the full author on the desktop), then the progress rule when reading has begun. A book on the PC but not on the phone adds a 12dp hairline cloud after the furniture text, never on the cover.

## Elevation & Depth

Surfaces are flat and tonal: page, then raised for sheets, dialogs, the sidebar and panels. Covers are the only objects in the page that cast a shadow, a soft ambient elevation that lifts them like real books: low for shelf and grid covers, deeper for the book in hand and the full-card cover. Platform sheets, dialogs and menus keep their native elevation. Scrims over a cover (downloading, opening) are black at 60% with a white 2.5dp ring.

### Shadow Vocabulary
- **Cover at rest** (Compose elevation 3dp, following the 3dp corner): every shelf, grid and desktop card cover.
- **Cover in hand** (Compose elevation 12dp, following the 4dp corner): the book in hand, the phone sheet cover, the desktop details cover.

### Named Rules
**The Only Books Lift Rule.** Nothing in the page casts a shadow except a cover. Rows, tabs, captions and controls sit flat on the page.

## Shapes

Covers have barely softened corners (3dp; 4dp at large size), like trimmed board. Buttons are platform pills. Desktop chrome uses moderate rounding: sidebar rows 8dp, preview wells 12dp, dialogs 16dp, the search field a 20dp capsule with a 1dp hairline. Rules are straight: 2dp for progress, hairline for dividers, a 0.75dp-equivalent hairline on the cover's head. The mark is a closed book seen from the front: page edges behind, claret cover with a darker spine band, a short author bar and hairline at the head, two title bars low.

## Components

### Buttons
- **Shape:** platform pill (fully rounded).
- **Primary:** accent fill, on-accent text, sans label. One per view: Read / Continue, Choose library folder, Edit, Save, Pair a phone.
- **Quiet (secondary):** text-only in the muted colour: See all, the sort menu, other secondary text actions.
- **Outlined:** hairline outline for the second choice beside a primary (Add a book, Show in Explorer/Finder).

### Progress Rule
The book's reading state as one line: a 2dp track in the hairline colour with the accent filling the read fraction. It appears under the book in hand, under every started cover, in the sheet and details panel, and becomes the reader's seek bar (a 12dp accent thumb that grows to 18dp while dragging and seeks on release). No badge, pill or ring shows progress.

### View Tabs
Shelves / Genres / All books in sans labels; the selected tab in the text colour with an underline indicator matched to its label, the others muted; a full-width hairline beneath. No filled or pill tabs.

### Navigation (desktop sidebar)
Rows with an 18dp line glyph, a 14sp sans name and a muted count. Selected: the accent-tinted fill with the glyph in the accent. While dragging books, a target row takes a 30% accent fill and a 1dp accent outline. Group labels (Library, Folders, Genres) are small muted capitals; see Do's and Don'ts for their limit.

### Cover Card (desktop)
No tile and no card: the cover, 10dp, the serif title, the muted author · year, the progress rule. Selection outlines the cover itself with a 2dp accent border at the cover's corner radius.

### Inputs
Phone search replaces the title bar with a transparent text field. Desktop search is a raised capsule with a hairline outline, a muted search glyph and a clear glyph. Dialog fields are platform outlined fields.

### Typeset Cover (signature)
For every cover-less book, identical on phone and desktop: the cloth ground; the author in tracked capitals at the head, a hairline rule beneath; the title in Literata italic set low; the "PREFORM" imprint at the foot in tracked capitals. All lettering scales with the cover width, so a 56dp empty-library placeholder, a 96dp shelf cover and a full cover are one design.

### The Full Card
Phone: a bottom sheet with the 112dp cover beside the title, italic author, series, progress rule and primary button; the description in Literata; a hairline; then label/value facts (Genre, Published, Format, Where) with muted labels in a 96dp column. Desktop: the details panel stacks the same content under a large cover with 64dp label columns.

### Icons
Line glyphs at 1.8 stroke with round caps and joins (Material outlined on the phone, a matching hand-drawn set on the desktop). The app icons, Android adaptive icon and Windows tray use the claret series mark; the macOS menu bar uses the monochrome book mark.

## Do's and Don'ts

### Do:
- **Do** keep the accent for the way forward: the primary button, the progress rule, the seek thumb, selection.
- **Do** set secondary text actions and section labels in the muted colour.
- **Do** give every cover-less book the typeset cover in one of the five cloths, chosen by its first genre.
- **Do** keep one cover scale per row or grid, at 2:3, with the book in hand largest.
- **Do** put the same furniture under every cover: title, then author · year, then the 2dp progress rule.
- **Do** separate with hairlines (text at 10–14% over the page) and let covers carry the only shadow.
- **Do** use Literata for titles, authors, descriptions and covers, with the display optical size for large titles; use the platform sans for controls and data.
- **Do** use the six bases and nine accents exactly as defined; the desktop palette mirrors the phone's.

### Don't:
- **Don't** wrap books in cards or tiles, and don't add badges, pills or percentage chips for state; the rule is the state.
- **Don't** add texture, gradients, paper grain or decorative frames.
- **Don't** put a kicker or eyebrow label above a heading. Tracked capitals belong on the typeset cover, and on the desktop only as list-group labels in the organizer's chrome (sidebar groups, list sections), never above a serif title and never on the phone.
- **Don't** mark state on a cover; download state goes in the caption as a hairline glyph.
- **Don't** add a sixth cloth or an orange accent.
- **Don't** set controls in Literata or book titles in the sans.
- **Don't** colour secondary actions, metadata or labels with the accent.
