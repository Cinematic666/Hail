import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

MODULE_PATH = Path(__file__).with_name("export_legacy_hail_backup.py")
SPEC = importlib.util.spec_from_file_location("legacy_backup", MODULE_PATH)
legacy_backup = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(legacy_backup)


class LegacyBackupExporterTest(unittest.TestCase):
    def test_shared_preferences_are_typed_and_sensitive_values_are_excluded(self):
        xml = b"""<map>
            <string name="app_theme">theme_dark</string>
            <boolean name="filter_system_apps" value="true" />
            <int name="home_font_size_i" value="14" />
            <float name="auto_freeze_delay_f" value="2.5" />
            <set name="hidden_apps"><string>com.example.a</string><string>com.example.b</string></set>
            <string name="working_mode">su_disable</string>
            <boolean name="biometric_login" value="true" />
            <boolean name="auto_freeze_after_lock" value="true" />
            <string name="api_token">secret</string>
        </map>"""
        entries = legacy_backup.parse_preferences(xml)
        by_key = {item["key"]: item for item in entries}
        self.assertEqual("theme_dark", by_key["app_theme"]["value"])
        self.assertEqual("boolean", by_key["filter_system_apps"]["type"])
        self.assertEqual(14, by_key["home_font_size_i"]["value"])
        self.assertEqual(2.5, by_key["auto_freeze_delay_f"]["value"])
        self.assertEqual(["com.example.a", "com.example.b"], by_key["hidden_apps"]["value"])
        self.assertNotIn("working_mode", by_key)
        self.assertNotIn("biometric_login", by_key)
        self.assertNotIn("auto_freeze_after_lock", by_key)
        self.assertNotIn("api_token", by_key)

    def test_created_archive_contains_all_required_entries_and_valid_manifest(self):
        files = {
            "v1/tags.json": b'[{"tag":"Default","id":0}]',
            "v1/apps.json": b'[]',
            "notes/state.json": b'{"ok":true}',
        }
        preferences = [{"key": "app_theme", "type": "string", "value": "theme_dark"}]
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "migration.zip"
            count, digest = legacy_backup.create_archive(
                files, preferences, "com.aistra.hail.enhanced", output
            )
            self.assertEqual(3, count)
            self.assertEqual(64, len(digest))
            with zipfile.ZipFile(output) as archive:
                self.assertIsNone(archive.testzip())
                manifest = json.loads(archive.read("manifest.json"))
                self.assertEqual(legacy_backup.FORMAT, manifest["format"])
                self.assertEqual(3, manifest["fileCount"])
                self.assertEqual(1, manifest["preferenceCount"])
                self.assertIn("files/v1/tags.json", archive.namelist())
                self.assertIn("files/v1/apps.json", archive.namelist())

    def test_archive_creation_adds_default_state_for_fresh_legacy_install(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "fresh.zip"
            count, _ = legacy_backup.create_archive(
                {"notes/state.json": b"{}"}, [], "com.aistra.hail.enhanced", output
            )
            self.assertEqual(3, count)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(
                    b'[{"tag":"Default","id":0}]',
                    archive.read("files/v1/tags.json")
                )
                self.assertEqual(b"[]", archive.read("files/v1/apps.json"))

    def test_tar_reader_rejects_path_traversal(self):
        import io
        import tarfile
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode="w") as archive:
            info = tarfile.TarInfo("../files/v1/tags.json")
            info.size = 2
            archive.addfile(info, io.BytesIO(b"[]"))
        with self.assertRaises(ValueError):
            legacy_backup.read_tar_bytes(buffer.getvalue())


if __name__ == "__main__":
    unittest.main()
