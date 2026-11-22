# PassVault Privacy Policy

**Effective date:** 16 November 2026
**Developer:** [YOUR NAME OR COMPANY]
**Contact:** [YOUR CONTACT E-MAIL]

PassVault is a password manager. This policy explains what happens to your information. The short version:
**your passwords are encrypted on your phone with a key that only your master password can unlock. We cannot read them, and we
have no way to recover them if you lose both your master password and your recovery key.**

## 1. What stays on your phone

Everything you save (logins, notes, settings) is stored on your phone, encrypted with AES-256-GCM. The encryption key is
protected by your master password (processed with Argon2id) and by your recovery key. Your master password and recovery key
are never stored in readable form and are never sent anywhere.

PassVault has no server and no account, so **no personal data leaves your phone**, with the one optional
exception in section 3.

## 2. No account and no server

PassVault does not ask for an e-mail address or create an account, and there is no PassVault server. Your encrypted vault exists only on your phone and in backup files you choose to export.

## 3. Optional breach check

If you tap "Check for data breaches", the app checks your passwords against the Have I Been Pwned "Pwned Passwords" service
(api.pwnedpasswords.com). For each password only the **first 5 characters of its SHA-1 hash** are sent; the full password and
the full hash never leave your phone, and the service cannot tell which password you checked. This runs only when you tap the
button.

## 3a. Optional website icons

By default each login shows a coloured badge with the site's initial, drawn on your phone; nothing is sent anywhere. If you turn on
Settings -> Appearance -> "Show real website icons", the app requests each site's icon from DuckDuckGo's icon service
(icons.duckduckgo.com) over HTTPS. Only the **site's domain** (for example amazon.com) is sent, never usernames, passwords or notes.
Icons are cached on your phone and are deleted when you turn the switch off. DuckDuckGo may see your IP address and the domains
requested, as with any web request. This is off unless you turn it on.

## 3b. Optional 2FA QR scanner

If you tap "Scan QR code" when adding a two-factor secret, the scan is done by Google's code scanner, which is part of Google Play
services and runs outside PassVault. PassVault itself has no camera permission and never sees camera images; it only receives the
text of the QR code you scanned. The 2FA secret is stored encrypted in your vault like every other field. You can also paste the
setup key instead and never use the scanner.

## 3c. Optional sharing of one item

"Share securely" turns one item into an encrypted link or QR code. The item is encrypted on your phone with a passphrase you choose or PassVault generates, and the link itself holds the encrypted data. **Nothing is uploaded to us or to any server.** What you do with the link (a message app, a QR code on your screen) is up to you, and anyone who has both the link and the passphrase can read the item. PassVault keeps no list of what you shared and cannot take a link back.

## 4. Autofill, clipboard and screen

- **Autofill:** when you use PassVault as your Android autofill service, the app reads the structure of the screen you are
  filling in (for example the website name and the position of login boxes) to find a matching login. This is processed on your
  phone and is not sent anywhere.
- **Clipboard:** copied passwords are marked as sensitive and cleared automatically after the time you choose.
- **Screenshots:** screenshots and screen recording of the app are blocked by default.
- **Biometrics:** fingerprint or face unlock is handled by Android. PassVault never receives your biometric data.

## 5. What we do not do

We do not use advertising, analytics or tracking software, we do not sell or share your data, and the app contains no
third-party trackers.

If the app crashes it saves a short report on your phone (app version, phone model, Android version and which part of
the app failed, with no passwords, messages or vault contents). It is never sent automatically; you can share or delete it in
Settings → About.

## 6. Backups and files you create

When you export a backup, the file is encrypted and is saved where you choose; keeping it safe is up to you. When you import a
CSV file from another password manager, that file contains passwords in plain text. Delete it after importing.

## 7. Your choices and deleting your data

- **Delete data on your phone:** uninstall the app or clear its storage.
- **Questions or requests about your data:** contact us at the address above.

## 8. Children

PassVault is not directed at children under 13 (or the minimum age in your country), and we do not knowingly collect their
information.

## 9. Changes

If this policy changes, we will update the date at the top and, for significant changes, tell you in the app.

## 10. Contact

[YOUR CONTACT E-MAIL]
