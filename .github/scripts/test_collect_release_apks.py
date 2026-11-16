import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect_release_apks.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class ReleaseCollectionTest(unittest.TestCase):
    def write_version(self, root, name="1.3.6", code=66):
        (root / "gradle.properties").write_text(f"lyriscopeVersionName={name}\nlyriscopeVersionCode={code}\n")

    def test_tag_cannot_advance_without_app_version(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write_version(root, "1.3.18", 78)
            with self.assertRaisesRegex(RuntimeError, "does not match configured versionName"):
                collector.release_version("v1.3.19", root)

    def test_new_release_requires_a_higher_code_than_both_previous_apks(self):
        previous = {"tag": "v1.3.19", "versionName": "1.3.18", "versionCode": 78,
                    "apks": [{"versionCode": 79}, {"versionCode": 78}]}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write_version(root, "1.3.20", 79)
            with self.assertRaisesRegex(RuntimeError, "must increase versionCode"):
                collector.release_version("v1.3.20", root, previous)
            self.write_version(root, "1.3.20", 80)
            self.assertEqual(collector.release_version("v1.3.20", root, previous),
                             {"versionName": "1.3.20", "versionCode": 80})

    def test_existing_release_rebuild_keeps_published_versions(self):
        previous = {"tag": "v1.3.20", "versionName": "1.3.20", "versionCode": 80}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write_version(root, "1.3.20", 80)
            self.assertEqual(collector.release_version("v1.3.20", root, previous)["versionCode"], 80)
            self.write_version(root, "1.3.20", 81)
            with self.assertRaisesRegex(RuntimeError, "preserve its published APK versions"):
                collector.release_version("v1.3.20", root, previous)

    def test_module_asset_never_looks_like_the_app_updater_target(self):
        for tag in ("v1.3.6", "v1.3.6-release-candidate", "v1.3.6-RELEASE.1"):
            names = collector.asset_names(tag)
            self.assertEqual(len(names), 1)
            self.assertIn("Lyriscope-Module", names[0])
            self.assertFalse(any("-release" in name.lower() for name in names))

    def test_reject_unsafe_tags_before_writing_output(self):
        for tag in ("../v1", "v1/../v2", "v1\nother", "v1$(pwd)", "v1.0-unsigned"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                collector.asset_names(tag)

    def test_missing_module_apk_creates_no_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write_version(root)
            destination = root / "release-apks"
            with self.assertRaisesRegex(RuntimeError, "Missing signed release APK"):
                collector.collect("v1.3.6", destination, root, "unused-aapt", "unused-apksigner")
            self.assertFalse(destination.exists())

    def test_module_without_a_verified_signer_is_never_published(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "module.apk"
            apk.write_bytes(b"fixture")
            with patch.object(collector, "output", return_value="no signer lines here"):
                with self.assertRaisesRegex(RuntimeError, "No verified signing certificate"):
                    collector.inspect_apk(apk, "dev.lyriscope.spotify", set(), "aapt", "apksigner")

    def test_actual_apk_inspection_rejects_wrong_package_permissions_and_debuggable(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            apk.write_bytes(b"fixture")
            signer = "Signer #1 certificate SHA-256 digest: aabb"
            for badging, permissions in (
                ("package: name='wrong.package' versionCode='1'", ""),
                ("package: name='expected.package' versionCode='1'\napplication-debuggable", ""),
                ("package: name='expected.package' versionCode='1'", "uses-permission: name='unexpected.permission'"),
            ):
                with patch.object(collector, "output", side_effect=[signer, badging, permissions]):
                    with self.assertRaises(RuntimeError):
                        collector.inspect_apk(apk, "expected.package", set(), "aapt", "apksigner")

    def test_module_is_collected_with_checksums(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write_version(root)
            # The standalone build is not required to exist: it is no longer published.
            path = root / "spotify-module/build/outputs/apk/release/spotify-module-release.apk"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"module")
            def inspected(path, package, permissions, *tools):
                return {"packageName": package, "versionName": "1.3.6", "versionCode": 66, "signers": ["aabb"]}
            with patch.object(collector, "inspect_apk", side_effect=inspected):
                reports = collector.collect("v1.3.6", root / "out", root, "aapt", "apksigner")
            self.assertEqual([row["product"] for row in reports], ["spotify-module"])
            self.assertEqual([p.name for p in (root / "out").glob("*.apk")], ["Lyriscope-Module-v1.3.6.apk"])
            self.assertTrue(all(len(row["sha256"]) == 64 for row in reports))
            self.assertFalse(list((root / "out").glob("*version.json")))

    def test_stale_module_version_publishes_nothing(self):
        for stale in ({"versionName": "1.3.18", "versionCode": 80},
                      {"versionName": "1.3.20", "versionCode": 78}):
            with self.subTest(stale=stale), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                self.write_version(root, "1.3.20", 80)
                valid = {"versionName": "1.3.20", "versionCode": 80, "signers": ["aabb"]}
                with patch.object(collector, "inspect_apk", side_effect=[{**valid, **stale}]):
                    with self.assertRaisesRegex(RuntimeError, "spotify-module APK version"):
                        collector.collect("v1.3.20", root / "out", root, "aapt", "apksigner")
                self.assertFalse((root / "out").exists())

    def test_old_and_new_apksigner_output_formats(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            apk.write_bytes(b"fixture")
            for label in ("Signer #1", "V2 Signer:"):
                with patch.object(collector, "output", side_effect=[
                    label + " certificate SHA-256 digest: aabb",
                    "package: name='expected.package' versionCode='1' versionName='1.0'", ""
                ]):
                    info = collector.inspect_apk(apk, "expected.package", set(), "aapt", "apksigner")
                    self.assertEqual(info["signers"], ["aabb"])


if __name__ == "__main__":
    unittest.main()
