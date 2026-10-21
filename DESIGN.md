---
name: Perform
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

# Perform design

## The idea

The whole library is designed as one book series, after David Pearson's typographic Penguin covers ("Great Ideas"). Real covers come first. A book without a cover gets a typeset two-colour cover in its genre's cloth, so even a messy folder looks like an edited list. Around the books the interface stays quiet: a neutral base the reader picks, one serif for anything that's a book, the platform's own sans for controls, and hairlines instead of boxes.

Each book is one object shown at three sizes. There's the cover with its caption, in shelf rows and grids. There's the book in hand, the largest, at the top of the phone library. And there's the full card, which is the bottom sheet on the phone and the details panel on the desktop. The covers, cloths, type and caption line are the same on Android and in Perform Library on Windows and macOS. Each platform keeps its own chrome: Material top bars and sheets on the phone, a sidebar and native-feeling dialogs on the desktop.

The phone is sparse (one book in hand, then sideways shelf rows). The desktop is denser (a strict cover grid, a sidebar and a details panel). The accent colour marks the way forward and very little else.

In short:

- Six neutral bases (three light, three dark) and nine accents, chosen by the reader. Ink with Blue is the default.
- Exactly five cover cloths: claret, navy, bottle, ochre and graphite.
- Literata (variable, bundled) for titles, authors, descriptions and covers, and the platform sans for controls and data.
- Reading progress is a 2dp rule in the accent everywhere.
- Hairline dividers. No cards, badges or texture.
- The mark is a closed book bound in claret, set like a typeset cover.

## Colour

The reader chooses a neutral page and one accent. The only fixed colours are the five cloths and the claret of the mark.

### Accent

The accent (`{colors.primary}`, Blue on a dark base by default) marks the way forward: the Read/Continue button, the fill of the progress rule, the seek thumb in the reader, selection outlines on the desktop, the desktop "Add books" action, and checked boxes and selected genres while editing. Each accent has a deeper shade for light bases (`accent-*-on-light`) and a lighter one for dark bases (`accent-*-on-dark`). Light shades carry white text, and dark shades carry a near-black tint of their own hue. Mono is black on light bases and white on dark ones. There's no orange accent, by choice.

### Neutrals

Each base has four values: the page (`*-background`), a raised surface for sheets, dialogs and the desktop sidebar and panels (`*-raised`), text (`*-text`), and muted (`*-muted`).

The light bases are Mono (white page, black text), Paper (warm off-white, blue-black text) and Parchment (sand page, brown-black text). The dark bases are Noir (true black), Ink (near-black with warm grey text) and Slate (blue-grey).

Muted is for authors, counts, the caption line, section labels, unselected tabs and every secondary action. Hairlines are the text colour at low alpha over the page: 14% on the phone (dividers and the progress rule's track), and 12% on light or 10% on dark bases on the desktop. The reader's seek track uses 18% of the chrome's content colour. A selected desktop sidebar row is filled with the accent at 14% (light) or 18% (dark) over the raised surface. Danger and OK on the desktop reuse the red and green accent shades for the base's lightness, for remove actions and the sharing status dot.

### Cover cloths

Both apps take the cloths from one shared source and pick one by a book's first genre. Books without a genre are spread evenly over claret, navy, bottle and graphite.

- Claret with cream ink: Classics, Romance, Historical Fiction, Drama, Poetry. It's also the cloth of the mark.
- Navy with cream ink: Science Fiction, Philosophy, Religion, Psychology, Politics.
- Bottle with cream ink: Fantasy, Science & Nature, Health, Self-Help.
- Ochre with near-black ink: History, Biography, Travel, Art, Cooking, Young Adult, Children's, Comics.
- Graphite with cream ink: Fiction, Literary Fiction, Horror, Mystery, Thriller, Technology, Business, Reference.

Two rules follow from this. The accent only marks what moves the reader on (the book in hand's button, reading progress, selection and the main action of a view), while secondary text actions such as See all, sort and Cancel, and section labels, stay muted. And typeset covers use exactly the five cloths, two colours each, with no sixth cloth, no gradients and no accent-coloured covers.

## Type

Literata, a variable font bundled with both apps, sets titles at optical size 36 and book text in the interface (caption titles, authors, descriptions) at optical size 12. The fallback is the system serif. Labels use the platform sans: Roboto on Android, Segoe UI on Windows and SF on macOS. The desktop uses the system monospace for file paths and addresses.

The serif belongs to the books, and the sans belongs to the machine.

From largest to smallest:

- Page title, Literata display 400 at 28sp on the phone and 26sp on the desktop, with slightly tight tracking. Used for "Library", a shelf or genre name, and desktop page titles.
- Book in hand, Literata display 400 at 28sp, beside its 132dp cover, up to three lines.
- Sheet, panel and dialog titles, Literata display 400 at 24sp in the phone sheet and 22sp in the desktop panel and dialogs.
- Shelf title, Literata display 400 at 22sp, for shelf and group names, followed by the count in muted label type.
- Author, Literata italic at 16sp on the phone and 15sp in the desktop panel, muted. Authors are always italic and always under a title.
- Caption title, Literata 400 at 14sp under every cover (one line on the phone, 14/18sp on the desktop).
- Description, Literata 400 at 14sp on the phone. The desktop panel uses the sans at 13/19sp.
- Label, sans 500 at 14sp, for buttons, tabs, counts and status lines. Desktop controls run at 13sp.
- Caption line, sans 500 at 11sp on the phone and 12sp on the desktop, muted: the author · year line under a cover.
- Cover lettering, inside the typeset cover only, scaled with the cover's width from a 92dp reference. The author is in sans capitals at 6.5 × scale, tracked 1.3 × scale. The title is Literata display italic up to 15 × scale with 1.08 line height, sized so the longest word never breaks. The "PERFORM" imprint is tracked sans capitals at 5.5 × scale.

Literata sets anything that is a book (titles, authors, shelf names, descriptions, covers) plus page titles. The platform sans sets anything that's a control or data. There's never a serif button or a sans book title. On a typeset cover the title is Literata italic, set low, and the author runs in tracked capitals along a hairline at the head. Those capitals appear on covers only.

## Layout

The phone page has one 20dp margin that every title, row and grid lines up with. Shelf rows run to the screen edges and scroll sideways, with 14dp between covers, and rows are 24dp apart. From the top: the large title with search, add and settings; the book in hand (132dp cover, 20dp gap, then title, italic author, 18dp, progress rule, 8dp, status line, 18dp, button); the Shelves / Genres / All books tabs over a full-width hairline; then shelf rows (title, count and "See all" on the right, 6dp, covers) and the grid of loose books with its count and sort. The grid uses adaptive columns with a 96dp minimum, so its covers match the shelf rows. With several libraries paired, Shelves groups each computer's shelves under its name, followed by the phone's own books.

The desktop window has three columns. The raised sidebar has the Library, Folders, Genres and Other libraries groups with counts, Devices and Settings at the foot, and a sharing status line. The mark and "Perform Library" sit in the window's own title bar above it. The page has a toolbar (serif title, book count, search, sort, Organize…, Add books, refresh) over a strict cover grid of 150dp adaptive cells. The details panel has 24dp padding, a large cover at 78% width and the full card. Dialogs are raised surfaces with 28dp padding.

Within a surface, every cover in a row or grid is the same size. Only the book in hand and the full card are larger, and covers are always 2:3.

Under every cover, in this order: the title (serif, one line), then the author's surname and year (muted sans; the full author on the desktop), then the progress rule once reading has started. A book that's on a computer but not yet on the phone adds a 12dp hairline cloud after the caption text, never on the cover. On the desktop, another library's book can add one muted line with a tick: "In yours" or "Another edition in yours".

## Depth

Surfaces are flat and tonal: the page, then the raised surface for sheets, dialogs, the sidebar and panels. Covers are the only things on the page that cast a shadow, a soft lift like a real book. Shelf and grid covers use Compose elevation 3dp with the 3dp corner. The book in hand, the phone sheet cover and the desktop details cover use 12dp with the 4dp corner. Platform sheets, dialogs and menus keep their native elevation. Overlays on a cover while it downloads or opens are black at 60% with a white 2.5dp ring.

Rows, tabs, captions and controls sit flat. Nothing but a cover casts a shadow.

## Shapes

Cover corners are barely softened (3dp, or 4dp at large size), like trimmed board. Buttons are platform pills. Desktop chrome uses moderate rounding: 8dp sidebar rows, 12dp preview wells, 16dp dialogs, and a 20dp capsule with a 1dp hairline for search. Rules are straight: 2dp for progress, hairlines for dividers, and a 0.75dp-equivalent hairline on the cover's head. The mark is a closed book seen from the front, with page edges behind, a claret cover with a darker spine band, a short author bar and hairline at the head, and two title bars low down.

## Components

### Buttons

Buttons are platform pills. The primary button has the accent fill, on-accent text and a sans label, and there's one per view: Read / Continue, Choose library folder, Edit, Save, Pair a phone. Quiet buttons are text only, in the muted colour, for See all, the sort menu and other secondary actions. Outlined buttons, with a hairline outline, are the second choice beside a primary, such as Add a book or Show in Explorer/Finder.

### Progress rule

A book's reading state is one line: a 2dp track in the hairline colour with the accent filling the read part. It sits under the book in hand, under every started cover, and in the sheet and details panel. In the reader it becomes the seek bar, with a 12dp accent thumb that grows to 18dp while you drag and seeks when you let go. Progress is never shown with a badge, pill or ring.

### View tabs

Shelves / Genres / All books are sans labels. The selected tab is in the text colour with an underline matched to its label, the others are muted, and a full-width hairline runs underneath. There are no filled or pill tabs.

### Desktop sidebar

Each row has an 18dp line glyph, a 14sp sans name and a muted count. A selected row gets the accent-tinted fill and an accent glyph. While books are being dragged, the row under them gets a 30% accent fill and a 1dp accent outline. Group labels (Library, Folders, Genres) are small muted capitals. Another library's row has a small online dot beside its count.

### Desktop cover card

There's no tile and no card: the cover, 10dp, the serif title, the muted author · year, and the progress rule. Selection outlines the cover itself with a 2dp accent border at the cover's corner radius.

### Inputs

On the phone, search replaces the title bar with a transparent text field. On the desktop, search is a raised capsule with a hairline outline, a muted search glyph and a clear glyph. Dialog fields are the platform's outlined fields.

### Typeset cover

Every book without a cover gets the same design on phone and desktop: the cloth ground, the author in tracked capitals at the head with a hairline under it, the title in Literata italic set low, and the "PERFORM" imprint at the foot in tracked capitals. All the lettering scales with the cover's width, so a 56dp placeholder in an empty library, a 96dp shelf cover and a full cover are the same design.

### Full card

On the phone it's a bottom sheet: the 112dp cover beside the title, italic author, series, progress rule and primary button, then the description in Literata, a hairline, and label/value facts (Genre, Published, Format, Where) with muted labels in a 96dp column. On the desktop, the details panel stacks the same content under a large cover, with 64dp label columns.

### Icons

Line glyphs at a 1.8 stroke with round caps and joins: Material outlined on the phone and a matching hand-drawn set on the desktop. The app icons, the Android adaptive icon and the Windows tray icon use the claret series mark. The macOS menu bar uses the monochrome book mark.

## Do and don't

Do:

- Keep the accent for the way forward: the primary button, the progress rule, the seek thumb and selection.
- Set secondary text actions and section labels in the muted colour.
- Give every book without a cover the typeset cover in one of the five cloths, picked by its first genre.
- Keep one cover size per row or grid, at 2:3, with the book in hand the largest.
- Put the same caption under every cover: title, then author · year, then the 2dp progress rule.
- Separate things with hairlines (the text colour at 10 to 14% over the page) and let covers carry the only shadow.
- Use Literata for titles, authors, descriptions and covers, with the display optical size for large titles, and the platform sans for controls and data.
- Use the six bases and nine accents exactly as defined. The desktop palette matches the phone's.

Don't:

- Wrap books in cards or tiles, or add badges, pills or percentage chips for state. The rule is the state.
- Add texture, gradients, paper grain or decorative frames.
- Put a small label above a heading. Tracked capitals belong on the typeset cover, and on the desktop only as group labels in the organizer's sidebar and lists, never above a serif title and never on the phone.
- Mark state on a cover. Download state goes in the caption as a hairline glyph.
- Add a sixth cloth or an orange accent.
- Set controls in Literata or book titles in the sans.
- Colour secondary actions, details or labels with the accent.
