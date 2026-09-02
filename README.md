# SproutOS for Android

The client for SproutOS's own app catalogue. Not a "store" — SproutOS is the whole product, and the
app is one way into it.

Its canonical Android application ID is `com.sproutos.store`, registered in Play Console under the
friendly name **SproutOS**. The same ID is its namespace and stable self-update package identity.

## What it is for

Two tabs at the bottom:

- **Public** — apps anyone can install, from the shared catalogue.
- **Personal** — the customer's own things, in two sections: their **apps**, and their **websites**.
  Searchable across both, because someone who built a site and an app on SproutOS thinks of them as
  one project, not two catalogues.

An app starts in its owner's Personal catalogue. A platform-reviewed app can also be published to
the Public catalogue without changing the owner's Personal copy.

## SproutOS signs every app with its own key

Each Android project receives an independent signing key. Signed APKs are served from private
object storage behind expiring URLs. **Ur LLC is the developer of record for every app published
this way** — that is what lets a customer publish without their own Play Console account, a D-U-N-S
number, or the verification wait. One project's key never signs another project's APK.

It also means SproutOS is accountable for what is distributed under its name, so apps are reviewed
before publication and can be removed. That is not a formality: Google's current [Android developer
verification timeline](https://developer.android.com/developer-verification) starts regional
enforcement for participating stores in Brazil, Indonesia, Singapore, and Thailand on September 30,
2026, then expands globally to all apps on certified devices in 2027. SproutOS manages directly
distributed package names through Play Console and checks their status with the Android Developer
ID Status API.

## Why this client is distributed directly

The client installs apps and updates itself outside Google Play. Google's general [Policy
Coverage](https://support.google.com/googleplay/android-developer/answer/10146128) and [Device and
Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646) rules do not
allow that behavior in an ordinary Play listing. Google now has a separate US [Third-party App
Store on Play program](https://support.google.com/googleplay/android-developer/answer/17118006), but
SproutOS is not enrolled in it. The current client is downloaded directly from sproutos.me — the
F-Droid model, and the reason the website needs a download page.

Installing an app therefore needs `REQUEST_INSTALL_PACKAGES` and the user allowing installs from
this source. That is a real friction point and worth designing for rather than apologising about.

## Automatic updates

The two automatic-update switches are independent and can be changed at any time:

- **Update SproutOS automatically** checks the catalogue's `clientUpdate`.
- **Update installed apps automatically** checks public apps and the signed-in customer's personal
  apps, but never installs a catalogue app that is not already on the device.

The client schedules one unique WorkManager job approximately daily. It only runs on an unmetered
network while battery and storage are not low. Every candidate still passes the same package name,
version, signing-certificate, byte count, and SHA-256 checks as a foreground update. A missing app,
same or lower version, or different signer is refused.

Android 12 and later can sometimes update without showing a confirmation screen. SproutOS requests
that only for its own update, or an app for which Android records SproutOS as the installer or update
owner, and only when the APK meets that Android release's target-SDK floor. Android owns the final
decision and may still require confirmation. In that case SproutOS posts an actionable notification;
if notifications are unavailable, it abandons the waiting session and explains on the next app open
that the update must be retried manually. It never describes this feature as universally silent.

Foreground installs now use PackageInstaller sessions too. This lets a newly installed app record
SproutOS as its installer and, on Android 14+, request update ownership. An app originally installed
by the older client or another source may therefore require one confirmed update before later
updates become eligible for confirmation-free installation.

The platform behavior is documented by Android's
[`SessionParams.setRequireUserAction`](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)),
[`SessionParams.setRequestUpdateOwnership`](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequestUpdateOwnership(boolean)),
[`UPDATE_PACKAGES_WITHOUT_USER_ACTION`](https://developer.android.com/reference/android/Manifest.permission#UPDATE_PACKAGES_WITHOUT_USER_ACTION),
and [periodic WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
documentation.

## Status

The client implements the catalogue v2 contract, native PKCE sign-in, authenticated personal
catalogue refresh, verified PackageInstaller install/update sessions, unknown-source permission
guidance, scheduled app updates, and self-update when the platform publishes `clientUpdate`.

## Building

Needs a JDK 21 and the Android SDK (platform 35, build-tools 35).

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
gradle :app:testDebugUnitTest   # the catalogue contract, from this side
gradle :app:assembleDebug       # an installable, unsigned APK
gradle :app:connectedDebugAndroidTest # manifest/settings checks on a running emulator
```

Debug builds use the emulator host bridge (`10.0.2.2`) for the website and API. Release builds use
`https://sproutos.me` and `https://api.sproutos.me` and disable cleartext traffic.

The release build is deliberately **unsigned**. SproutOS signs every APK it distributes, including
this one, on a machine that is not a CI runner — see `docs/apk-signing.md` in the platform
repository. A signing config here would be a second key, in a place the first one is not.

## Handing a release to the on-prem signer

Never submit a locally selected APK or a pull-request build to the production signer. After a
release change is reviewed and merged, the successful **Android client** workflow on the resulting
`main` commit creates one immutable artifact named
`sproutos-android-unsigned-<40-character-commit>`. The release-handoff job independently checks the
expected package and version declared in the workflow, proves that the APK is still unsigned,
records its byte size and SHA-256 in `release-manifest.json` and `SHA256SUMS`, and publishes a GitHub
build-provenance attestation for the APK.

For every release, update the workflow's `EXPECTED_VERSION_CODE` and `EXPECTED_VERSION_NAME` in the
same reviewed change as `app/build.gradle.kts`. The two values intentionally live at separate
boundaries: a forgotten or unintended Android version change must stop the handoff job instead of
silently becoming eligible for production signing.

Choose the exact merged commit and its successful push workflow run, then download that run's
artifact rather than whichever run happens to be newest:

```bash
repository=MySproutOS/SproutOS-Android
revision=REPLACE_WITH_REVIEWED_40_CHARACTER_MAIN_COMMIT
run_id=$(gh run list --repo "$repository" --workflow ci.yml --commit "$revision" \
  --event push --status success --limit 1 --json databaseId --jq '.[0].databaseId')
test -n "$run_id"
run_json=$(gh run view "$run_id" --repo "$repository" \
  --json conclusion,event,headBranch,headSha)
jq -e --arg revision "$revision" '
  .conclusion == "success" and
  .event == "push" and
  .headBranch == "main" and
  .headSha == $revision
' <<<"$run_json"

handoff=$(mktemp -d /private/tmp/sproutos-android-handoff.XXXXXX)
gh run download "$run_id" --repo "$repository" \
  --name "sproutos-android-unsigned-$revision" --dir "$handoff"
(cd "$handoff" && shasum -a 256 -c SHA256SUMS)
jq -e --arg repository "$repository" --arg revision "$revision" '
  .schemaVersion == 1 and
  .sourceRepository == $repository and
  .sourceCommit == $revision and
  .packageName == "com.sproutos.store" and
  .signed == false and
  (.artifact | type == "string") and
  (.sha256 | test("^[0-9a-f]{64}$")) and
  (.sizeBytes | type == "number" and . > 0) and
  (.versionCode | type == "number" and . > 0) and
  (.versionName | type == "string" and length > 0)
' "$handoff/release-manifest.json"
apk=$(jq -r .artifact "$handoff/release-manifest.json")
gh attestation verify "$handoff/$apk" \
  --repo "$repository" \
  --signer-workflow "$repository/.github/workflows/ci.yml" \
  --source-ref refs/heads/main \
  --source-digest "$revision" \
  --deny-self-hosted-runners
```

The operator must compare `versionCode`, `versionName`, SHA-256, size, and source commit with the
reviewed release before running the platform's `queue-client-release` command. The GitHub artifact
is an unsigned custody handoff, not a customer download and not a GitHub Release. The only public
client APK is the version the on-prem signer verifies, signs with the existing `com.sproutos.store`
identity, and publishes through SproutOS's versioned artifact store.

## Sign-in

The system browser through Custom Tabs, never a WebView: a WebView in this app could read the
password as it is typed, which is why every OAuth guideline for native apps forbids one.

PKCE is not optional here. A public client cannot keep a secret — anything compiled into this APK
is readable by anyone who downloads it — so the authorization code is all that stands between an
attacker and a session, and PKCE binds it to a verifier that never leaves the device.

The callback arrives on a custom scheme, which any app can also claim. The `state` check is what
actually establishes that a redirect came from the flow this app started; Android delivering it
here proves nothing.

## What is tested and what is not

`CatalogueTest` covers the contract with the platform: what this build accepts, what it ignores, and
what it refuses. The platform's own tests cover what it emits. Both sides need one, because a rename
on either is a tab that shows nothing and explains nothing.

The unit suite covers the update-only decision and each documented Android target-SDK threshold in
addition to the catalogue and download contracts. The instrumentation suite verifies that the two
settings persist independently and that the updater permissions and private result receiver are in
the packaged manifest.

## Platform contract

- Browser authorization is `https://sproutos.me/oauth/authorize`; token exchange and catalogue are
  on `https://api.sproutos.me`.
- The first-party public client requests only `project:read`. Its seed must keep the exact
  `sproutos://auth/callback` redirect and that default scope.
- Catalogue version 2 carries authoritative Android app/project IDs, the immutable generated
  package name, monotonic version code, signed-object digest and size, and signing-certificate
  digest. The client refuses inconsistent metadata and verifies all of it again from the APK.
- `clientUpdate`, when present, comes from the DB-backed latest signed SproutOS client release.
