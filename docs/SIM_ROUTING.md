# SIM reception by manager

Status: personal SMS reception implemented; per-SIM voice routing is **not** implemented or advertised as working.

## SMS

- Android reads the **gateway's** inventory through authenticated `GET /api/v1/routing/subscriptions`, not the manager phone's local subscriptions.
- Identity is gateway API device UUID + Android subscription ID. Slot index is a display label, not a routing identity.
- `PUT` to the same endpoint saves only the authenticated user's selection; manager/admin roles are accepted. The request needs `If-Match: "revision"`, `device_id`, and an explicit `sms_subscription_ids` array. Stale writes return 412, inactive SIMs and gateway changes return 409. User IDs cannot be supplied by the client.
- No saved preference preserves existing shared reception. A saved empty array disables SMS reception and notifications. Multiple managers may choose the same SIM.
- The durable mirror and outbox are filtered server-side, before pagination. Own outgoing SMS remains visible even when the sender does not receive that SIM. Unknown SIM/gateway identities are excluded for explicitly configured reception. The raw events endpoint is unavailable to configured users so it cannot bypass filtering.
- These are personal delivery preferences, **not an authorization boundary**: operators can subscribe to any active SIM in this installation. Administrative per-line access grants remain separate future work.
- Outgoing SMS uses a separate explicit active SIM choice. Two active SIMs never silently select the first. The UI labels the source SIM in each message.
- The mirror binds to a single gateway UUID. A replacement phone is refused until an administrator explicitly migrates/archives the mirror and configures the new gateway. Android message IDs and event cursors must not collide across phones. This iteration is not a multi-phone SMS transport.
- Already queued unbound legacy SMS must be explicitly resent after module activation; they are never silently assigned to a SIM. A verified queued message fails if its gateway or subscription changes. Delivery still uses the existing local phone API; simultaneous physical USB gateway replacement during an in-flight HTTP send is not covered by an atomic identity-bound phone command protocol.
- The first identity scan reconciles existing messages against the current phone database. Legacy records that cannot be verified remain unknown and are excluded from personal SIM selection. No old SMS are deleted by selection.
- Preferences live at `PHONE_GATEWAY_SIM_PREFERENCES_FILE` (default `config/sim-preferences.json`), private atomic files with per-user revision numbers. Mirror/outbox data remains private.

## Voice: required next stage

The current `chan_mobile` connection exposes one Bluetooth phone, not two independently identified SIM trunks. Native and browser clients currently have shared managed SIP profiles. Consequently, current voice calls remain in the shared queue and the SIM screen explicitly says so. `receive_calls:true` is rejected rather than accepted as a no-op.

Per-SIM voice distribution needs all of:

1. Trusted gateway call metadata carrying the actual receiving SIM. Reading the SIM inventory is not call identification. A companion Android call-state/dialer adapter needs separate capability and real-device verification; Android permissions/role requirements must be confirmed before changing the gateway phone's default dialer.
2. Unique authenticated SIP endpoints per manager/device, not an arbitrary client-supplied extension or a shared SIP password.
3. Server-side SIM → operator queue membership, conservative handling of unknown SIMs, queue priorities and monitored transfer/conference behavior.
4. Two-SIM incoming/outgoing and busy-line tests on the real phone. Do not promise concurrent cellular calls based on the number of installed SIMs.

No Asterisk channels, dialer role, SIM defaults or voice queue configuration are changed by this SMS iteration.

## Verification and rollout

- `go test ./...`, `go test -race ./...`, `go vet ./...`.
- `ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk21 ./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Read-only live check: `HIRO_TEST_SMS_API_URL=http://127.0.0.1:28081/api/v1 HIRO_TEST_SMS_TOKEN_FILE=/private/path go test ./internal/server -run TestSIMLiveGatewayInventory -v`. This uses only gateway device/SIM GETs, isolated test auth and temporary preferences; it reads no SMS content and sends nothing.
- Upgrade Android operators before enforcing explicit outgoing-SIM selection on the live server. Old clients do not send subscription IDs on dual-SIM phones and will receive a selection-required error. A debug APK is not a signed update for the existing stable installation; do not uninstall the stable app to work around signing.
- Real-device UI and real SMS receipt/send validation remain separate from compilation and unit tests; do not send paid SMS to real customers as a smoke test.
