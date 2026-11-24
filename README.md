# Local Budget

A simple, private budget tracker for Android. Open the app, type an amount, pick a category, done.

- **100 % offline & local** – the app requests *no permissions at all* (not even internet).
  Everything is stored in a SQLite database on your phone.
- **No bank integration, no accounts, no ads, no tracking.**

## Features

| | |
|---|---|
| **Fast entry** | Big number pad, expense/income toggle, category chips, optional note and date. The last used category is preselected. |
| **Overview** | Balance, income and expenses for the current month, budget progress, *“€X/day left for N days”*, upcoming bills, recent entries. |
| **Monthly budget** | Overall monthly budget plus optional per-category budgets with progress bars (turn red when exceeded). |
| **Custom budget month** | Start your budget month on any day (1–28), e.g. on payday. |
| **Recurring items / monthly billing** | Rent, subscriptions, insurance, salary… weekly, monthly, quarterly, yearly or every *N* weeks/months/years, with optional end date and pause switch. Due entries are booked automatically (with catch-up if you didn't open the app for a while). Shows your fixed costs per month. You can also tick *Repeat monthly* while adding a normal entry. |
| **History** | Browse by month, filter expenses/income, full-text search over notes & categories, swipe to delete (with undo), tap to edit. |
| **Statistics** | Donut chart per category, spending pace vs. budget, projected month total, last 6 months income vs. expenses, savings rate. |
| **Categories** | Emoji + colour, add/edit/delete; deleting keeps the entries as “Uncategorized”. |
| **Home screen widget** | Shows what's left of your budget (or what you spent) and has **− Expense / + Income** buttons that jump straight to the number pad. |
| **App shortcuts** | Long-press the app icon → *Add expense* / *Add income*. |
| **Backup** | Export/restore a JSON backup and export all entries as CSV – to any location you choose (Downloads, SD card, …). |
| **Material You** | Dynamic colours on Android 12+, light & dark theme. |

## Install

**On your phone, open this link:**
https://github.com/lorenzweinlinger/local-budget/releases/latest/download/local-budget.apk

It always points to the newest build (every push is built by GitHub Actions and published under
*Releases*). Open the downloaded file and allow “install unknown apps” for your browser when asked.
Requires Android 8.0 or newer. (If the repository is private, you need to be logged in to GitHub in that browser.)

> Updates install over the previous version and keep your data, because every build is signed with the
> same key (`keystore/debug.keystore`, a throw-away key committed to the repo). If you want to sign with your
> own private key instead, set `LB_KEYSTORE_PATH`, `LB_KEYSTORE_PASSWORD`, `LB_KEY_ALIAS` and `LB_KEY_PASSWORD`
> when building – note that switching keys later requires uninstalling (export a backup first!).

## Build locally

Requires JDK 17+ and the Android SDK (Android Studio works out of the box).

```sh
./gradlew testDebugUnitTest   # unit + Robolectric UI tests
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Code layout

```
app/src/main/java/com/localbudget/
  domain/   pure Kotlin logic: money parsing/formatting, budget periods, recurrence rules, number pad
  data/     SQLite database, repository, settings, JSON/CSV backup
  ui/       Jetpack Compose screens (overview, entry, history, stats, recurring, settings)
  widget/   home screen widget
```
