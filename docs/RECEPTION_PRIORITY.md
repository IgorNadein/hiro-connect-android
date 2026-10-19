# Personal call reception preferences

Settings → My call priority is separate from SMS subscriptions, shared-client
queue priority, and outgoing line selection. It edits ONLY the authenticated
user's reception preferences; no owner ID, SIP address or custom dial string is
sent. It uses the normal account-scoped authenticatedRequest/refresh flow.

The three-step editor supports line, verified voice SIM, device, gateway type,
and global preference, optionally for a specific canonical caller number.
Unsupported SIM selection is disabled, not guessed from the Android slot or SMS
subscription ID. Empty preferences mean equal default priority 0. An explicit
zero is NOT an opt-out. Values replace broader personal values, not sum them.

GET/PUT `/api/v1/routing/reception` use per-user revisions and If-Match. Writes
require an explicit array, including [] for a reset. Stable scope+caller keys
prevent a reload from editing a different row or recreating a removed rule.
Unsaved drafts, errors and account changes are guarded; a conflict reload does
not overwrite the draft. Other preferences survive one-rule edit/deletion.

POST `/api/v1/routing/reception/preview` returns the server's OWN effective
preference. This is not an availability probe, actual call, manager roster or
private-owner lookup. The app does not invent colleagues or an answer winner.

The server explicitly returns applied_to_live_calls=false while individual
authenticated SIP endpoints and the live PBX executor are not integrated.
Saving must NOT display “priority active”. Shared endpoints 1001/2001 cannot
identify individual managers. Existing real calls and SMS behavior are unchanged.

UI work uses native Compose, not a WebView: common content-height headers,
grouped settings rows, compact blue navigation, accessible search fields,
typography and surfaces follow the approved reference. SIP/audio/notifications
stay in the existing native service. Native UI QA is run separately with fake
repositories; no actual contacts, SMS or external calls are used for testing.

## Reproduce native UI checks

Run `./gradlew -I tools/ui-smoke/run-ui.init.gradle :app:testDebugUnitTest
--tests ru.hiro.manager.NativePrioritySmokeTest --tests
ru.hiro.manager.NativeWorkspaceSmokeTest` as one command. The opt-in runner adds
Robolectric and test-only debug dependencies, uses a plain Android Application,
and writes actual Compose screenshots under `build/ui-smoke/`. It tests save,
conflict/draft retention, unsupported SIM, server-owned preview, narrow dark
screens and 150% text scaling, in addition to role-gated settings navigation.
The release workflow runs these checks before publishing an APK. Normal unit
tests do not require the opt-in UI runtime. Build without the init script to
restore ordinary debug outputs; no UI-test manifest/runtime enters the release.
