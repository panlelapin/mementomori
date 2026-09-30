"""Regression tests use temporary Git repositories and fake tools, never GitHub or ADB."""

import importlib.machinery
import importlib.util
import os
import re
import shlex
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
loader = importlib.machinery.SourceFileLoader("source_state", str(ROOT / "scripts/source-state"))
STATE = importlib.util.module_from_spec(importlib.util.spec_from_loader(loader.name, loader))
loader.exec_module(STATE)


class DeliveryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.git("init", "-q")
        self.git("config", "user.email", "tests@example.invalid")
        self.git("config", "user.name", "Test")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, stderr=subprocess.STDOUT)

    def write(self, name, text, executable=False):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        if executable:
            path.chmod(0o755)
        return path

    def test_source_digest_survives_stage_and_commit_but_detects_content_mode_and_deletion(self):
        path = self.write("file", "one")
        before = STATE.source_digest(self.root)
        self.git("add", ".")
        self.git("commit", "-qm", "initial")
        self.assertEqual(before, STATE.source_digest(self.root))
        path.write_text("two")
        self.assertNotEqual(before, STATE.source_digest(self.root))
        path.write_text("one")
        path.chmod(0o755)
        self.assertNotEqual(before, STATE.source_digest(self.root))
        path.unlink()
        deleted = STATE.source_digest(self.root)
        self.assertNotEqual(before, deleted)
        self.git("add", "-A")
        self.git("commit", "-qm", "delete")
        self.assertEqual(deleted, STATE.source_digest(self.root))

    def contract_fixture(self):
        self.write(".gitignore", "build/\ncheck-local.log\n")
        self.write("gradlew", "#!/bin/sh\nexit 0\n", True)
        self.write("gradle/wrapper/gradle-wrapper.jar", "fixture")
        self.write("gradle/wrapper/gradle-wrapper.properties", "distributionSha256Sum=fixture\n")
        self.write("app/build.gradle.kts", "minSdk = 34\n")
        self.write("skill/make-android-widget/SKILL.md", "fixture")
        self.write("skill/make-android-widget/agents/openai.yaml", "fixture")
        for name in ("assets", "references"):
            (self.root / "skill/make-android-widget" / name).mkdir()
        for name in ("check-local", "make-remote", "check-github-stuff", "verify-apk", "source-state"):
            self.write("scripts/" + name, "#!/bin/sh\nexit 0\n", True)
            self.write("skill/make-android-widget/scripts/" + name, "#!/bin/sh\nexit 0\n", True)

    def contract(self):
        return subprocess.run(
            ["bash", "-c", 'source "$1"; run_check contract check_project_contract; exit "$failures"', "test", str(ROOT / "scripts/check-local")],
            cwd=self.root, capture_output=True, text=True,
        )

    def test_contract_rejects_each_mismatch_and_missing_executable(self):
        self.contract_fixture()
        self.assertEqual(0, self.contract().returncode)
        for name in ("check-local", "make-remote", "check-github-stuff", "verify-apk", "source-state"):
            with self.subTest(name=name):
                path = self.root / "scripts" / name
                original = path.read_text()
                path.write_text(original + "# changed\n")
                self.assertNotEqual(0, self.contract().returncode)
                path.write_text(original)
                path.chmod(0o644)
                self.assertNotEqual(0, self.contract().returncode)
                path.chmod(0o755)

    def test_contract_rejects_missing_wrapper_or_unignored_log(self):
        self.contract_fixture()
        (self.root / "gradle/wrapper/gradle-wrapper.jar").unlink()
        self.assertNotEqual(0, self.contract().returncode)
        self.write("gradle/wrapper/gradle-wrapper.jar", "fixture")
        self.write(".gitignore", "build/\n")
        self.assertNotEqual(0, self.contract().returncode)

    def test_recovery_contract_requires_matching_script_tests_config_and_permissions(self):
        self.contract_fixture()
        script = self.write("scripts/restore-workspace", "#!/usr/bin/env python3\n", True)
        self.assertNotEqual(0, self.contract().returncode)
        resource = self.write("skill/make-android-widget/scripts/restore-workspace",
                              script.read_text(), True)
        self.write("config/recovery.json", "{}")
        tests = self.write("tests/test_workspace_recovery.py", "# tests\n")
        self.write("skill/make-android-widget/tests/test_workspace_recovery.py", tests.read_text())
        self.assertEqual(0, self.contract().returncode)
        for change in ("content", "mode"):
            with self.subTest(change=change):
                if change == "content":
                    resource.write_text("# mismatch\n")
                else:
                    resource.write_text(script.read_text())
                    resource.chmod(0o644)
                self.assertNotEqual(0, self.contract().returncode)
    def test_syntax_checker_parses_every_script(self):
        self.contract_fixture()
        for name in ("check-local", "make-remote", "verify-apk"):
            with self.subTest(name=name):
                path = self.root / "scripts" / name
                original = path.read_text()
                path.write_text("if then broken\n")
                result = subprocess.run(["bash", "-c", 'source "$1"; check_shell_syntax', "test", str(ROOT / "scripts/check-local")], cwd=self.root, capture_output=True)
                self.assertNotEqual(0, result.returncode)
                path.write_text(original)

    def test_missing_workflow_overwrites_old_pass_and_captures_error(self):
        self.write("check-local.log", "CHECK_LOCAL_RESULT=PASS\n")
        result = subprocess.run(["bash", str(ROOT / "scripts/check-local")], cwd=self.root, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        log = (self.root / "check-local.log").read_text()
        self.assertNotIn("CHECK_LOCAL_RESULT=PASS", log)
        self.assertIn("CHECK_LOCAL_RESULT=FAIL", log)
        self.assertIn("workflow not found", log)

    def test_apk_verifier_checks_certificate_identity(self):
        apk = self.write("release.apk", "fixture")
        aapt = self.write("aapt", "#!/bin/sh\nprintf \"package: name='com.example.test'\\n\"\n", True)
        analyzer = self.write("analyzer", '#!/bin/sh\ncase "$2" in min-sdk) echo 34;; target-sdk) echo 36;; debuggable) echo false;; esac\n', True)
        signer = self.write("signer", "#!/bin/sh\nprintf 'Signer #1 certificate SHA-256 digest: " + "a" * 64 + "\\n'\n", True)
        env = dict(os.environ, ANDROID_HOME=str(self.root), AAPT2=str(aapt), APKANALYZER=str(analyzer), APKSIGNER=str(signer))
        for expected, success in [("a" * 64, True), ("b" * 64, False), ("", False)]:
            with self.subTest(expected=expected):
                result = subprocess.run([str(ROOT / "scripts/verify-apk"), str(apk), "com.example.test", "34", "36"], env=dict(env, EXPECTED_CERT_SHA256=expected), capture_output=True)
                self.assertEqual(success, result.returncode == 0)

    def test_failed_quality_task_is_logged_and_later_checks_still_run(self):
        self.contract_fixture()
        shutil.copy2(ROOT / "scripts/source-state", self.root / "scripts/source-state")
        shutil.copy2(ROOT / "scripts/source-state", self.root / "skill/make-android-widget/scripts/source-state")
        self.write(".github/workflows/android-widget.yml", "fixture")
        self.write("gradlew", "#!/bin/sh\necho gradle-stdout\necho gradle-stderr >&2\nexit 2\n", True)
        command = 'source "$1"; check_shellcheck() { return 0; }; resolve_toolchains() { return 0; }; check_workflow() { return 0; }; main'
        result = subprocess.run(["bash", "-c", command, "test", str(ROOT / "scripts/check-local")], cwd=self.root, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        log = (self.root / "check-local.log").read_text()
        for expected in ("gradle-stdout", "gradle-stderr", "FAIL: Gradle", "PASS: Android XML", "CHECK_LOCAL_RESULT=FAIL"):
            self.assertIn(expected, log)

    def test_resume_after_commit_skips_push_and_dispatch_and_rejects_other_commits(self):
        self.write(".gitignore", "check-local.log\nbuild/\ntrace\nbin/\n")
        self.write("app/build.gradle.kts", 'applicationId = "com.example.test"\n')
        self.write(".github/workflows/android-widget.yml", "fixture")
        self.write("gradlew", "#!/bin/sh\nexit 0\n", True)
        self.write("scripts/verify-apk", "#!/bin/sh\nexit 0\n", True)
        shutil.copy2(ROOT / "scripts/source-state", self.write("scripts/source-state", ""))
        digest = STATE.source_digest(self.root)
        self.git("add", ".")
        self.git("commit", "-qm", "checked tree")
        self.git("remote", "add", "origin", "https://github.com/example/test")
        sha = self.git("rev-parse", "HEAD").decode().strip()
        self.write("check-local.log", f"CHECK_LOCAL_SOURCE_DIGEST={digest}\nCHECK_LOCAL_RESULT=PASS\n")
        self.write("bin/gh", '#!/bin/sh\necho "$*" >> trace\ncase "$1 $2" in "run view") printf "completed\\tsuccess\\t%s\\n" "$TEST_SHA";; "run download") exit 7;; "auth status"|"repo view") exit 0;; *) exit 99;; esac\n', True)
        env = dict(os.environ, PATH=str(self.root / "bin") + os.pathsep + os.environ["PATH"], EXPECTED_CERT_SHA256="a" * 64)
        result = subprocess.run(["bash", str(ROOT / "scripts/make-remote"), "--resume", "42"], cwd=self.root, env=dict(env, TEST_SHA=sha), capture_output=True, text=True)
        self.assertIn("Resuming successful run 42", result.stdout)
        self.assertNotIn("workflow run", (self.root / "trace").read_text())
        self.assertNotEqual(0, result.returncode)  # Deliberate stop before downloading any artifact.
        wrong = subprocess.run(["bash", str(ROOT / "scripts/make-remote"), "--resume", "42"], cwd=self.root, env=dict(env, TEST_SHA="b" * 40), capture_output=True, text=True)
        self.assertIn("not current commit", wrong.stderr)

    def test_interrupted_artifact_replacement_restores_previous_build_and_fails(self):
        source = (ROOT / "scripts/make-remote").read_text()
        cleanup = re.search(r"(?ms)^cleanup\(\) \{\n.*?^\}", source).group()
        backup = self.root / "backup"
        backup.mkdir()
        (backup / "previous.apk").write_text("previous verified artifact")
        download = self.root / "download"
        download.mkdir()
        command = "\n".join([
            "set -Eeuo pipefail",
            "BUILD_PATH=" + shlex.quote(str(self.root / "build")),
            "BACKUP_DIR=" + shlex.quote(str(backup)),
            "DOWNLOAD_DIR=" + shlex.quote(str(download)),
            "STAGING_DIR=''", "DEVICE_VERIFY_DIR=''", cleanup,
            "trap cleanup EXIT", "trap 'exit 143' TERM", "kill -TERM $$",
        ])
        result = subprocess.run(["bash", "-c", command], capture_output=True)
        self.assertEqual(143, result.returncode)
        self.assertEqual("previous verified artifact", (self.root / "build/previous.apk").read_text())
        self.assertFalse(download.exists())

    def github_check_fixture(self):
        slug = "example/" + self.root.name
        command = self.write("bin/gh", '''#!/bin/sh
echo "$*" >> "$TEST_TRACE"
case "$1 $2" in
  "auth status") exit 0;;
  "api --paginate")
    test "$TEST_API_FAILURE" = 0 || exit 7
    printf '%s\\n' "$TEST_REMOTE";;
  "repo view") printf 'https://github.com/%s\\n' "$TEST_REMOTE";;
  *) exit 99;;
esac
''', True)
        env = dict(os.environ, PATH=str(command.parent) + os.pathsep + os.environ["PATH"],
                   TEST_REMOTE=slug, TEST_API_FAILURE="0", TEST_TRACE=str(self.root / "trace"))
        return slug, env

    def test_github_check_matching_origin_is_read_only(self):
        slug, env = self.github_check_fixture()
        self.git("remote", "add", "origin", "https://github.com/" + slug + ".git")
        before = (self.root / ".git/config").read_bytes()
        result = subprocess.run([str(ROOT / "scripts/check-github-stuff")], cwd=self.root,
                                env=env, capture_output=True)
        self.assertEqual(0, result.returncode)
        self.assertEqual(before, (self.root / ".git/config").read_bytes())
        self.assertEqual(2, len((self.root / "trace").read_text().splitlines()))

    def test_github_check_refuses_origin_change_without_interactive_confirmation(self):
        _, env = self.github_check_fixture()
        self.git("remote", "add", "origin", "https://github.com/other/old")
        result = subprocess.run([str(ROOT / "scripts/check-github-stuff")], cwd=self.root,
                                env=env, capture_output=True, stdin=subprocess.DEVNULL)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"interactive question", result.stderr)
        self.assertEqual(b"https://github.com/other/old\n", self.git("remote", "get-url", "origin"))

    def test_github_check_api_error_does_not_create_or_delete_any_repository(self):
        _, env = self.github_check_fixture()
        result = subprocess.run([str(ROOT / "scripts/check-github-stuff")], cwd=self.root,
                                env=dict(env, TEST_API_FAILURE="1"), capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"could not list", result.stderr)
        self.assertEqual(2, len((self.root / "trace").read_text().splitlines()))

    def test_workflow_rejects_retired_sdk_tools_including_implicit_action_default(self):
        workflow = (ROOT / ".github/workflows/android-widget.yml").read_text()
        for packages, success in [("platform-tools", True), ("tools platform-tools", False), (None, False)]:
            with self.subTest(packages=packages):
                replacement = "" if packages is None else "          packages: " + packages + "\n"
                content = re.sub(r"(?m)^          packages: platform-tools\n", replacement, workflow)
                path = self.write("workflow.yml", content)
                result = subprocess.run(
                    ["bash", "-c", 'source "$1"; WORKFLOW_FILE=$2; check_workflow', "test",
                     str(ROOT / "scripts/check-local"), str(path)], capture_output=True,
                )
                self.assertEqual(success, result.returncode == 0)


if __name__ == "__main__":
    unittest.main()
