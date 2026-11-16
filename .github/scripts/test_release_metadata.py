"""Offline release metadata checks; APK bytes are synthetic hash fixtures."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import generate_release_metadata as release


REPOSITORY = Path(__file__).resolve().parents[2]


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.original_cwd = Path.cwd()
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(os.chdir, self.original_cwd)
        os.chdir(self.directory.name)
        for project, package_name, version_name, version_code in (
            ("app", "dev.lyriscope.app", "1.3.6", 66),
            ("spotify-module", "dev.lyriscope.spotify", "1.0.5", 6),
        ):
            Path(project).mkdir()
            Path(project, "build.gradle").write_text(
                "android { defaultConfig {\n"
                f'    applicationId "{package_name}"\n'
                f'    versionName "{version_name}"\n'
                f"    versionCode {version_code}\n"
                "} }\n",
                encoding="utf-8",
            )
        Path("release-apks").mkdir()
        self.payloads = {
            "Lyriscope-Android-v1.3.6-release.apk": b"standalone APK fixture",
            "Lyriscope-Spotify-v1.3.6.apk": b"Spotify module APK fixture",
        }
        for name, payload in self.payloads.items():
            Path("release-apks", name).write_bytes(payload)
        Path(".github").mkdir()
        Path(".github/release-notes-template.md").write_text(
            (REPOSITORY / ".github/release-notes-template.md").read_text(encoding="utf-8"),
            encoding="utf-8",
        )

    def assets_by_product(self):
        return {asset["product"]: asset for asset in release.apk_assets("release-apks")}

    def use_shared_versions(self):
        Path("gradle.properties").write_text(
            "# Release identity shared by both APKs\n"
            "lyriscopeVersionName=1.3.8\n"
            "lyriscopeVersionCode=68\n", encoding="utf-8")
        for project in ("app", "spotify-module"):
            path = Path(project, "build.gradle")
            text = path.read_text(encoding="utf-8")
            text = release.re.sub(r'versionName "[^"]+"',
                'versionName providers.gradleProperty("lyriscopeVersionName").get()', text)
            text = release.re.sub(r"versionCode [0-9]+",
                'versionCode providers.gradleProperty("lyriscopeVersionCode").get().toInteger()', text)
            path.write_text(text, encoding="utf-8")

    def test_products_keep_independent_package_and_version(self):
        assets = self.assets_by_product()
        self.assertEqual({"standalone", "spotify-module"}, set(assets))
        standalone, module = assets["standalone"], assets["spotify-module"]
        self.assertEqual("dev.lyriscope.app", standalone["packageName"])
        self.assertEqual(("1.3.6", 66), (standalone["versionName"], standalone["versionCode"]))
        self.assertEqual("dev.lyriscope.spotify", module["packageName"])
        self.assertEqual(("1.0.5", 6), (module["versionName"], module["versionCode"]))
        self.assertEqual("Lyriscope Module (LSPosed)", module["label"])
        self.assertNotIn("-release", module["name"].lower())

    def test_existing_asset_fields_and_checksums_are_preserved(self):
        for asset in release.apk_assets("release-apks"):
            with self.subTest(asset=asset["name"]):
                payload = self.payloads[asset["name"]]
                self.assertEqual(str(Path("release-apks", asset["name"])), asset["path"])
                self.assertEqual(len(payload), asset["size"])
                self.assertEqual(hashlib.sha256(payload).hexdigest(), asset["sha256"])

    def test_default_gradle_version_remains_standalone(self):
        self.assertEqual({"versionName": "1.3.6", "versionCode": 66}, release.read_gradle_version())
        self.assertEqual({"versionName": "1.0.5", "versionCode": 6},
                         release.read_gradle_version("spotify-module/build.gradle"))

    def test_shared_release_version_preserves_both_package_identities(self):
        self.use_shared_versions()
        assets = self.assets_by_product()
        for product, package in (("standalone", "dev.lyriscope.app"),
                                 ("spotify-module", "dev.lyriscope.spotify")):
            with self.subTest(product=product):
                self.assertEqual(package, assets[product]["packageName"])
                self.assertEqual(("1.3.8", 68),
                                 (assets[product]["versionName"], assets[product]["versionCode"]))
        # Changing the one shared source changes both products in the next metadata run.
        Path("gradle.properties").write_text(
            "lyriscopeVersionName = 1.3.9\nlyriscopeVersionCode : 69\n", encoding="utf-8")
        for asset in self.assets_by_product().values():
            self.assertEqual(("1.3.9", 69), (asset["versionName"], asset["versionCode"]))

    def test_shared_properties_do_not_override_historical_product_literals(self):
        Path("gradle.properties").write_text(
            "lyriscopeVersionName=99.9.9\nlyriscopeVersionCode=999\n", encoding="utf-8")
        self.test_products_keep_independent_package_and_version()

    def test_absolute_gradle_path_uses_its_project_properties_not_working_directory(self):
        self.use_shared_versions()
        path = Path("spotify-module/build.gradle").resolve()
        Path("unrelated").mkdir()
        os.chdir("unrelated")
        Path("gradle.properties").write_text(
            "lyriscopeVersionName=99.9.9\nlyriscopeVersionCode=999\n", encoding="utf-8")
        self.assertEqual({"versionName": "1.3.8", "versionCode": 68}, release.read_gradle_version(path))

    def test_missing_or_invalid_shared_version_fails_instead_of_publishing_unknown(self):
        self.use_shared_versions()
        for properties in ("lyriscopeVersionName=1.3.8\n", "lyriscopeVersionCode=68\n",
                           "lyriscopeVersionName=1.3.8\nlyriscopeVersionCode=invalid\n"):
            with self.subTest(properties=properties):
                Path("gradle.properties").write_text(properties, encoding="utf-8")
                with self.assertRaises(ValueError):
                    release.read_gradle_version()
        Path("gradle.properties").unlink()
        with self.assertRaises(ValueError):
            release.read_gradle_version()

    def test_current_checked_in_products_use_the_same_release_version(self):
        for relative in ("gradle.properties", "app/build.gradle", "spotify-module/build.gradle"):
            Path(relative).write_text((REPOSITORY / relative).read_text(encoding="utf-8"), encoding="utf-8")
        properties = dict(line.split("=", 1) for line in Path("gradle.properties").read_text().splitlines()
                          if line.startswith(("lyriscopeVersionName=", "lyriscopeVersionCode=")))
        expected = (properties["lyriscopeVersionName"], int(properties["lyriscopeVersionCode"]))
        assets = self.assets_by_product()
        self.assertEqual({"dev.lyriscope.app", "dev.lyriscope.spotify"},
                         {asset["packageName"] for asset in assets.values()})
        self.assertEqual({expected}, {(asset["versionName"], asset["versionCode"]) for asset in assets.values()})

    def test_unsupported_property_arithmetic_fails_instead_of_misreporting_version(self):
        self.use_shared_versions()
        path = Path("app/build.gradle")
        original = path.read_text(encoding="utf-8")
        for expression, replacement in (
            ('gradleProperty("lyriscopeVersionName").get()', 'gradleProperty("lyriscopeVersionName").get() + "-beta"'),
            ('gradleProperty("lyriscopeVersionCode").get().toInteger()', 'gradleProperty("lyriscopeVersionCode").get().toInteger() + 1'),
        ):
            with self.subTest(expression=expression):
                path.write_text(original.replace(expression, replacement), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "Unsupported"):
                    release.read_gradle_version()

    def test_unknown_asset_does_not_inherit_either_product_identity(self):
        Path("release-apks/other.apk").write_bytes(b"other")
        asset = self.assets_by_product()["unknown"]
        self.assertEqual("", asset["packageName"])
        self.assertEqual("", asset["versionName"])
        self.assertIsNone(asset["versionCode"])

    def test_download_descriptions_distinguish_products_in_both_languages(self):
        assets = list(self.assets_by_product().values())
        for language, app_phrase, module_phrase in (
            ("zh", "独立 Android 应用", "Spotify 的 LSPosed 模块"),
            ("en", "Standalone Android app", "Spotify module for LSPosed"),
        ):
            with self.subTest(language=language):
                lines = release.asset_downloads(assets, language)
                app_line = next(line for line in lines if "Lyriscope-Android-" in line)
                module_line = next(line for line in lines if "Lyriscope-Spotify-" in line)
                self.assertIn(app_phrase, app_line)
                self.assertIn(module_phrase, module_line)
                self.assertIn("v1.3.6 (66)", app_line)
                self.assertIn("v1.0.5 (6)", module_line)
                self.assertNotIn("v1.3.6", module_line.split(": ", 1)[1])

    def test_legacy_asset_records_still_render_and_keep_signature_description(self):
        records = [{"name": "Lyriscope-Spotify-v1.3.6.apk"},
                   {"name": "Lyriscope-Android-v1.3.6-release-unsigned.apk"}]
        lines = release.asset_downloads(records, "en")
        self.assertIn("Spotify module for LSPosed", lines[0])
        self.assertIn("Unsigned release APK", lines[1])

    def test_main_writes_one_android_version_manifest_with_both_products(self):
        self.check_main_manifest("1.3.6", 66, "1.0.5", 6)

    def test_main_writes_shared_version_for_both_products_and_download_notes(self):
        self.use_shared_versions()
        self.check_main_manifest("1.3.8", 68, "1.3.8", 68)

    def check_main_manifest(self, version_name, version_code, module_version_name, module_version_code):
        tag = f"v{version_name}"
        commits = [{"hash": "abc1234", "subject": "build: include Spotify module APK",
                    "body": "", "files": []}]
        with mock.patch.dict(os.environ, {"RELEASE_TAG": tag}, clear=True), \
                mock.patch.multiple(release,
                    resolve_commit=mock.Mock(return_value="a" * 40),
                    previous_tag=mock.Mock(return_value="v1.3.5"),
                    resolve_range_ref=mock.Mock(return_value=tag),
                    git_diff_stat=mock.Mock(return_value=""),
                    release_commits=mock.Mock(return_value=commits),
                    run_git=mock.Mock(side_effect=AssertionError("unexpected git invocation"))), \
                mock.patch.object(release.urllib.request, "urlopen",
                                  side_effect=AssertionError("network forbidden")), \
                contextlib.redirect_stdout(io.StringIO()):
            release.main()

        manifests = list(Path("release-metadata").glob("*version.json"))
        # One manifest per release, named after the release: it covers every APK in it.
        self.assertEqual([f"Lyriscope-{tag}-version.json"], [path.name for path in manifests])
        metadata = json.loads(manifests[0].read_text(encoding="utf-8"))
        self.assertEqual((version_name, version_code), (metadata["versionName"], metadata["versionCode"]))
        self.assertEqual(set(self.payloads), {asset["name"] for asset in metadata["apks"]})
        self.assertEqual({"standalone", "spotify-module"}, {asset["product"] for asset in metadata["apks"]})
        products = {asset["product"]: asset for asset in metadata["apks"]}
        self.assertEqual((module_version_name, module_version_code),
                         (products["spotify-module"]["versionName"], products["spotify-module"]["versionCode"]))
        self.assertEqual("dev.lyriscope.app", products["standalone"]["packageName"])
        self.assertEqual("dev.lyriscope.spotify", products["spotify-module"]["packageName"])
        notes = Path("release-metadata/release-notes.md").read_text(encoding="utf-8")
        self.assertIn("Spotify 的 LSPosed 模块", notes)
        self.assertIn("Spotify module for LSPosed", notes)
        self.assertIn(f"v{version_name} ({version_code})", notes)
        self.assertIn(f"v{module_version_name} ({module_version_code})", notes)
        for payload in self.payloads.values():
            self.assertIn(hashlib.sha256(payload).hexdigest(), notes)


if __name__ == "__main__":
    unittest.main()
