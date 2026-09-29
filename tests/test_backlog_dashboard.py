"""Regression checks for refreshing the dashboard after CLI mutations."""

import contextlib
import io
import json
from pathlib import Path
import shutil
import sys
import unittest
from unittest.mock import patch
from uuid import uuid4

import backlog_cli


class DashboardRefreshTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parent / f"dashboard-test-{uuid4().hex}"
        self.root.mkdir()
        self.addCleanup(shutil.rmtree, self.root)
        self.backlog = self.root / "backlog.json"
        self.dashboard = self.root / "backlog-dashboard.html"
        self.template = self.root / "backlog-dashboard.template.html"
        self.backlog.write_text(
            json.dumps({"statuses": {"todo": "Pending", "done": "Done"}, "tasks": []}),
            encoding="utf-8",
        )
        self.template.write_text("<script>const DATA = __DATA__;</script>", encoding="utf-8")
        for name, value in (("ROOT", self.root), ("BACKLOG_PATH", self.backlog)):
            patcher = patch.object(backlog_cli, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def run_cli(self, *arguments):
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(sys, "argv", ["backlog_cli.py", *arguments]):
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                result = backlog_cli.main()
        self.assertEqual(result, 0, stderr.getvalue())
        return stderr.getvalue()

    def add_task(self):
        return self.run_cli("add", "--title", "New <task>", "--description", "Learn words")

    def saved_data(self):
        return json.loads(self.backlog.read_text(encoding="utf-8"))

    def dashboard_data(self):
        content = self.dashboard.read_text(encoding="utf-8")
        return json.loads(content.removeprefix("<script>const DATA = ").removesuffix(";</script>"))

    def test_add_refreshes_dashboard_with_saved_task(self):
        self.assertEqual(self.add_task(), "")
        self.assertEqual(self.dashboard_data(), self.saved_data())
        self.assertEqual(self.dashboard_data()["tasks"][0]["title"], "New <task>")
        self.assertTrue((self.root / "backlog/001.md").is_file())
        self.assertNotIn("<task>", self.dashboard.read_text(encoding="utf-8"))

    def test_update_refreshes_existing_dashboard(self):
        self.add_task()
        before = self.dashboard.read_bytes()
        self.assertEqual(self.run_cli("update", "001", "--status", "done"), "")
        self.assertNotEqual(self.dashboard.read_bytes(), before)
        self.assertEqual(self.dashboard_data(), self.saved_data())
        self.assertEqual(self.dashboard_data()["tasks"][0]["status"], "done")

    def test_invalid_utf8_template_preserves_added_task_and_detail(self):
        self.template.write_bytes(b"\xff")
        self.dashboard.write_text("Previous dashboard", encoding="utf-8")
        self.assertIn("dashboard", self.add_task())
        self.assertEqual(self.saved_data()["tasks"][0]["id"], "001")
        self.assertIn("Learn words", (self.root / "backlog/001.md").read_text(encoding="utf-8"))
        self.assertEqual(self.dashboard.read_text(encoding="utf-8"), "Previous dashboard")

    def test_failed_dashboard_replace_preserves_previous_html_and_saved_update(self):
        self.add_task()
        previous_html = self.dashboard.read_bytes()
        previous_detail = (self.root / "backlog/001.md").read_bytes()
        replace = backlog_cli.os.replace

        def fail_dashboard_replace(source, destination):
            if Path(destination) == self.dashboard:
                raise PermissionError("Dashboard is locked")
            return replace(source, destination)

        with patch.object(backlog_cli.os, "replace", side_effect=fail_dashboard_replace):
            self.assertIn("dashboard", self.run_cli("update", "001", "--status", "done"))
        self.assertEqual(self.saved_data()["tasks"][0]["status"], "done")
        self.assertEqual(self.dashboard.read_bytes(), previous_html)
        self.assertEqual((self.root / "backlog/001.md").read_bytes(), previous_detail)
        self.assertEqual(list(self.root.glob("*.tmp")), [])


if __name__ == "__main__":
    unittest.main()
