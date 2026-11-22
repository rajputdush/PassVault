# PassVault (working name)

Offline-first Android password manager.

- **Step 1:** project setup, crypto core, onboarding (master password + recovery key).
- **Step 2:** unlock screen, fingerprint unlock, auto-lock, vault list (add / edit / delete / search),
  password generator, copy with auto-clearing clipboard.
- **Step 3:** change master password, wrong-password lockout, settings screen,
  encrypted backup export and restore.
- **Security check (this version):** weak / reused / old password report, optional data-breach check.
- **Cards and secure notes:** items now have a type (login, card, note). Cards check the number (Luhn) and expiry; notes are free text. Favourites, folders and filter chips organise the list.
- **2FA codes:** paste a setup key or `otpauth://` link, or scan the QR code (Google Play services scanner, no camera permission). The live code, countdown and copy button are in the login, with a quick "2FA" button in the list.
- **Passphrases:** the generator dialog offers passwords (length and character types) or passphrases from the 7,776-word EFF list.
- **Apps:** logins can be linked to an Android package so autofill offers them directly inside that app; password change forms update the right login.
- **Custom fields:** any item can carry extra labelled values (security question, account number, PIN), each optionally hidden.
- **Fuzzy search:** typo-tolerant ("amzaon" finds Amazon, "gml" finds Gmail) with best matches first. Passwords and card numbers are never searched.
- **Emergency Kit:** Settings -> Security -> "Replace your recovery key" creates a new recovery key (the old one stops working) and a printable one-page PDF. The PDF is also offered when you first set up the vault.
- **Bitwarden import:** the unencrypted Bitwarden .json export brings logins, cards, secure notes, folders, favourites, 2FA codes, custom fields and Android-app logins. CSV from Chrome, Firefox, 1Password, LastPass and others still imports logins.
- **Card expiry warnings:** the Security check lists cards that have expired or expire within two months.
- **Several accounts on one site:** logins for the same site or app are grouped into one expandable entry ("Amazon, 3 accounts"), each account listed by username with an optional label such as Work. Possible duplicates are flagged. Turn grouping off in Settings, Appearance, Your list.
- **Logos and linked apps:** typing a finished website address (like amazon.com) shows its logo once you pause, if real logos are on (the editor offers to turn them on). "Choose an app" lists the apps on your phone with search, and a login linked to an installed app shows that app's own icon.
- **Cards:** add them inside PassVault. Autofill fills a saved card only when you pick it; cards are not saved from payment forms.
- **Trash:** deleted items go to Trash (a "Trash" chip appears in the list) and can be restored for 30 days; then they are removed for good. Trashed items are never autofilled or counted in the security check.
- **Password history:** each login keeps its last 5 passwords (shown, copied or put back from the editor). Sharing never includes the history.
- **Leave any time:** Settings → Backup & import can export everything as plain JSON (Bitwarden format) or logins as CSV after re-entering the master password; 1Password `.1pux` files can be imported. Security check lists duplicate logins and cards and can move extra copies to Trash. Long-press the app icon or use the Quick Settings tile "Lock PassVault" to lock instantly.
- **Languages:** Settings → Appearance → Language (System / English / हिन्दी). Screen text is translated through `ui/Strings.kt` and `ui/HindiStrings.kt`; anything without a translation stays in English. To add another language, add a map like `HindiStrings` and an entry in `AppLanguage`.
- **Fingerprint check (optional):** Settings → Security can ask for your fingerprint before opening a login or card or copying a password, even while unlocked (valid 30 s). Autofill is not affected.
- **Crash reports without tracking:** a crash saves a short local report (no messages, no vault data); you choose whether to share it from Settings → About. No analytics or network reporting.
- **Tappable share links:** with `share.host` set (see docs/RELEASE.md) shared items are normal web links that WhatsApp makes tappable; the receiver also gets the link picked up from the clipboard.
- **Site icons, optional name:** each login shows a brand-coloured badge (Amazon, Facebook, ...) computed offline; real favicons are an opt-in switch in Settings -> Appearance. The Name field is optional: the title is derived from the website.
- **Redesign:** new colour theme, shared components, top bars with icons, cards, tidier forms.

## Build and run (Ubuntu)

```bash
./gradlew :crypto:test            # 72 JVM unit tests
./gradlew :app:installDebug       # build and install on the connected phone/emulator
~/Android/Sdk/platform-tools/adb shell am start -n com.ritvik.passvault/.MainActivity
```

Start from scratch (see onboarding again): `adb shell pm clear com.ritvik.passvault`

Needs JDK 17+, the Android SDK with platform 35, and `local.properties` containing `sdk.dir=/path/to/Sdk`.

## What to try (step 3)

1. **Settings** (top right of the list): choose the auto-lock time and clipboard-clear time.
2. **Change master password**: lock and unlock with the new one; items are still there.
3. **Check your recovery key**: type the saved key; it should say it is correct.
4. **Wrong-password lockout**: type a wrong password 5 times on the lock screen; a 30-second wait starts
   and grows with each further mistake. A correct password (or fingerprint) resets it.
5. **Backup**: Settings > Export encrypted backup, save the .json file somewhere.
6. **Restore**: run `adb shell pm clear com.ritvik.passvault`, then on the first screen tap
   "Restore from backup" and pick that file. Unlock with the master password that was current when you
   exported (or use "Forgot password? Use recovery key").

## Layout

```
crypto/   Pure Kotlin/JVM, fully unit-tested without a device.
  VaultCrypto    key hierarchy: master password -> wrap key -> vault key -> items
  AesGcm, Hkdf, Kdf, RecoveryKey, PasswordPolicy, PasswordGenerator, AttemptLimiter
app/
  Argon2KeyDeriver      Argon2id via Argon2Kt (native)
  VaultHeaderStore      atomic write of vault_header.json
  VaultSession          unlocked vault key in memory (observable), wiped on lock
  BiometricVault        Keystore key + encrypted vault key for fingerprint unlock
  AppSettings           auto-lock and clipboard times; PrefsAttemptStore for the lockout counter
  items/                VaultItem, ItemRepository (one encrypted file per item)
  backup/               BackupCodec (header + encrypted item files in one JSON file)
  onboarding/ unlock/ vault/ settings/   screens and view models
  ClipboardHelper       sensitive copy + auto-clear
  MainActivity          FLAG_SECURE, auto-lock, screen-off lock
```

## Security design (summary)

- The vault key is 256 random bits. Every item is encrypted with it (AES-256-GCM), with the item id
  bound as associated data so files cannot be swapped between items.
- The vault key is stored twice, wrapped by a key from the master password (Argon2id, 64 MiB, 3 passes)
  and by a key from the recovery key. Changing the master password only re-wraps this one key.
- KDF parameters are saved in the header and checked against minimums on load (no downgrade).
- Wrong passwords: free for 4 tries, then 30 s, 1 min, 5 min, 15 min, 1 hour.
- A backup is the header plus the items exactly as stored (still encrypted); it needs the master password
  from backup time or the recovery key. No extra crypto was added for it.
- Fingerprint unlock: the vault key is encrypted with a Keystore AES key that needs a fresh
  BIOMETRIC_STRONG authentication for every use and is invalidated when fingerprints change.
- No Android backups (`allowBackup=false` plus extraction rules); `FLAG_SECURE` blocks screenshots when you switch "Allow screenshots" off (it is on by default);
  the decrypted item list is cleared from memory on lock.

## Offline only

PassVault has no server and no account. Your vault never leaves the phone except in a backup file you export yourself. To use it on another phone, export an encrypted backup (Settings -> Backup & import) and restore it on the new phone, or share single items with "Share securely".

Settings is a menu: Security, Autofill, Backup & import, Appearance, About.

## Autofill

- Settings -> Autofill -> "Turn on autofill", then choose PassVault in Android's list. In Chrome also switch on
  Settings -> Autofill services -> "Autofill using another service".
- Tap a username/password box in another app or site: vault locked -> "Unlock PassVault" (fingerprint or master
  password, then pick a login); unlocked and the site matches a saved login -> that login is offered directly;
  otherwise "Choose from PassVault" (you pick the login yourself).
- Phishing protection: a login is only offered automatically for a site whose registrable domain equals the one
  saved in the item's "website" field (github.com.evil.com and notgithub.com do not match). Apps and unknown
  sites always need you to pick the login on the PassVault screen. Fill the item's website field for best results.
- After you sign in somewhere, Android offers "Save to PassVault?" (only while the vault is unlocked).
- If PassVault's unlock screen is what unlocked the vault, it locks again when you leave that screen.

## Import from other password managers

- Settings -> "Import passwords" -> choose the CSV file exported from Chrome/Edge/Brave, Firefox, Bitwarden,
  1Password, LastPass or any tool whose CSV has a "password" column (columns are recognised by header name).
- Notes, cards and identities from Bitwarden are skipped (logins only); rows without a password are skipped;
  logins already in the vault (same site, username, password) are not added twice; max 5000 rows per file.
- The CSV is plain text. Delete it (and empty the trash / downloads) right after importing.

## Release

See `docs/RELEASE.md` (signing, building the app bundle, Play Console), `docs/PRIVACY_POLICY.md` (fill in the
placeholders), and `docs/SECURITY.md` (brief for an independent reviewer). Icon: adaptive vector icon in `res/`;
512 px Play Store icon in `store/`.

## Known limitations

- Autofill depends on each app/browser supporting Android's autofill framework; some apps and custom fields
  are not detected. Saving a login only works while the vault is unlocked.

- Kotlin `String` cannot be wiped, so decrypted passwords live in memory until garbage collected.
- The lockout uses the phone's clock, so someone who can change the date could shorten a wait
  (the wait is capped at 1 hour so a wrong date can never lock the owner out for long).
- A backup opens with the master password that was current when it was made. If you change the
  password later, export a new backup.
- Restore only works on a fresh install (it never overwrites an existing vault).
- Backup, restore, settings and the Android UI have no automated tests yet; only the `crypto` module does
  (198 tests).
- Import is login-only (no cards, identities or attachments), and there is no export to other managers.
- The strength check is an estimate, not a dictionary-based one; the breach check needs internet and is
  the only feature that uses the network (HTTPS to api.pwnedpasswords.com, 5-character hash prefixes only).
- Get an independent security review before releasing to real users.
