import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "tools/termux/travelzctl"


class TravelzCtlTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.public = self.root / "travelz"
        self.vault = self.root / "vault"
        (self.public / "trips/2026-example").mkdir(parents=True)
        (self.vault / "trips/2026-example").mkdir(parents=True)
        (self.public / "trips/index.json").write_text(
            json.dumps({"trips": [{"id": "2026-example"}]}), encoding="utf-8")
        (self.public / "trips/2026-example/trip.json").write_text(
            json.dumps({"title": "Example", "route": "A to B", "start": "2026-01-01", "end": "2026-01-02"}),
            encoding="utf-8")
        self.manifest = self.vault / "trips/2026-example/docs.json"
        self.manifest.write_text(json.dumps({"documents": [
            {"id": "ticket", "label": "Ticket", "file": "ticket.pdf", "type": "pdf"}
        ]}), encoding="utf-8")
        (self.vault / "trips/2026-example/ticket.pdf").write_bytes(b"fixture")
        self.environment = os.environ.copy()
        self.environment.update({"TRAVELZ_REPO": str(self.public), "TRAVAULT_REPO": str(self.vault),
                                 "TRAVELZ_STATE": str(self.root / "last-sync.json")})

    def run_cli(self, *args):
        result = subprocess.run([sys.executable, str(SCRIPT), *args], env=self.environment,
                                capture_output=True, text=True, check=False)
        self.assertEqual(result.stderr, "")
        return result.returncode, json.loads(result.stdout)

    def test_trip_and_curated_docs(self):
        code, trip = self.run_cli("trip", "--json")
        self.assertEqual(code, 0)
        self.assertEqual(trip["id"], "2026-example")
        code, docs = self.run_cli("docs", "--json")
        self.assertEqual(code, 0)
        self.assertEqual(docs["documents"], [
            {"id": "ticket", "label": "Ticket", "type": "pdf", "available": True}])
        self.assertNotIn("file", docs["documents"][0])

    def test_open_accepts_id_and_never_a_path(self):
        tools = self.root / "bin"
        tools.mkdir()
        marker = self.root / "opened"
        opener = tools / "termux-open"
        opener.write_text("#!/bin/sh\nprintf '%s' \"$1\" > \"$TRAVELZ_OPEN_MARKER\"\n", encoding="utf-8")
        opener.chmod(0o700)
        self.environment["PATH"] = f"{tools}:{self.environment.get('PATH', '')}"
        self.environment["TRAVELZ_OPEN_MARKER"] = str(marker)
        code, opened = self.run_cli("open", "ticket", "--json")
        self.assertEqual(code, 0)
        self.assertTrue(opened["launched"])
        self.assertEqual(marker.read_text(), str(self.vault / "trips/2026-example/ticket.pdf"))
        code, rejected = self.run_cli("open", "../ticket", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(rejected["error"], "invalid_id")

    def test_manifest_cannot_escape_trip_folder(self):
        outside = self.root / "outside.pdf"
        outside.write_bytes(b"outside")
        (self.vault / "trips/2026-example/ticket.pdf").unlink()
        (self.vault / "trips/2026-example/ticket.pdf").symlink_to(outside)
        code, result = self.run_cli("docs", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(result["error"], "manifest_invalid")
        self.manifest.write_text(json.dumps({"documents": [
            {"id": "ticket", "label": "Ticket", "file": "../outside.pdf", "type": "pdf"}
        ]}), encoding="utf-8")
        code, result = self.run_cli("docs", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(result["error"], "manifest_invalid")

    def test_missing_document_is_reported_without_opening_it(self):
        (self.vault / "trips/2026-example/ticket.pdf").unlink()
        code, listed = self.run_cli("docs", "--json")
        self.assertEqual(code, 0)
        self.assertFalse(listed["documents"][0]["available"])
        code, opened = self.run_cli("open", "ticket", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(opened["error"], "file_missing")

    def test_missing_repositories_are_visible_in_status_and_refuse_sync(self):
        code, listed = self.run_cli("status", "--json")
        self.assertEqual(code, 1)
        self.assertFalse(listed["travelz_repo"]["exists"])
        self.assertFalse(listed["vault_repo"]["exists"])
        code, result = self.run_cli("sync", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(result["error"], "repo_missing")

    def test_sync_refuses_dirty_repo_before_pulling_either(self):
        for repo in (self.public, self.vault):
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            subprocess.run(["git", "-C", str(repo), "-c", "user.name=Test", "-c",
                            "user.email=test@example.invalid", "add", "."], check=True)
            subprocess.run(["git", "-C", str(repo), "-c", "user.name=Test", "-c",
                            "user.email=test@example.invalid", "commit", "-qm", "fixture"], check=True)
        (self.public / "local-note").write_text("keep", encoding="utf-8")
        code, result = self.run_cli("sync", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(result["error"], "dirty")
        self.assertTrue((self.public / "local-note").exists())

    def test_failed_pull_does_not_record_a_successful_sync(self):
        for repo in (self.public, self.vault):
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            subprocess.run(["git", "-C", str(repo), "-c", "user.name=Test", "-c",
                            "user.email=test@example.invalid", "add", "."], check=True)
            subprocess.run(["git", "-C", str(repo), "-c", "user.name=Test", "-c",
                            "user.email=test@example.invalid", "commit", "-qm", "fixture"], check=True)
        code, result = self.run_cli("sync", "--json")
        self.assertEqual(code, 1)
        self.assertEqual(result["error"], "git_error")
        self.assertFalse((self.root / "last-sync.json").exists())


if __name__ == "__main__":
    unittest.main()
