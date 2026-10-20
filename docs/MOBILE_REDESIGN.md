# HI-RO Connect: task-based mobile interface

Update 0.7: shared server history, archive playback, notes and live participation
requests are now implemented. See [Shared calls](SHARED_CALLS.md) for permissions,
server requirements and remaining end-to-end acceptance checks. Attribution is
backend-only and is no longer part of any client screen.

The approved interactive concept is design version 5. This release maps existing
working functionality to four primary tabs: Calls, Messages, Clients, Settings.
The SIP/native core and its existing technical screens are retained; this is not
a replacement telephony engine. No demonstration people, simulated conversation
counts or fake connection-success states are included in the APK.

## Screen mapping

| Task | Application location |
| --- | --- |
| Shared calls and local history | Calls → Journal; device-only history is a separate action |
| Outgoing source and number | Calls → Dial number; explicit line selection applies to that call |
| In-call controls | Labeled microphone, speaker, hold, DTMF, transfer/details and hangup |
| Server SMS and source SIM | Messages; each thread requires a SIM choice when multiple exist |
| SIP chats | Messages → SIP chats icon |
| Shared clients / private book | Clients → Team clients / My contacts |
| Personal contact or selected phone import | Clients → Add personal contact / Select from phone |
| Explicit publication | Personal contact → Make shared client → Review → Confirm |
| External client with unknown origin | Clients → Add client from another source (admin) |
| Queue order / outgoing selection | Shared client → Priority and line selection |
| Effective priority explanation | Shared client → Priority → Check example |
| Personal SIM reception | Settings → Receive calls and SMS |
| Private contact synchronization | Settings → My data → Contacts and synchronization |
| Permissions and notification sound | Settings → Notifications and background operation |
| Appearance | Settings → Appearance; follows system or explicit light/dark choice |
| Voice-line inventory and common rules | Settings → Organization (admin) |
| Updates | Settings → Help → About application |

## Data and behavior

Private contact ownership is always derived by the server from authentication.
READ_CONTACTS alone is not upload consent. First-login onboarding and the contact
settings explain the destination and private visibility before enabling sync.
Sync operates while the authenticated application process is alive; there is no
promise of a persistent worker after Android kills the process. Stopping sync
does not erase the server book. Selected import is separate from full-book sync.

Client promotion publishes only the selected name, number and rules, after a
separate confirmation. Ordinary client edits do not resend an origin ID.
External creation sends explicit JSON null for the optional origin. Attribution
is not ownership, manager assignment or call destination. Demotion first saves a
private copy, then removes the shared client for everyone. Concurrent writes use
server revisions; failures do not silently discard the editor.

The priority preview matches the server's lexicographic ordering: specificity,
client override, exact direction, numeric value. Automatic outgoing selection
compares that whole score, not just the numeric value; ties use numeric line
codes. SIM matching requires a verified SIM. The preview is based on downloaded
configuration and enabled lines, not actual channel occupancy/provider health.

Tab changes warn about unsaved edits and SMS drafts. A retry of the same SMS
payload uses the same client-message ID. Server writes do not automatically retry
with changed payloads. User consent is not inferred from installing the update.

## Explicitly not shipped

The concept also includes a mobile AI
assistant, per-SIM voice routing, in-app provider provisioning, per-user routing
of private-contact matches, server-wide sessions management and scheduled quiet
hours. Those require additional server/device capabilities and are not simulated
as working features in this release. Gateway voice remains a shared flow; the
current Bluetooth adapter cannot identify an incoming voice SIM. Local history
is labeled as local and is separate from the server-wide journal.

## Verification

Build/test on the Kali development host without installing the manager APK on the
shared gateway phone. Unit tests cover contact serialization, consent account
keys, nullable attribution, gateway SIM models, rule scope/direction ordering,
verified SIM matching, numeric tie-breaking and tab classification. APK assembly
and lint are separate checks; document their actual results in the release task.
The GitHub release workflow signs with the existing key. Real-device UI, voice
and SMS end-to-end checks remain necessary before claiming full device coverage.
No production address book upload or real call/SMS is part of automated tests.
