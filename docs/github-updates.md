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
