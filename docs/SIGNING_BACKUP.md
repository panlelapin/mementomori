# Recoverable signing backup

The encrypted backup is **not created or published merely by adding these instructions**.
It requires the owner's interactive passphrase entry. Run from the repository root:

```sh
scripts/backup-signing-key .signing config/signing-backup.tar.gpg
```

Requirements: Bash, GnuPG with a working pinentry, GNU tar and coreutils. GnuPG handles
the hidden input; neither the agent nor a command argument receives the passphrase.
Use a unique passphrase of at least seven randomly selected words. A long quotation or
predictable sentence is not equivalent to random words. Do not send the passphrase in chat.

GnuPG is invoked with `--no-keyring`: no public `pubring.kbx` is needed for password-based
encryption. A missing public keyring must not prevent this backup.

This bundles the existing PKCS12 keystore and its existing password. It does not
create/rotate the signing identity, change CI secrets, push changes or remove originals.
The public fingerprint stays in `config/release-certificate.sha256`. Publication follows
the repository's normal delivery procedure, after a fresh successful local check.

## Include the conversation with the same recovery passphrase

The optional exporter reads one explicitly chosen local Codex JSONL session. It validates
the session's project, retains user/assistant text and excludes tools, internal instructions,
reasoning and injected environment context. It never copies the raw session or scans other
conversations. It refuses to overwrite a prior export or write plaintext that Git could track.

```sh
scripts/export-conversation /path/to/selected-session.jsonl .signing/conversation.json
scripts/backup-signing-key .signing config/signing-backup.tar.gpg conversation.json
```

Only the `.tar.gpg` belongs on GitHub. The ignored JSON remains confidential and local until
the encrypted download has been verified and deletion separately authorized. The transcript
is a snapshot of available text, without attachments; it is not a restorable Codex thread.
Future messages are not automatically added. For an updated export/backup, choose new filenames
and verify the new downloaded archive before retiring any previous backup.

## Verification after publication

Download `config/signing-backup.tar.gpg` from the published commit into a separate file.
Compare `sha256sum` of that download with the local encrypted backup. Verify decryption
and exact archive equality while the original files are still available:

```bash
set -o pipefail
gpg --no-options --no-keyring --no-symkey-cache --decrypt /path/to/downloaded-signing-backup.tar.gpg |
  cmp - <(tar -C .signing -cf - -- release.p12 password)
```

Success means exit status zero for the entire pipeline. A failed or cancelled decryption
must never be treated as successful. Do not delete local originals until the downloaded
backup passes this check and the owner explicitly authorizes deletion.
If the backup includes `conversation.json`, append that exact filename after `password` in
the `tar` command above. The file order and source metadata must match the creation command.

## Recovery after losing the originals

For a clone of this repository, prefer `scripts/restore-workspace`. It checks the
ciphertext checksum, validates the archive before extraction, verifies the recovered
private-key certificate and refuses overwrites. See [the recovery guide](RESTORE_WORKSPACE.md).
The manual procedure below is a fallback, not an equally comprehensive validator.

Download the encrypted backup and use a private directory outside the repository. For
example, in Bash (each command must succeed before continuing):

```bash
umask 077
recovery_dir=$(mktemp -d)
gpg --no-options --no-keyring --no-symkey-cache --output "$recovery_dir/backup.tar" \
  --decrypt /path/to/signing-backup.tar.gpg &&
tar -xf "$recovery_dir/backup.tar" -C "$recovery_dir" -- release.p12 password
```

GnuPG requests the recovery passphrase. After successful decryption/extraction, the directory
contains the original key and its original password. Its plaintext archive is also sensitive.
Never upload these recovered files or print the password in logs. Once recovery is verified,
remove the redundant plaintext archive from this exact recovery directory.
For a backup containing the transcript, add `conversation.json` to the explicit extraction
list after `password`.

Before using a recovered key, compare its certificate's SHA-256 with the trusted public
fingerprint (replace `/private/recovery` with that directory):

```sh
keytool -exportcert -keystore /private/recovery/release.p12 -storetype PKCS12 \
  -storepass:file /private/recovery/password -alias distribution | sha256sum
```

Use `set -o pipefail` in Bash so a failed `keytool` cannot appear successful. The password
is read from its file, not displayed or embedded in process arguments. Losing the recovery
passphrase cannot be repaired using the encrypted backup alone. Keep an additional copy
of the ciphertext if you need protection against losing access to GitHub.
