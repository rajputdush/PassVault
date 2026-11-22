# Release checklist

## 0. Before anything public

- [ ] Fill in the placeholders in `docs/PRIVACY_POLICY.md` and `docs/privacy-policy.html` (name, contact, log retention).
      Have it read by someone qualified for your country (this template is not legal advice).
- [ ] Host the policy at a public https URL (for example `https://digitalutilization.com/passvault/privacy`).
- [ ] Get an **independent security review** (give them `docs/SECURITY.md`). Do this before real users store real passwords.

## 1. Create the signing key (once)

```
keytool -genkeypair -v -keystore ~/passvault-release.jks -alias passvault -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties     # then edit the passwords and path
```

**Back up `passvault-release.jks` and its passwords somewhere safe (not in the project folder).** If you use Play App Signing
(recommended) Google holds the app signing key and this is only your upload key, which can be reset; without it, a lost key
means you can never update the app.

## 2. Build

```
./gradlew clean :crypto:test
./gradlew :app:assembleRelease      # apk: app/build/outputs/apk/release/
./gradlew :app:bundleRelease        # aab for Play: app/build/outputs/bundle/release/
```

Install the **release** APK on a real phone and test everything again: release builds shrink code (R8). Test: create vault, unlock, fingerprint, add/edit/delete, backup and
restore, autofill, import, security check. If anything crashes only in release, tell me the
error; the cause is usually a missing ProGuard keep rule in `app/proguard-rules.pro`.

For every update raise `versionCode` (a whole number, +1 each upload) and `versionName` in `app/build.gradle.kts`.

## 3. Google Play Console

1. Create a developer account (one-time fee). New personal accounts must run a **closed test** with a minimum number of
   testers for a minimum number of days before production (check Play Console for the current numbers).
2. Create the app, upload the `.aab`, enable Play App Signing.
3. Store listing: name, short and full description, `store/play-store-icon-512.png`, a 1024x500 feature graphic, phone screenshots.
   (The app blocks screenshots with FLAG_SECURE; for store screenshots temporarily remove that flag in a debug build, or use
   an emulator build without it, and never ship that build.)
4. App content: privacy policy URL, ads: none, target audience 18+ (not for children), content rating questionnaire.
5. **Data safety form** (check Play's current definitions as you fill it in):
   - Data shared with third parties: none.
   - Data collected: none by PassVault. The vault stays on the phone; the breach check sends only 5-character hash prefixes when the user taps it. Read Play's current definitions as you fill the form.
   - Data encrypted in transit: yes. Deletion: nothing is stored on a server; uninstalling removes the data.
   - The breach check sends 5-character hash prefixes to Have I Been Pwned only when the user taps the button.
   - The optional QR scanner is Google's code scanner in Play services (no camera permission in PassVault). 2FA secrets, cards and notes are stored only in the encrypted vault.
   - Optional website icons (off by default) send only a site's domain to DuckDuckGo's icon service when the user turns them on.
6. Declare the autofill service honestly in the description ("fills logins into other apps and websites").
7. Roll out to closed testing first, then production.

## 4. After release

- Watch crash reports in Play Console (Android vitals). Upload the R8 `mapping.txt` (`app/build/outputs/mapping/release/`) with each release so
  the stack traces are readable. Reports users share from Settings → About are also obfuscated in release builds; decode them with the same file.
- Keep dependencies updated (Compose BOM, androidx, Argon2Kt, ).
- Re-test autofill after every Android release (autofill behaviour changes between versions).

## Tappable share links (WhatsApp, Telegram, SMS)

Chat apps do not make `passvault://` links tappable. To send a normal web link instead:

1. Upload `docs/share/index.html` to your website so it opens at, for example, `https://digitalutilization.com/passvault/share/`
   (nothing else is needed; the page is static and never sees the encrypted item, which sits after the `#`).
2. In `local.properties` add `share.host=digitalutilization.com` (and `share.path=/passvault/share` if you used a different folder), then rebuild.
3. Shared links, and the QR code, are now `https://…/passvault/share/#PVS1…`. Tapping one opens the page, which opens PassVault.
4. Optional, so Android opens PassVault directly without the page: publish `https://<host>/.well-known/assetlinks.json` with your app's
   package name `com.ritvik.passvault` and the SHA-256 fingerprint of your signing certificate (Play Console → App signing).

Without `share.host`, links stay `passvault://share/…`; the receiver then pastes the message into **+ → Receive a shared item**
(PassVault also picks up a copied link by itself).
