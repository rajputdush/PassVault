# PassVault

PassVault is an offline Android password manager written in Kotlin with Jetpack Compose. Your vault is encrypted and stored only on your phone. There is no account, no server and no cloud sync.

**[Download the latest APK from GitHub Releases](https://github.com/rajputdush/PassVault/releases/latest)**

## Key features

**Vault**
- Item types: logins, payment cards and secure notes.
- Folders, favourites and filter chips. The list is sorted A to Z.
- Typo-tolerant search ("amzaon" finds Amazon). Passwords and card numbers are never searched.
- Several accounts on one site or app are grouped into one expandable entry, with an **Add another account** button that pre-fills the site. Grouping can be switched off in Settings.
- Custom fields (for example a security question or PIN), each optionally hidden.
- Password history: each login keeps its last 5 passwords.
- Trash: deleted items stay for 30 days and can be restored. Trashed items are never autofilled.
- Optional links from a login to an installed Android app, so autofill can offer it inside that app.
- Brand-coloured badges for sites are generated offline. Real site logos are an opt-in setting (see [Privacy](#privacy-and-data-handling)).
- English and Hindi interface (Settings, Appearance, Language). Text without a translation stays in English.

**Passwords and 2FA**
- Password generator: random passwords with chosen length and character types, or passphrases from the 7,776-word EFF list.
- Built-in TOTP authenticator. Paste a setup key or `otpauth://` link, or scan a QR code. Live codes with a countdown and a copy button.
- Security check: weak, reused and old passwords, duplicate logins and cards (extra copies can be moved to Trash), and cards that have expired or expire within two months.
- Optional breach check (needs internet, see below).

**Protection**
- Master password plus a 256-bit recovery key, with a printable Emergency Kit PDF (a warning is shown before it is saved, because the file holds the recovery key in plain text).
- Fingerprint unlock (Android BIOMETRIC_STRONG).
- Optional fingerprint check before opening an item or copying a password, even while unlocked (valid for 30 seconds).
- Auto-lock after a time you choose and on screen-off. Lock instantly from the app, the Quick Settings tile "Lock PassVault" or the app-icon shortcut.
- Clipboard marked as sensitive and cleared automatically after a time you choose.
- Screenshots, screen recording and the recent-apps preview are blocked by default; a switch in Settings allows them.
- Encrypted activity log of unlocks, copies, autofill, deletes and restores (item titles only, never passwords or notes). Optional; the latest 500 events are kept.
- Wrong-password lockout.

**Data portability**
- Encrypted backup and restore.
- Import from Bitwarden, 1Password, Chrome, Firefox, LastPass and other tools.
- Plain-text export (JSON or CSV) for moving to another manager.
- Share a single item as a passphrase-protected code, link or QR code. No server is involved.

**Autofill**
- Android Autofill Framework service for logins in apps and browsers, with a "Save to PassVault?" prompt for new logins.

## Security architecture

All cryptographic code lives in the pure Kotlin/JVM `crypto` module so it can be unit-tested without a device.

| Area | Implementation |
|---|---|
| Item encryption | AES-256-GCM with a fresh random 96-bit nonce per item. The item id is bound as associated data, so item files cannot be swapped. |
| Vault key | 256 random bits. It is never derived from the password; it is stored twice in the vault header, wrapped once by the master-password key and once by the recovery-key key. |
| Master password | NFKC-normalised, then **Argon2id** (64 MiB memory, 3 iterations, parallelism 1) via Argon2Kt. PBKDF2-HMAC-SHA256 with 600,000 iterations exists as a fallback. |
| Key separation | HKDF-SHA256 with separate labels produces the password-wrap and recovery-wrap keys, so one master key is never used directly for two purposes. |
| Changing the password | Only the wrapped vault key is re-wrapped; items are not re-encrypted. |
| Recovery key | 256 random bits shown once as 13 groups of 4 Base32 characters. Replacing it makes the old key stop working immediately. |
| Anti-downgrade | KDF parameters are stored in the header and checked against minimums every time it is read (Argon2id at least 19 MiB and 2 passes, PBKDF2 at least 210,000 rounds). A tampered header that weakens them is rejected. Shared-item codes bind their whole header as associated data and enforce the same minimums. |
| Fingerprint unlock | The vault key is encrypted with an Android Keystore AES key that requires a fresh BIOMETRIC_STRONG authentication for every use and is invalidated when fingerprints change. |
| Lockout | The first 4 wrong passwords are free; the 5th starts a 30 s wait, then 1 min, 5 min, 15 min, and 1 hour for every further mistake. The counter is persisted. The wait is timed with the monotonic clock, so changing the phone's date does not shorten it; a reboot restarts the wait in full. |
| Local protections | `allowBackup=false` plus data-extraction rules keep the vault out of Android cloud backups. The decrypted list is cleared from memory on lock. Release builds allow HTTPS only. |

Independent review brief: [`docs/SECURITY.md`](docs/SECURITY.md). **This project has not had an independent security audit.** Treat it accordingly.

## Privacy and data handling

- Your vault is stored in the app's private storage on your phone. No account is needed and nothing is uploaded.
- PassVault has no analytics, no ads and no tracking.
- The app declares the `INTERNET` permission, and only two optional features use the network:
  - **Breach check:** uses the Have I Been Pwned "Pwned Passwords" range API over HTTPS (`api.pwnedpasswords.com`). Only the first five characters of a password's SHA-1 hash are sent, with padding enabled; the match is made on your phone. Passwords themselves are never sent.
  - **Real site logos (off by default):** when you turn this on, the domain of a saved site (for example `amazon.com`) is requested from DuckDuckGo's icon service (`icons.duckduckgo.com`) to show its logo. Leave it off to keep your list of sites on the phone.
- Crash reports are saved only on the phone, without vault data. You decide whether to share one from Settings, About.
- Privacy policy template: [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md).

## Technology stack

- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3
- Android: minSdk 26, targetSdk and compileSdk 35
- Argon2Kt 1.5.0 (native Argon2id), AndroidX Biometric 1.1.0
- Google Play services code scanner and ZXing core (QR scanning, no camera permission)
- Gradle with a version catalog; JUnit 4 for tests

## Download and installation

1. Open the [latest release](https://github.com/rajputdush/PassVault/releases/latest) and download the APK from **Assets**.
2. Open the file on your phone and allow "Install unknown apps" for your browser or file manager when asked.
3. Create your vault: choose a master password (12 or more characters) and **save the recovery key**. It is shown only once.

The APK is distributed through GitHub Releases. Updates are installed by downloading the newer APK from the same page; each release is signed with the same key.

## Build from source (Ubuntu/Linux)

Requirements: JDK 17 or newer, and the Android SDK with platform 35.

```bash
git clone https://github.com/rajputdush/PassVault.git
cd PassVault
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew :crypto:test            # JVM unit tests
./gradlew :app:installDebug       # build and install on a connected phone or emulator
./gradlew :app:assembleRelease    # release APK (see docs/RELEASE.md for signing)
```

The debug build is signed with Android's debug key; a release APK needs your own keystore (copy `keystore.properties.example` to `keystore.properties`). A phone cannot install a release build over a debug build, because the signing keys differ. Signing and release steps are in [`docs/RELEASE.md`](docs/RELEASE.md).

Tests: the `crypto` module has 216 JVM unit tests (crypto, key handling, lockout, import, share codes, search, TOTP, autofill matching and more), and `app/src/androidTest` has 4 instrumented tests. Backup and restore, the settings screens and the Compose UI have no automated tests yet.

## Backup, restore and import

**Encrypted backup**
- Settings, Export, restore & import, then export an encrypted backup. The file holds the vault header and the item files exactly as stored, still encrypted. It can be kept in cloud storage or emailed to yourself.
- To open a backup you need the master password that was current when the backup was made, or your recovery key. If you change the password later, make a new backup.

**Restore**
- On a fresh install, choose "Restore from backup" on the first screen and pick the file, then unlock with the password from backup time or use "Forgot password? Use recovery key".
- Restore only works on a fresh install. It never overwrites an existing vault.

**Import**
- Settings, Import passwords. The format is detected from the file:

| Source | File | Brought across |
|---|---|---|
| Bitwarden | Unencrypted `.json` export | Logins, cards, secure notes, folders, favourites, 2FA secrets, custom fields and Android-app logins. Identities are skipped. |
| 1Password | `.1pux` export | Logins, cards and secure notes, with notes, 2FA secrets and extra fields. Items in 1Password's trash and other kinds (identities, bank accounts, licences) are skipped. |
| Chrome, Edge, Brave, Firefox, LastPass, Bitwarden CSV and others | `.csv` with a "password" column | Logins only. Columns are recognised by header name. |

- Rows without a password are skipped. Items already in the vault (same site, username and password; same card number; same note) are not added twice. Up to 5,000 items per file.
- Import files are plain text. Delete them as soon as you have imported.

**Plain-text export (leaving PassVault)**
- After re-entering your master password, you can export everything as unprotected Bitwarden-style JSON, or logins only as CSV. These files hold your passwords unencrypted. Items in Trash are not included.

**Share one item**
- Share securely makes a code, link or QR code that only the right passphrase (at least 16 characters; a generated five-word passphrase is suggested) can open. It works without a server. Anyone who has both the code and the passphrase can read the item, and a code cannot be taken back once sent. Password history is never included.

## Android Autofill setup

1. In PassVault: Settings, Autofill, Turn on autofill, then choose PassVault in Android's list.
2. In Chrome, also enable Settings, Autofill services, "Autofill using another service".

How it behaves:
- **Vault locked:** tapping a login box shows "Unlock PassVault" (fingerprint or master password).
- **Vault unlocked and the site matches:** the matching logins are offered directly. A match requires the saved website's registrable domain, worked out with the Mozilla Public Suffix List, to equal the page's domain (`github.com.evil.com` and `notgithub.com` do not match `github.com`, and two customers of the same host such as `a.github.io` and `b.github.io` never match each other).
- **Apps and unmatched sites:** "Choose from PassVault" opens a picker; a login is only filled into a place it was not saved for if you pick it yourself.
- **Payment forms:** "Choose a card from PassVault" opens a card picker. Cards are never put in the drop-down, never filled without you tapping the card, and never saved from forms.
- **Saving:** after you sign in somewhere, Android can offer "Save to PassVault?". If the vault is locked, an unlock screen opens first and the login is saved afterwards; the result is shown on screen. Existing logins whose password changed are updated.
- If PassVault's own unlock screen is what unlocked the vault, it locks again when you leave that screen.
- Fill the website field of a login for the best results.
- Android does not tell apps which autofill entry you tap, so the activity log records "Autofill offered" for the drop-down and "Autofilled" when you pick from PassVault's own screen.

## Project structure

```
crypto/   Pure Kotlin/JVM module, unit-tested without a device
  crypto/     AES-GCM, HKDF, KDF, VaultCrypto (key hierarchy), RecoveryKey, AttemptLimiter, password generator and audit
  importer/   Bitwarden JSON, 1Password .1pux, CSV
  share/      encrypted share codes
  autofill/   domain and card-field matching
  totp/ search/ sites/ cards/ export/ activity/ diagnostics/
app/      Android application
  autofill/   autofill service, auth and save screens
  items/      VaultItem and the encrypted one-file-per-item repository
  backup/     backup file format
  onboarding/ unlock/ vault/ settings/ security/ share/ ui/   screens and view models
  Argon2KeyDeriver, BiometricVault, VaultSession, VaultHeaderStore, AppSettings, ClipboardHelper, ActivityLog
docs/     Security brief, privacy policy, release checklist, static share page
store/    Play Store icon
```

## Known limitations

- Autofill depends on each app and browser supporting Android's autofill framework; some apps and custom fields are not detected. Saving a login needs the vault to be unlocked, or to be unlocked from the save prompt.
- Kotlin `String` values cannot be wiped, so decrypted passwords stay in memory until garbage collected.
- The lockout counter lives in the app's private storage, so someone with root access can reset it. The wait is capped at 1 hour.
- A backup opens with the master password that was current when it was made.
- Restore only works on a fresh install.
- Imports of CSV files bring logins only. There is no encrypted export to other managers; the plain-text exports are unprotected.
- The password-strength check is an estimate, not a dictionary check. The breach check needs internet.
- Passkeys are not supported. 2FA codes are copied, not filled automatically.
- Malware with accessibility or screen access, or a rooted phone, can read what is shown while the vault is unlocked.
- No independent security review has been done yet.

## Security reporting

Please report vulnerabilities privately, not in a public issue: use the **Report a vulnerability** option under the repository's **Security** tab on GitHub. Include steps to reproduce and the app version. Do not include real passwords or vault files.
