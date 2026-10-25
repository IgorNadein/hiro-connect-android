# Android updates through GitHub

HI-RO Connect checks the latest stable release of the public repository
<https://github.com/IgorNadein/hiro-connect-android>. The update screen is under
**About → Updates**.

The updater accepts only a single `HI-RO-Connect-*.apk` asset from this
repository. Before Android opens its installer, the app verifies the announced
version code, file size, optional GitHub SHA-256 digest, application package,
newer version code, and signing certificate. Partial or mismatched downloads are
removed and never offered for installation.

The app does not send account data, messages, contacts, or server credentials to
GitHub. Its update request contains only the normal HTTP metadata and the app
user-agent.

## Publishing a release

`.github/workflows/android-release.yml` builds and publishes a signed APK after
Android changes reach `main`, and it can also be started manually. The workflow
expects these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The release description must contain the numeric line `Version code: N`; the
workflow writes it automatically. Keep an offline backup of the signing key. A
new signing key cannot update installations signed with the previous key.

The first switch from a locally installed debug build to the official release
requires one reinstall because Android does not permit changing an existing
application's signing certificate. All subsequent official releases update in
place.

The official application ID is `ru.hiro.manager`. The SHA-256 digest of its
release signing certificate is
`0e22bda4393b0fe6a09c786ba6078aa98d25cd4646939b5c12189126b4bc5266`; the release
workflow refuses to publish an APK signed by another certificate.

If Android reports that the APK conflicts with an existing package, remove the
old local/debug HI-RO Connect installation from every Android profile (including
work profiles, secondary users, and Samsung Secure Folder) before installing the
stable release. Android cannot replace an application with an APK carrying the
same application ID and a different certificate. This one-time removal clears
that installation's local data, but does not delete server-side calls, messages,
or contacts. Do not work around the conflict by changing the official application
ID or by publishing a debug-signed APK; either option would split the update line.

Install only the APK attached to a stable GitHub Release. Files produced by a
local build without the release secrets are either debug-signed or unsigned and
are not interchangeable with the stable application. The manager APK deliberately
does not request local SMS/MMS or system-overlay permissions: messages are handled
by the authenticated server API, while boot recovery uses Android's boot receiver
and foreground-service mechanisms.
