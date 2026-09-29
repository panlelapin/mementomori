"""Verify privacy boundaries using synthetic messages, not the owner's conversations."""

import importlib.machinery
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
loader = importlib.machinery.SourceFileLoader("export_conversation", str(ROOT / "scripts/export-conversation"))
EXPORT = importlib.util.module_from_spec(importlib.util.spec_from_loader(loader.name, loader))
loader.exec_module(EXPORT)


class ConversationExportTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.session = self.root / "session.jsonl"
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        (self.root / ".gitignore").write_text("private/\n")
        (self.root / "private").mkdir(mode=0o700)
        self.output = self.root / "private/conversation.json"

    def write_session(self, items, cwd=None):
        records = [{"type": "session_meta", "payload": {"id": "fixture", "cwd": str(cwd or self.root)}}]
        records.extend(items)
        self.session.write_text("".join(json.dumps(record) + "\n" for record in records))

    def message(self, role, text, **extra):
        return {"type": "response_item", "payload": {
            "type": "message", "role": role,
            "content": [{"type": "output_text" if role == "assistant" else "input_text", "text": text}],
            **extra,
        }}

    def test_only_visible_text_survives_and_plaintext_is_private(self):
        self.write_session([
            self.message("user", "Hello"), self.message("assistant", "Answer", channel="final"),
            self.message("assistant", "Progress", channel="commentary"),
            self.message("system", "INTERNAL"), self.message("developer", "INTERNAL"),
            self.message("assistant", "INTERNAL", channel="analysis"),
            self.message("assistant", "INTERNAL", recipient="functions.exec"),
            self.message("tool", "INTERNAL"), self.message("user", "<environment_context>INTERNAL"),
            self.message("user", "# AGENTS.md instructions for /project\nINTERNAL"),
            {"type": "response_item", "payload": {"type": "reasoning", "text": "INTERNAL"}},
            {"type": "event_msg", "payload": {"type": "agent_message", "message": "duplicate"}},
        ])
        transcript = EXPORT.visible_transcript(self.session, self.root)
        self.assertEqual(["Hello", "Answer", "Progress"], [m["text"] for m in transcript["messages"]])
        EXPORT.write_private_export(self.root, self.output, transcript)
        self.assertNotIn("INTERNAL", self.output.read_text())
        self.assertEqual(0o600, self.output.stat().st_mode & 0o777)
        with self.assertRaises(FileExistsError):
            EXPORT.write_private_export(self.root, self.output, transcript)
        with self.assertRaises(ValueError):
            EXPORT.write_private_export(self.root, self.root / "public.json", transcript)

    def test_wrong_or_mixed_project_is_rejected(self):
        self.write_session([self.message("user", "Hello")], cwd=self.root / "different")
        with self.assertRaisesRegex(ValueError, "different project"):
            EXPORT.visible_transcript(self.session, self.root)
        self.write_session([
            self.message("user", "Hello"),
            {"type": "turn_context", "payload": {"cwd": str(self.root / "different")}},
        ])
        with self.assertRaisesRegex(ValueError, "another project"):
            EXPORT.visible_transcript(self.session, self.root)

    def test_partial_tail_is_reported_and_malformed_complete_record_fails(self):
        self.write_session([self.message("user", "Hello")])
        with self.session.open("ab") as stream:
            stream.write(b'{"unfinished":')
        transcript = EXPORT.visible_transcript(self.session, self.root)
        self.assertTrue(transcript["incomplete_tail_omitted"])
        with self.session.open("ab") as stream:
            stream.write(b"\n")
        with self.assertRaises(ValueError):
            EXPORT.visible_transcript(self.session, self.root)


if __name__ == "__main__":
    unittest.main()
