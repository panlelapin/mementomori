"""Recovery tests use disposable repositories/keys, never real signing data or GitHub.

Unit tests cover hostile archives and transactional publication. Integration tests
exercise real GnuPG and keytool; a test-only wrapper supplies a PUBLIC fixture
phrase so no production script accepts secrets through arguments/environment.
"""

from contextlib import redirect_stdout
import hashlib
import importlib.machinery
import importlib.util
import io
import json
import os
from pathlib import Path
import pty
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/restore-workspace"
loader = importlib.machinery.SourceFileLoader("workspace_recovery", str(SCRIPT))
RECOVERY = importlib.util.module_from_spec(importlib.util.spec_from_loader(loader.name, loader))
loader.exec_module(RECOVERY)


def archive_bytes(entries):
    """Build a GNU-format archive; explicit TarInfo values can model malicious input."""
    data = io.BytesIO()
    with tarfile.open(fileobj=data, mode="w", format=tarfile.GNU_FORMAT) as archive:
        for name, content in entries:
            info = name if isinstance(name, tarfile.TarInfo) else tarfile.TarInfo(name)
            info.size = len(content)
            archive.addfile(info, io.BytesIO(content))
    return data.getvalue()


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.git("init", "-q")
        (self.root / ".gitignore").write_text("/.signing/\n")
        (self.root / "config").mkdir()
        self.config = {
            "format": 1, "archive": "config/backup.tar.gpg", "archive_sha256": "a" * 64,
            "certificate": "config/certificate.sha256", "key_alias": "distribution",
            "files": ["release.p12", "password", "conversation.json"],
            "jdk": 17, "android_api": 36, "build_tools": "36.0.0",
        }
        self.config_file = self.root / "config/recovery.json"
        self.config_file.write_text(json.dumps(self.config))
        self.staging = self.root / "staging"
        self.staging.mkdir(mode=0o700)
        self.contents = {
            "release.p12": b"public dummy fixture, not a key",
            "password": b"public fixture password\n",
            "conversation.json": json.dumps({
                "format": "visible-conversation-v1",
                "messages": [{"role": "user", "text": "public fixture message"}],
            }).encode(),
        }

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, stderr=subprocess.PIPE)

    def unpack(self, entries=None):
        RECOVERY.unpack(archive_bytes(entries if entries is not None else self.contents.items()),
                        self.staging, self.config["files"])

    def test_config_accepts_only_documented_schema_and_safe_paths(self):
        self.assertEqual(self.config, RECOVERY.load_config(self.root))
        for key, bad in (("format", 2), ("archive", "../outside"), ("archive", "/absolute"),
                         ("archive", "config//backup"), ("archive_sha256", "wrong"),
                         ("files", ["../password"]), ("jdk", True), ("jdk", "17"),
                         ("key_alias", "--help"), ("build_tools", "../36.0.0")):
            with self.subTest(key=key, bad=bad):
                self.config_file.write_text(json.dumps(dict(self.config, **{key: bad})))
                with self.assertRaises(RECOVERY.RecoveryError):
                    RECOVERY.load_config(self.root)

    def test_config_rejects_symlinked_parent(self):
        (self.root / "linked").symlink_to(self.root / "config", target_is_directory=True)
        self.config["archive"] = "linked/backup.tar.gpg"
        self.config_file.write_text(json.dumps(self.config))
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.load_config(self.root)

    def test_ciphertext_checksum_and_certificate_format_fail_closed(self):
        ciphertext = b"public ciphertext fixture"
        (self.root / self.config["archive"]).write_bytes(ciphertext)
        (self.root / self.config["certificate"]).write_text("b" * 64 + "\n")
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.checked_archive(self.root, self.config)
        self.config["archive_sha256"] = hashlib.sha256(ciphertext).hexdigest()
        self.assertEqual((ciphertext, "b" * 64), RECOVERY.checked_archive(self.root, self.config))
        (self.root / self.config["certificate"]).write_text("not a fingerprint")
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.checked_archive(self.root, self.config)

    def test_regular_input_rejects_symlinks_fifo_empty_and_oversize(self):
        source = self.root / "source"
        source.write_bytes(b"abcdef")
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.regular_bytes(source, 2)
        source.write_bytes(b"")
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.regular_bytes(source, 2)
        link = self.root / "link"
        link.symlink_to(source)
        with self.assertRaises(OSError):
            RECOVERY.regular_bytes(link, 10)
        fifo = self.root / "fifo"
        os.mkfifo(fifo)
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.regular_bytes(fifo, 10)

    def test_unpack_keeps_exact_bytes_and_private_permissions(self):
        self.unpack()
        for name, expected in self.contents.items():
            self.assertEqual(expected, (self.staging / name).read_bytes())
            self.assertEqual(0o600, (self.staging / name).stat().st_mode & 0o777)

    def test_archive_with_only_key_and_password_is_supported(self):
        self.config["files"] = ["release.p12", "password"]
        self.unpack([(name, self.contents[name]) for name in self.config["files"]])
        self.assertEqual(set(self.config["files"]), {path.name for path in self.staging.iterdir()})

    def test_traversal_absolute_extra_and_duplicate_members_never_write(self):
        for name in ("../outside", "/outside", "nested/password", "other", "release.p12"):
            with self.subTest(name=name):
                with self.assertRaises(RECOVERY.RecoveryError):
                    self.unpack(list(self.contents.items()) + [(name, b"unexpected")])
                self.assertEqual([], list(self.staging.iterdir()))

    def test_symlinks_hardlinks_devices_and_directories_never_write(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.DIRTYPE, tarfile.FIFOTYPE,
                     tarfile.CHRTYPE, tarfile.BLKTYPE):
            with self.subTest(kind=kind):
                info = tarfile.TarInfo("release.p12")
                info.type = kind
                info.linkname = "../outside"
                with self.assertRaises(RECOVERY.RecoveryError):
                    self.unpack([(info, b"")] + list(self.contents.items())[1:])
                self.assertEqual([], list(self.staging.iterdir()))

    def test_missing_empty_oversize_and_invalid_json_or_password_never_write(self):
        variants = [list(self.contents.items())[1:]]
        for name, content in (("password", b""), ("password", b"x" * 4097),
                              ("password", b"one\ntwo"), ("password", b"null\x00byte"),
                              ("conversation.json", b"[]"),
                              ("conversation.json", b'{"format":"unknown","messages":[]}')):
            variants.append(list(dict(self.contents, **{name: content}).items()))
        for entries in variants:
            with self.subTest(entries=[name for name, _ in entries]):
                with self.assertRaises(RECOVERY.RecoveryError):
                    self.unpack(entries)
                self.assertEqual([], list(self.staging.iterdir()))

    def test_trailing_data_and_truncated_tar_are_rejected_before_writing(self):
        data = archive_bytes(self.contents.items())
        for invalid in (data + b"hidden additional data", data[:100]):
            with self.assertRaises((RECOVERY.RecoveryError, tarfile.TarError)):
                RECOVERY.unpack(invalid, self.staging, self.config["files"])
            self.assertEqual([], list(self.staging.iterdir()))

    def test_private_directory_requires_ignore_and_no_tracked_secrets(self):
        (self.root / ".gitignore").write_text("")
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.validate_private_directory(self.root)
        self.assertFalse((self.root / ".signing").exists())
        (self.root / ".gitignore").write_text("/.signing/\n")
        private = RECOVERY.validate_private_directory(self.root)
        self.assertEqual(0o700, private.stat().st_mode & 0o777)
        (private / "password").write_text("public fixture")
        self.git("add", "-f", ".signing/password")
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.validate_private_directory(self.root)

    def test_private_directory_refuses_symlink_and_public_permissions(self):
        private = self.root / ".signing"
        private.symlink_to(self.staging, target_is_directory=True)
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.validate_private_directory(self.root)
        private.unlink()
        private.mkdir(mode=0o700)
        private.chmod(0o755)
        with self.assertRaises(RECOVERY.RecoveryError):
            RECOVERY.validate_private_directory(self.root)
        self.assertEqual(0o755, private.stat().st_mode & 0o777)

    def test_lock_prevents_concurrency_and_releases_on_interruption(self):
        private = RECOVERY.validate_private_directory(self.root)
        with self.assertRaises(KeyboardInterrupt):
            with RECOVERY.restoration_lock(private):
                with self.assertRaises(RECOVERY.RecoveryError):
                    with RECOVERY.restoration_lock(private):
                        self.fail("a second restoration must not acquire the lock")
                raise KeyboardInterrupt()
        self.assertFalse((private / ".restore.lock").exists())

    def test_publication_conflict_rolls_back_only_our_new_files(self):
        self.unpack()
        private = RECOVERY.validate_private_directory(self.root)
        (private / "password").write_bytes(b"pre-existing data")
        with self.assertRaises(FileExistsError):
            RECOVERY.publish(self.staging, private, self.config["files"])
        self.assertFalse((private / "release.p12").exists())
        self.assertEqual(b"pre-existing data", (private / "password").read_bytes())

    def test_publication_interruption_after_link_rolls_back(self):
        self.unpack()
        private = RECOVERY.validate_private_directory(self.root)
        real_link = os.link

        def interrupt_after_link(*args, **kwargs):
            real_link(*args, **kwargs)
            raise KeyboardInterrupt()

        with patch.object(RECOVERY.os, "link", side_effect=interrupt_after_link):
            with self.assertRaises(KeyboardInterrupt):
                RECOVERY.publish(self.staging, private, self.config["files"])
        self.assertEqual([], list(private.iterdir()))

    def test_noninteractive_restoration_refuses_before_writing(self):
        with patch.object(RECOVERY.sys.stdin, "isatty", return_value=False):
            with self.assertRaises(RECOVERY.RecoveryError):
                RECOVERY.restore(self.root, self.config, b"unused", "a" * 64, Path("/unused"))
        self.assertFalse((self.root / ".signing").exists())

    def test_default_negative_answer_and_eof_never_authorize_an_action(self):
        with patch.object(RECOVERY.sys.stdin, "isatty", return_value=True), \
                patch.object(RECOVERY.sys.stderr, "isatty", return_value=True):
            for answer in ("", "n", "non", "unexpected"):
                with patch("builtins.input", return_value=answer):
                    self.assertFalse(RECOVERY.confirm("test"))
            with patch("builtins.input", side_effect=EOFError):
                self.assertFalse(RECOVERY.confirm("test"))

    def test_diagnose_cli_never_decrypts_or_mutates_files(self):
        self.config["archive_sha256"] = hashlib.sha256(b"encrypted fixture").hexdigest()
        self.config_file.write_text(json.dumps(self.config))
        (self.root / self.config["archive"]).write_bytes(b"encrypted fixture")
        (self.root / self.config["certificate"]).write_text("a" * 64)
        commands = self.root / "bin"
        commands.mkdir()
        for name, body in (("gh", 'test "$1 $2" = "auth status"\n'),
                           ("gpg", 'exit 99\n')):
            command = commands / name
            command.write_text("#!/bin/sh\n" + body)
            command.chmod(0o700)
        before = sorted(str(path.relative_to(self.root)) for path in self.root.rglob("*"))
        result = subprocess.run(
            [str(SCRIPT), "--diagnose"], cwd=self.root, capture_output=True, timeout=30,
            env=dict(os.environ, PATH=str(commands) + os.pathsep + os.environ["PATH"]),
        )
        self.assertEqual(2, result.returncode)  # No skill in this disposable checkout.
        self.assertEqual(before, sorted(str(path.relative_to(self.root)) for path in self.root.rglob("*")))
        self.assertFalse((self.root / ".signing").exists())

    def test_declined_recovery_and_checks_perform_no_mutation(self):
        with patch.object(RECOVERY, "git", return_value=str(self.root).encode()), \
                patch.object(RECOVERY, "load_config", return_value=self.config), \
                patch.object(RECOVERY, "checked_archive", return_value=(b"unused", "a" * 64)), \
                patch.object(RECOVERY, "diagnose", return_value=([], None, None)), \
                patch.object(RECOVERY, "confirm", return_value=False), \
                patch.object(RECOVERY, "restore") as restore, \
                patch.object(RECOVERY.subprocess, "run") as run, \
                patch.object(RECOVERY.sys, "argv", [str(SCRIPT)]), redirect_stdout(io.StringIO()):
            self.assertEqual(0, RECOVERY.main())
            restore.assert_not_called()
            run.assert_not_called()

    def test_failed_check_local_never_invokes_make_remote(self):
        for path in ("scripts/check-local", "scripts/make-remote"):
            command = self.root / path
            command.parent.mkdir(exist_ok=True)
            command.write_text("#!/bin/sh\nexit 7\n")
            command.chmod(0o700)
        self.config["archive_sha256"] = hashlib.sha256(b"encrypted fixture").hexdigest()
        self.config_file.write_text(json.dumps(self.config))
        (self.root / self.config["archive"]).write_bytes(b"encrypted fixture")
        (self.root / self.config["certificate"]).write_text("a" * 64)
        with patch.object(RECOVERY.Path, "cwd", return_value=self.root), \
                patch.object(RECOVERY.sys, "argv", [str(SCRIPT)]), \
                patch.object(RECOVERY, "diagnose", return_value=([], None, None)), \
                patch.object(RECOVERY, "confirm", side_effect=[False, True]) as confirm, \
                redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RECOVERY.RecoveryError, "check-local"):
                RECOVERY.main()
            self.assertEqual(2, confirm.call_count)


class RecoveryCryptoTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.root = Path(cls.temporary.name)
        cls.jdk = RECOVERY.discover_jdk(17)
        if cls.jdk is None or not shutil.which("gpg"):
            raise AssertionError("JDK 17/keytool and GnuPG are required, not optional skips")
        cls.password = cls.root / "password"
        cls.password.write_text("public-test-keystore-password\n")
        cls.key = cls.root / "release.p12"
        subprocess.run([
            str(cls.jdk / "bin/keytool"), "-genkeypair", "-noprompt", "-keyalg", "EC",
            "-groupname", "secp256r1", "-alias", "distribution", "-dname", "CN=Public Test Fixture",
            "-validity", "2", "-storetype", "PKCS12", "-keystore", str(cls.key),
            "-storepass:file", str(cls.password),
        ], check=True, capture_output=True)
        cert = subprocess.check_output([
            str(cls.jdk / "bin/keytool"), "-exportcert", "-alias", "distribution",
            "-keystore", str(cls.key), "-storepass:file", str(cls.password),
        ], stderr=subprocess.PIPE)
        cls.fingerprint = hashlib.sha256(cert).hexdigest()
        cls.phrase = cls.root / "phrase"
        cls.phrase.write_text("public test recovery phrase, no real secret\n")
        cls.gpg_home = cls.root / "gnupg"
        cls.gpg_home.mkdir(mode=0o700)
        cls.addClassCleanup(subprocess.run, ["gpgconf", "--homedir", str(cls.gpg_home),
                                            "--kill", "gpg-agent"], check=True, capture_output=True)
        cls.data = archive_bytes([
            ("release.p12", cls.key.read_bytes()), ("password", cls.password.read_bytes()),
            ("conversation.json", json.dumps({"format": "visible-conversation-v1", "messages": [
                {"role": "user", "text": "public fixture transcript"}]}).encode()),
        ])
        cls.real_gpg = shutil.which("gpg")
        cls.encrypted = subprocess.run([
            cls.real_gpg, "--no-options", "--no-keyring", "--homedir", str(cls.gpg_home),
            "--batch", "--pinentry-mode", "loopback", "--passphrase-file", str(cls.phrase),
            "--symmetric", "--cipher-algo", "AES256", "--s2k-count", "65536",
        ], input=cls.data, check=True, capture_output=True).stdout

    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.repo = Path(self.workspace.name)
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        (self.repo / ".gitignore").write_text("/.signing/\n")
        self.config = {"files": ["release.p12", "password", "conversation.json"],
                       "key_alias": "distribution"}
        commands = self.repo / "bin"
        commands.mkdir()
        wrapper = commands / "gpg"
        wrapper.write_text('''#!/bin/sh
if test "$RECOVERY_FIXTURE_MODE" = cancel; then exit 130; fi
exec "$RECOVERY_FIXTURE_GPG" --batch --passphrase-file "$RECOVERY_FIXTURE_PHRASE" "$@"
''')
        wrapper.chmod(0o700)
        self.env = dict(os.environ, GNUPGHOME=str(self.gpg_home),
                        PATH=str(commands) + os.pathsep + os.environ["PATH"],
                        RECOVERY_FIXTURE_GPG=self.real_gpg,
                        RECOVERY_FIXTURE_PHRASE=str(self.phrase), RECOVERY_FIXTURE_MODE="ok")

    def restore(self, fingerprint=None, mode="ok", encrypted=None):
        master, slave = pty.openpty()
        try:
            with os.fdopen(os.dup(slave), "r") as terminal, \
                    patch.object(RECOVERY.sys, "stdin", terminal), \
                    patch.object(RECOVERY.sys.stderr, "isatty", return_value=True), \
                    patch.dict(os.environ, dict(self.env, RECOVERY_FIXTURE_MODE=mode), clear=True), \
                    redirect_stdout(io.StringIO()) as output:
                RECOVERY.restore(self.repo, self.config,
                                 self.encrypted if encrypted is None else encrypted,
                                 fingerprint or self.fingerprint, self.jdk / "bin/keytool")
                return output.getvalue()
        finally:
            os.close(slave)
            os.close(master)

    def assert_no_restored_files(self):
        private = self.repo / ".signing"
        self.assertEqual([], list(private.iterdir()) if private.exists() else [])

    def test_real_crypto_and_certificate_round_trip_with_private_permissions(self):
        output = self.restore()
        private = self.repo / ".signing"
        self.assertEqual(self.key.read_bytes(), (private / "release.p12").read_bytes())
        self.assertEqual(self.password.read_bytes(), (private / "password").read_bytes())
        self.assertEqual(0o700, private.stat().st_mode & 0o777)
        self.assertEqual(set(self.config["files"]), {path.name for path in private.iterdir()})
        for file in private.iterdir():
            self.assertEqual(0o600, file.stat().st_mode & 0o777)
        self.assertNotIn(self.password.read_text().strip(), output)
        self.assertNotIn("public fixture transcript", output)
        self.assertFalse((self.gpg_home / "pubring.kbx").exists())

    def test_wrong_certificate_cancellation_and_truncation_publish_nothing(self):
        for options in ({"fingerprint": "0" * 64}, {"mode": "cancel"},
                        {"encrypted": self.encrypted[:-24]}):
            with self.subTest(options=list(options)):
                with self.assertRaises(RECOVERY.RecoveryError):
                    self.restore(**options)
                self.assert_no_restored_files()

    def test_wrong_passphrase_does_not_publish_even_after_a_previous_success(self):
        wrong = self.repo / "wrong-phrase"
        wrong.write_text("another public fixture phrase\n")
        self.env["RECOVERY_FIXTURE_PHRASE"] = str(wrong)
        with self.assertRaises(RECOVERY.RecoveryError):
            self.restore()
        self.assert_no_restored_files()

    def test_repeat_or_symlinked_target_is_rejected_before_decryption(self):
        private = self.repo / ".signing"
        private.mkdir(mode=0o700)
        (private / "password").symlink_to(self.password)
        with patch.object(RECOVERY, "decrypt") as decrypt:
            with self.assertRaises(RECOVERY.RecoveryError):
                self.restore()
            decrypt.assert_not_called()
        self.assertTrue((private / "password").is_symlink())

    def test_wrong_key_password_is_rejected_without_printing_it(self):
        staging = self.repo / "fixture-staging"
        staging.mkdir(mode=0o700)
        (staging / "release.p12").write_bytes(self.key.read_bytes())
        (staging / "password").write_text("wrong-public-test-password")
        with self.assertRaises(RECOVERY.RecoveryError) as caught:
            RECOVERY.verify_key(staging, self.jdk / "bin/keytool", "distribution", self.fingerprint)
        self.assertNotIn("wrong-public-test-password", str(caught.exception))

    def test_decrypted_output_limit_kills_gpg_and_cleans_staging(self):
        with patch.object(RECOVERY, "MAX_ARCHIVE", 256):
            with self.assertRaisesRegex(RECOVERY.RecoveryError, "volumineuse"):
                self.restore()
        self.assert_no_restored_files()

    def test_signature_validation_failure_preserves_existing_encrypted_copy(self):
        private = self.repo / ".signing"
        private.mkdir(mode=0o700)
        downloaded = private / "backup.downloaded.tar.gpg"
        downloaded.write_bytes(self.encrypted)
        with self.assertRaises(RECOVERY.RecoveryError):
            self.restore(fingerprint="0" * 64)
        self.assertEqual(self.encrypted, downloaded.read_bytes())
        self.assertEqual([downloaded], list(private.iterdir()))


if __name__ == "__main__":
    unittest.main()
