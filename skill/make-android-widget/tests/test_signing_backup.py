"""Exercise the backup with real GnuPG and disposable data, never the distribution key."""

import os
from pathlib import Path
import pty
import shutil
import subprocess
import tarfile
import tempfile
import io
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/backup-signing-key"


class SigningBackupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "private"
        self.source.mkdir(mode=0o700)
        (self.source / "release.p12").write_bytes(b"disposable keystore fixture\x00\xff")
        (self.source / "password").write_bytes(b"disposable keystore password\n")
        self.output = self.root / "backup.tar.gpg"
        self.gpg_home = self.root / "gnupg"
        self.gpg_home.mkdir(mode=0o700)
        self.addCleanup(self.stop_agent)
        self.phrase = self.root / "phrase"
        self.phrase.write_text("public test phrase, never a real signing secret\n")
        self.wrong_phrase = self.root / "wrong-phrase"
        self.wrong_phrase.write_text("a different public fixture phrase\n")
        self.real_gpg = shutil.which("gpg")
        self.assertIsNotNone(self.real_gpg, "GnuPG is required for signing backup tests")
        commands = self.root / "bin"
        commands.mkdir()
        wrapper = commands / "gpg"
        wrapper.write_text('''#!/bin/sh
case " $* " in
  *" --decrypt "*)
    case "$BACKUP_TEST_MODE" in
      wrong) BACKUP_TEST_PHRASE=$BACKUP_TEST_WRONG;;
      cancel) exit 130;;
      changed) printf changed >> "$BACKUP_TEST_SOURCE/password";;
    esac;;
esac
exec "$BACKUP_TEST_GPG" --batch \\
  --pinentry-mode loopback --passphrase-file "$BACKUP_TEST_PHRASE" "$@"
''')
        wrapper.chmod(0o700)
        self.env = dict(
            os.environ,
            PATH=str(commands) + os.pathsep + os.environ["PATH"],
            BACKUP_TEST_GPG=self.real_gpg,
            GNUPGHOME=str(self.gpg_home),
            BACKUP_TEST_PHRASE=str(self.phrase),
            BACKUP_TEST_WRONG=str(self.wrong_phrase),
            BACKUP_TEST_SOURCE=str(self.source),
            BACKUP_TEST_MODE="ok",
        )

    def stop_agent(self):
        subprocess.run(
            ["gpgconf", "--homedir", str(self.gpg_home), "--kill", "gpg-agent"],
            check=True, capture_output=True,
        )

    def run_backup(self, mode="ok", extras=()):
        master, slave = pty.openpty()
        try:
            return subprocess.run(
                [str(SCRIPT), str(self.source), str(self.output), *extras],
                stdin=slave, stderr=slave, stdout=subprocess.PIPE,
                env=dict(self.env, BACKUP_TEST_MODE=mode), timeout=30,
            )
        finally:
            os.close(slave)
            os.close(master)

    def test_real_encryption_restores_exactly_two_files_without_exposing_plaintext(self):
        result = self.run_backup()
        self.assertEqual(0, result.returncode)
        self.assertFalse((self.gpg_home / "pubring.kbx").exists())
        encrypted = self.output.read_bytes()
        self.assertEqual(0o600, self.output.stat().st_mode & 0o777)
        for name in ("release.p12", "password"):
            self.assertNotIn((self.source / name).read_bytes(), encrypted + result.stdout)
        restored = subprocess.check_output([
            self.real_gpg, "--no-options", "--homedir", str(self.gpg_home), "--batch",
            "--pinentry-mode", "loopback", "--passphrase-file", str(self.phrase),
            "--decrypt", str(self.output),
        ], stderr=subprocess.PIPE)
        with tarfile.open(fileobj=io.BytesIO(restored)) as archive:
            self.assertEqual(["release.p12", "password"], archive.getnames())
            for name in archive.getnames():
                self.assertEqual((self.source / name).read_bytes(), archive.extractfile(name).read())
        self.assertFalse(list(self.root.glob("*.tmp.*")))

    def test_wrong_phrase_cancellation_or_changed_source_never_publish_a_backup(self):
        for mode in ("wrong", "cancel", "changed"):
            with self.subTest(mode=mode):
                result = self.run_backup(mode)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(self.output.exists())
                self.assertTrue((self.source / "release.p12").exists())
                self.assertTrue((self.source / "password").exists())
                self.assertFalse(list(self.root.glob("*.tmp.*")))

    def test_explicit_conversation_is_included_but_paths_outside_source_are_rejected(self):
        (self.source / "conversation.json").write_text('{"messages": ["private fixture"]}')
        self.assertNotEqual(0, self.run_backup(extras=("../phrase",)).returncode)
        self.assertFalse(self.output.exists())
        result = self.run_backup(extras=("conversation.json",))
        self.assertEqual(0, result.returncode)
        restored = subprocess.check_output([
            self.real_gpg, "--no-options", "--no-keyring", "--homedir", str(self.gpg_home),
            "--batch", "--pinentry-mode", "loopback", "--passphrase-file", str(self.phrase),
            "--decrypt", str(self.output),
        ], stderr=subprocess.PIPE)
        with tarfile.open(fileobj=io.BytesIO(restored)) as archive:
            self.assertEqual(["release.p12", "password", "conversation.json"], archive.getnames())
            self.assertEqual((self.source / "conversation.json").read_bytes(),
                             archive.extractfile("conversation.json").read())

    def test_existing_backup_and_symlinked_input_are_rejected(self):
        self.output.write_bytes(b"existing backup")
        self.assertNotEqual(0, self.run_backup().returncode)
        self.assertEqual(b"existing backup", self.output.read_bytes())
        self.output.unlink()
        (self.source / "password").unlink()
        (self.source / "password").symlink_to(self.phrase)
        self.assertNotEqual(0, self.run_backup().returncode)
        self.assertFalse(self.output.exists())

    def test_noninteractive_run_never_prompts_for_secrets(self):
        result = subprocess.run(
            [str(SCRIPT), str(self.source), str(self.output)], capture_output=True,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"interactive terminal", result.stderr)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
