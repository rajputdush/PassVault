# PassVault security overview (for reviewers)

A password manager must be reviewed by someone independent before real users trust it. This file is the brief to hand them.

## Design in one page

- **Vault key:** 256 random bits. Each item is AES-256-GCM encrypted with a fresh random 96-bit nonce; the item id is bound as
  associated data, so files cannot be swapped between items.
- **Key wrapping:** the vault key is stored twice in the vault header: wrapped by a key from the master password and by a key
  from the 256-bit recovery key. Changing the master password only re-wraps the vault key.
- **Master password -> key:** NFKC-normalised, then Argon2id (64 MiB, 3 iterations, parallelism 1), then HKDF-SHA256 with
  separate labels (`passvault/v1/hkdf/...`). Minimum parameters are enforced whenever a header is read (no downgrade by a
  tampered header or hostile server). PBKDF2-HMAC-SHA256 (600k) exists as a fallback.
- **Biometrics:** the vault key is encrypted by an Android Keystore AES key that requires BIOMETRIC_STRONG authentication for
  each use and is invalidated when fingerprints change.
- **Local protections:** wrong-password lockout (4 free tries, then 30 s ... 1 h, persisted), FLAG_SECURE, no Android backups,
  auto-lock on timer and screen-off, clipboard marked sensitive and auto-cleared.
- **Autofill:** logins are only offered directly for a site whose registrable domain equals the saved one; apps and
  unmatched sites require the user to pick on a PassVault screen.

## Known limitations (be honest with the reviewer)

- Kotlin/JVM `String` cannot be wiped, so decrypted fields live in memory until garbage collected.
- Lockout timing uses the monotonic clock (not the wall clock); after a reboot the wait restarts in full. Stored counters can still be reset by someone with root.
- A rooted phone or malware with accessibility/screen access while the vault is unlocked can read what is on screen.
- Domain matching uses a short built-in list of public suffixes, not the full Public Suffix List.
- No certificate pinning (standard HTTPS validation only).

## Suggested review scope

1. `crypto/src/main/kotlin/com/ritvik/passvault/crypto/` : AesGcm, Kdf, Hkdf, VaultCrypto, VaultHeader, RecoveryKey,
   AttemptLimiter. Check nonce handling, header validation, key wiping, error handling (no oracle between wrong password and
   corrupt data beyond what is intended).
2. `app/.../BiometricVault.kt`, `Biometrics.kt` : Keystore key properties, invalidation, fallback paths.
3. `app/.../autofill/` : domain matching (`DomainMatcher`), what is offered to which app/site, save flow.
4. `app/.../backup/BackupCodec.kt`, `importer/` : untrusted-file parsing.
5. Manifest, network security config, ProGuard rules.

The `crypto` module has 198 unit tests (`./gradlew :crypto:test`), including RFC 5869 HKDF vectors.

## Reporting a vulnerability

[YOUR SECURITY CONTACT E-MAIL]. Please do not publish details before a fix is available.


## Optional website icons

Off by default. When enabled, `sites/IconLoader.kt` requests `https://icons.duckduckgo.com/ip3/<domain>.ico` for a validated bare domain only (no path, user data or credentials), limits responses to 200 KB, decodes to at most 96 px, and caches under `cacheDir/site_icons` with SHA-256-hashed filenames. Turning the switch off clears the cache. Reviewers should check that nothing but the domain can reach the request.


## Item types, 2FA codes and app matching

- **Item types.** Logins, cards and secure notes share one encrypted item format (`items/VaultItem.kt`); the type is a field inside the encrypted JSON, so the server and sync layer cannot tell what kind of item a blob is. Older items without a type read as logins.
- **2FA (TOTP).** `crypto/.../totp` implements RFC 6238 (tested against the RFC vectors for SHA-1/256/512). The secret is stored inside the encrypted item and codes are computed on the phone. Scanning uses Google's Play-services code scanner, so PassVault holds no camera permission.
- **Cards.** Number, expiry, CVV and PIN are encrypted like any other field, hidden by default in the editor, and copied to the clipboard only on request (auto-cleared). The card number is never indexed for search. Autofill does not fill cards.
- **App matching.** Logins can be linked to an Android package name. Direct fill inside an app uses the package reported by the system (the activity's own package, not the page's view ids) and only offers logins linked to exactly that package. Known browsers are never matched by package: the website decides there (`autofill/AppIds.kt`, `SaveMatcher.forApp`). Reviewers should check this boundary.

## Recovery key replacement and the Emergency Kit

- `VaultCrypto.regenerateRecoveryKey` wraps the existing vault key under a fresh 256-bit recovery key; password wrapper, salt and items are untouched, and the old key stops working (tested). It needs the master password, is covered by the same attempt limiter as a password change, and with sync on it first replaces the stored header on the server (same endpoint as a password change, which also signs out other devices), so a failure leaves everything unchanged.
- The Emergency Kit PDF is built on the phone with Android's `PdfDocument` and written only where the user chooses (Storage Access Framework). It contains the key in the clear by design; the UI tells the user to print it and delete the file. The key is held in memory only while the "new recovery key" card is on screen.
- Custom fields live inside the encrypted item JSON; fuzzy search runs in memory on decrypted titles, usernames, URLs, folders and field *labels* (never passwords, card numbers or field values).

## Importing

Import files are parsed on the phone by `importer/` (CSV, and Bitwarden's unencrypted JSON via a small strict JSON reader with a nesting limit and a row limit). Nothing is uploaded. Imported secrets are encrypted like any other item as they are saved; the UI reminds the user to delete the plain-text export afterwards. Bitwarden's *encrypted* export is refused with instructions rather than guessed at. 2FA secrets that are not standard authenticator keys (for example Steam) are kept as hidden custom fields instead of being dropped.

## Sharing one item (no server involved)

Code: `crypto/.../share/ShareCodec.kt` (23 tests). A shared item is `PVS1.` + base64url(header | nonce | ciphertext).

- Header: version, KDF id, memory, iterations, parallelism, 16-byte random salt. The whole header is authenticated as AES-GCM associated data, so lowering the key-derivation cost inside a code makes it fail to open.
- Key: Argon2id (64 MiB, 3 passes; PBKDF2 accepted) from a passphrase (NFKC, trimmed, lower-cased), then HKDF with the separate label `passvault/v1/hkdf/share`. The passphrase is never part of the code. Generated passphrases are five EFF words (about 65 bits).
- Payload: gzip(JSON of the item + created/expires times), AES-256-GCM. Decompression is capped at 200 KB, and a code asking for more than 256 MiB or 16 Argon2 passes is refused before any work is done. Opening enforces the same minimum KDF strength as the vault.
- Limits to tell the reviewer: anyone with the code and the passphrase can read it; a code cannot be revoked; the expiry is enforced by the receiving app, so it guards against stale messages, not against a modified client. Offline guessing of a short, user-chosen passphrase is possible (the app requires at least 16 characters and offers a generated five-word one).
- Delivery: a `passvault://share/...` deep link (opens only a dialog asking for the passphrase and confirmation; nothing is saved automatically), a QR code (ZXing core, drawn on screen, no camera permission to show it), or pasted text. The dialogs set FLAG_SECURE on their own windows.

## Card autofill

Payment forms are recognised from Android autofill hints, HTML `autocomplete` values and, last, field names (`CardFieldClassifier`, tested). A form counts as a card form only with a card number, or a security code plus an expiry, so one-time-code boxes are not mistaken for cards. Cards are never put in the drop-down and never filled directly, even when the vault is unlocked: the user always opens the PassVault picker and taps a card. Card numbers, codes and PINs are not searched in the picker.
