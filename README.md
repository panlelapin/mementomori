# Memento Mori for Android 14+

In the app, you choose a future date and a font color.

Then in the widget will be displayed, in that color, the number of
- full years
- full months
- full weeks

between the current day and the target date.


Vibecoded locally with Devstral Small 2 24B Q4 on a Macbook M4 ???

No compilation is done locally, only through GH Actions.

AGENTS.md and SKILL.md are provided.

## Development

The Android code is under `app/src/main/`. `CountdownCalculator` computes independent whole
calendar intervals; `WidgetSettingsStore` persists only the date and the two opaque colors.
`BasicWidgetReceiver` handles events, `BasicWidgetRenderer` selects the host sizes, and
`WidgetBitmapRenderer` draws the bundled Noto Mono font into transparent bitmaps.
The settings screen uses Material 3 and follows the system appearance.

Run `scripts/check-local` for the complete local gate (JDK 17, Android SDK 36, Python 3 with
PyYAML, ShellCheck, ripgrep, GnuPG and Git are required). It records stdout, stderr and the result in
the ignored `check-local.log`. Local validation includes JVM and native-graphics Robolectric
tests, but never builds an APK. See [the test matrix](docs/TESTING.md).

After a successful matching check, `scripts/make-remote` commits, pushes, dispatches the
manual release workflow and verifies the downloaded APK. `--resume RUN_ID` resumes a
successful build of the exact current commit with a clean checkout. All release assemblies
depend on the quality gate, including the script regression tests.
The Android setup step explicitly requests `platform-tools`, not the retired SDK package `tools`.

`scripts/check-github-stuff` checks or configures the GitHub repository/origin. It is **not a
purge script**: it deletes neither releases, Actions runs nor artifacts. It asks before
creating a repository or changing an existing origin. No GitHub purge script is provided.

## Resume on another computer

After cloning, run `scripts/restore-workspace` in your own terminal. It diagnoses the
machine, optionally guides GitHub login, restores the encrypted backup only with your
confirmation, and verifies the original signing certificate. It separately asks before
local checks and remote delivery. `scripts/restore-workspace --diagnose` does not restore
or change anything. See the [complete recovery guide](docs/RESTORE_WORKSPACE.md).
It never installs system tools, overwrites private files or asks an agent for your passphrase.

Clone this repository: its source, Gradle wrapper, scripts, documentation and complete
`skill/make-android-widget/` are versioned together. Codex discovers that same skill through
the relative link `.agents/skills/make-android-widget`; no private copy in a user directory
is needed. Read `AGENTS.md` before making changes. See [Codex skill discovery](https://learn.chatgpt.com/docs/build-skills).

The new computer still needs the development tools listed above, Android SDK 36, GitHub
authentication (`gh auth login`) and Codex if used. These tools/credentials are not supplied
by a Git clone. Local caches, build output and `check-local.log` are disposable. Signing
for CI uses the existing GitHub secrets; a local signing key is not required to build remotely.
An encrypted backup and its recovery passphrase are needed only to recover the signing
identity or private transcript. Never rely on a GitHub Actions artifact's temporary retention
as the only backup.

## Distribution signing

The workflow uses the GitHub secrets `RELEASE_KEYSTORE` (base64 PKCS12) and
`RELEASE_STORE_PASSWORD`, with alias `distribution`. The public SHA-256 certificate fingerprint
is tracked in `config/release-certificate.sha256`; APK verification rejects any other signer.
The local originals were removed after verification and explicit authorization; the encrypted
backup is in `config/signing-backup.tar.gpg`. Recover them only when needed into the ignored,
private `.signing/` directory. Future updates need the same signing identity.
To create a new encrypted backup when originals are available,
run from your own interactive terminal:

```sh
scripts/backup-signing-key .signing config/signing-backup.tar.gpg
```

GnuPG asks for a new, unique passphrase (use at least seven randomly selected words), then
asks again to verify recovery. The encrypted archive contains both `release.p12` and its
existing `password`; only the new recovery passphrase needs to be remembered separately.
The script never uploads anything, overwrites a backup or removes the originals. Only the
verified `.tar.gpg` file may be published. The repository is public: anyone can download it
and attempt offline password guessing. Losing the passphrase makes this backup unusable;
losing the repository also loses the backup unless another encrypted copy exists.

After publication, download the encrypted file again, compare its SHA-256 and verify its
decryption before considering any local deletion. Never commit `.signing/`, its password,
an unencrypted archive, or the recovery passphrase. The CI secrets remain unchanged.
See [recovery instructions](docs/SIGNING_BACKUP.md).

To include a previously exported conversation from the ignored `.signing/` directory in the
same encrypted backup, pass its simple filename as an additional argument:

```sh
scripts/backup-signing-key .signing config/signing-backup.tar.gpg conversation.json
```

This keeps a single recovery passphrase for the key, its password and the conversation.
`scripts/export-conversation` creates a text-only transcript from one explicitly selected
local Codex session, refusing other projects and Git-visible plaintext destinations. It
excludes internal instructions, reasoning, tool calls/results and injected environment context.
Only locally available messages are recoverable; this is not an importable Codex session.

The previous 1.0 APK used a development key. The first installation signed with the new
distribution key requires uninstalling that version. The current ADB handoff deliberately
uninstalls before installing: **this removes saved settings and placed widget instances**.
The script verifies the installed APK's exact checksum, not its visual behavior.

## Known limits

The 01:00 alarm is inexact and Android may delay it. A stopped application process cannot
immediately redraw its bitmap on every system theme change; the next widget event or tap
reads the current theme. Launcher icon caching and actual home-screen behavior still require
device checks. An expired target date falls back to 2040-01-01, or tomorrow after that date.
