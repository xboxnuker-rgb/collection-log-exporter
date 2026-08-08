# Collection Log Exporter

A local-only RuneLite side-panel plugin that exports every incomplete
Collection Log page and missing slot to a sortable spreadsheet.

## Features

- Reads the account's complete item snapshot from the Collection Log's existing,
  user-initiated Search operation. The normal page is restored immediately; the
  plugin never clicks Search or injects game input.
- Uses RuneScape cache enums and structs for the current category, page and item
  definitions rather than maintaining a second static Collection Log.
- Shows page progress such as `3/5` on every missing-item row.
- Adds loose activity, attempts-per-hour, drop-rate, completed-KC and effective
  time estimates from an offline bundled dataset.
- Marks missing items `Anytime` once the page KC reaches their nominal drop
  rate, and shows how far over rate the player is.
- Ranks incomplete pages by effective hours to completion.
- Uses the logged-in display name to retrieve public boss, raid, clue and
  supported activity counters from the official Jagex hiscores.
- Shows every Collection Log page in a categorized KC-coverage checklist;
  Export unlocks after the whole-log item sync, while uncovered pages are
  optional manual inspections.
- Keeps uncovered pages in the first checklist and moves covered pages into a
  separate section underneath.
- Uses a crisp 16×16 spreadsheet-style `CLE` toolbar icon in OSRS gold.
- Excludes pages that do not expose a meaningful KC/attempt counter, such as
  Shooting Stars and Random Events.
- Offers three local export formats:
  - Excel workbook (`.xlsx`)
  - OpenDocument spreadsheet (`.ods`) for LibreOffice/OpenOffice
  - Universal CSV (`.csv`) for Google Sheets and other spreadsheet software
- Lets the user choose detail level, sorting and main/iron estimate rates before
  selecting a destination.
- Uploads no Collection Log data. The only network request is a public Jagex
  hiscore lookup for the logged-in display name.

## Using the plugin

1. Log in and open your own Collection Log.
2. If the side panel is not already listening, press **Sync open Collection Log**.
	Click the Collection Log's native **Search** button once. The panel will show
	`Snapshot ready` after the transmission settles.
3. Wait briefly for the public Jagex hiscore KC lookup. Supported pages are
   checked automatically.
4. **Choose export...** is already available. Optionally open the remaining
   unchecked pages first to improve their KC-based estimates.
5. Choose the format, detail level, sort order and estimate profile.
6. Select the destination file.

For Google Sheets, export CSV or XLSX and use
**File → Import → Upload** in Sheets. Direct upload is intentionally not
implemented so Collection Log data never leaves the client.

## Exported data

The workbook formats contain:

- **Page summary** — native sortable table with closest rank, category, page,
  progress, remaining slots, completed KC, effective page time and totals.
- **Remaining items** — native sortable table with one row per missing slot,
  page context, item ID, suggested activity, drop rate, completed KC and
  effective-time columns.
- **About** — player/export metadata, methodology, privacy note and estimate
  limitations.

CSV can contain one table. `Page summary only` exports the summary; the other
detail choices export missing-item rows with page summary fields repeated.

XLSX data sheets are native Excel tables with filter buttons. The totals
summary sits in a separately styled row immediately below each table so
literal progress values cannot invalidate Excel's formula-oriented totals-row
metadata.
The official progress read from the Collection Log title (for example
`910/1706`) is shown beside a separately calculated scanned total, making
snapshot discrepancies visible.

Shared items displayed on several Collection Log pages count once in global
missing totals and appear once in the detailed item export. Individual page
progress still reflects every slot displayed on that page.

### Current KC

The user clicking the Collection Log Search button exposes the complete owned-item snapshot but
not every page's KC/attempt counter. RuneProfile and WikiSync use that same
whole-log response; the exporter can passively merge those responses too.
After Sync, one official Jagex hiscore lookup fills supported boss, raid, clue
and activity counters. Manually rendered page headers take precedence, followed
by Jagex hiscores and RuneLite's local killcount cache. Pages not represented
in hiscores can be opened manually; otherwise they still export with a blank
counter and baseline estimate.

Scroll Cases are handled per item rather than with one misleading combined page
counter. Each Beginner, Easy, Medium, Hard, Elite and Master case uses that
tier's clue-completion count and its own milestone; the Mimic case uses Mimic
completions. When those public hiscore values are available, the Scroll Cases
page does not need to be opened manually.

Confirmed owned items are merged monotonically for the current RuneScape
profile, so a partial Search transmission cannot erase previously captured
slots. Cache variation IDs also fall back to normalized item-name matching.

### Estimate interpretation

Estimates are planning aids, not probability predictions. Each missing item
uses the fastest known activity for the selected account profile:

`attempts to rate = max(drop-rate attempts - completed KC, 0)`

`effective item hours = attempts to rate / attempts per hour + first-time overhead`

Once completed KC reaches the nominal target, the time cell displays `Anytime`
and `KC over target` shows the excess. This intentionally credits completed KC
for practical sorting even though random drops are generally memoryless; it
does not mean the next kill is more likely.

The Remaining items table starts with category, page, progress, missing item
and time estimates, followed by the supporting calculation fields. Its
`Page priority` heading is the overall page-completion order (1 is closest), not
an item rank. Count-like values display as whole numbers. Estimated item hours
below 10 retain up to three useful decimal places; larger item estimates display
as whole hours, while page totals retain two decimals. Unknown time estimates
display `Estimate unavailable` instead of a misleading zero. The About sheet
contains a line-by-line column glossary, and native workbook table styling
provides alternating row shading. Its short branded introduction and bundled
GSVS logo are embedded directly inside XLSX and ODS files, with no external
image link. Data-sheet columns are automatically sized
to their longest displayed heading or value, including the native table
filter-button space in each header.

Page time is the sum of its missing-item effective estimates. It displays
`Estimate unavailable` if any missing slot has no bundled estimate. Summing is
deliberately simple
and can overstate or understate activities where several items share a roll,
drops are sequential, one page counter covers several modes, or personal
performance differs from the source rates.

The bundled rate data is attributed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Development

Java 11 is required.

```powershell
.\gradlew.bat clean test
.\gradlew.bat run
```

When using a Jagex Account, follow RuneLite's
[development-client login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

RuneScape widgets and the file chooser require manual in-game testing in a
fresh development-client JVM. Never automate game input.

## License

This project is licensed under the BSD 2-Clause License. See [`LICENSE`](LICENSE).
