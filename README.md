# FinTrack

A daily income and expense tracker for Android. Record what comes in and what goes out in a few taps, and see where the money went. FinTrack works fully offline: there is no account and no server, and the app has no internet permission. Your records stay on your phone.

**[Download the latest APK](https://github.com/ocpabdurrahim/fintrack-android/releases/latest)** · Android 8.0 or newer

| Home | Add | Statistics |
|---|---|---|
| <img src="docs/screenshots/home.png" width="240" alt="Home screen"> | <img src="docs/screenshots/add.png" width="240" alt="Adding an expense"> | <img src="docs/screenshots/stats.png" width="240" alt="Statistics"> |
| **History** | **Budgets** | **Dark theme** |
| <img src="docs/screenshots/history.png" width="240" alt="History"> | <img src="docs/screenshots/budgets.png" width="240" alt="Budgets"> | <img src="docs/screenshots/home-dark.png" width="240" alt="Home in dark theme"> |

*The screenshots are taken automatically by CI on an Android 14 emulator, using sample data.*

## Features

- **Fast entry:** income, expenses and transfers between accounts. The amount field opens ready to type, and categories are one tap.
- **Home:** total balance, this month's income and expense, today's spending, budget left, recent entries.
- **History:** month by month and grouped by day, with filters. Search covers every note and category.
- **Statistics:** spending (or income) by category as a ring chart, a six-month income-vs-expense trend, and daily spending for the month.
- **Budgets:** an overall monthly limit plus per-category limits, with "about ৳X a day left".
- **Accounts:** cash, bank, bKash/Nagad or anything else, each with a starting balance.
- **Categories:** your own names, any emoji, your colours. Categories that are still in use get archived rather than deleted.
- **Recurring entries:** daily, weekly, monthly or yearly. Rent and salary get added on their own, even if the app was closed that day.
- **App lock:** a 4-digit PIN with optional fingerprint/face unlock.
- **Daily reminder** notification at a time you pick.
- **Your data, portable:** back up to a JSON file and restore it on any phone, or export to CSV for Excel and Google Sheets.
- **Local formats:** Taka (৳) by default with 12,34,567 digit grouping, plus 20 other currencies and international grouping.
- Light and dark themes, wallpaper colours on Android 12+, and launcher shortcuts ("Add expense", "Add income").

## Tech

| Layer | What |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| State | ViewModels exposing `StateFlow`, collected lifecycle-aware |
| Data | Room (SQLite) with Flow queries; DataStore for settings |
| Background | WorkManager for the daily reminder and recurring entries |
| Security | BiometricPrompt; PIN stored as salted SHA-256 |
| Money | `Long` minor units (paisa/cents), so totals are always exact |

```
app/src/main/java/io/github/ocpabdurrahim/fin/
├── data/      Room entities, DAOs, repository, settings, backup/CSV
├── domain/    money formatting, dates, statistics, recurrence, PIN
├── system/    reminders (WorkManager), biometrics, launcher shortcuts
└── ui/        Compose screens: home, transactions, edit, stats, budgets,
               more (settings), manage (accounts/categories/recurring), lock
```

## Building

Every push is built by GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)):

1. Unit tests (money maths, statistics, recurrence, backup format) and the debug and release APKs.
2. The app runs on an Android 14 emulator. UI tests add, transfer, delete and lock, a screenshot tour of every screen is taken, and the minified release APK is smoke-tested screen by screen.
3. Pushing a tag such as `v1.0.1` publishes a GitHub release with the signed APK.

To build locally you need JDK 17+ and the Android SDK (Android Studio sets both up):

```bash
./gradlew assembleDebug
```

Release builds are signed with the key from the `FIN_KEYSTORE_*` repository secrets. Without them a local release build falls back to the debug key, and that APK cannot update one installed from the Releases page.

## Data and privacy

Everything is stored in the app's private database on the phone. Nothing is uploaded anywhere. Use **More → Back up** regularly and keep the file somewhere safe (Google Drive, email to yourself). Uninstalling the app deletes its data.
