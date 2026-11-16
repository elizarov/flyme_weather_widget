"""Signing configuration tests use temporary dummy keys, never the release secret."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("weather_signing", Path(__file__).resolve().parents[1] / "signing.py")
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningTest(unittest.TestCase):
    def test_initialization_reuses_key_and_keeps_secrets_out_of_arguments(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "signing"
            def generate(command, **kwargs):
                Path(command[command.index("-keystore") + 1]).write_bytes(b"dummy-keystore")
                self.assertNotIn("test-password", command)
                self.assertIn("-storepass:env", command)
                self.assertEqual(kwargs["env"]["WEATHER_STORE_PASSWORD"], "test-password")
            with patch.object(signing, "SIGNING", root), patch.object(signing, "CONFIG", root / "release.properties"), \
                    patch.object(signing.secrets, "token_hex", return_value="test-password"), \
                    patch.object(signing.subprocess, "run", side_effect=generate) as run:
                signing.initialize()
                before = (root / "release.keystore").read_bytes()
                signing.initialize()
                self.assertEqual(run.call_count, 1)
                self.assertEqual((root / "release.keystore").read_bytes(), before)
                self.assertEqual((root / "release.keystore").stat().st_mode & 0o777, 0o600)
                self.assertEqual((root / "release.properties").stat().st_mode & 0o777, 0o600)

    def test_orphaned_key_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "release.keystore").write_bytes(b"existing")
            with patch.object(signing, "SIGNING", root), patch.object(signing, "CONFIG", root / "release.properties"), \
                    patch.object(signing.subprocess, "run") as run:
                with self.assertRaises(ValueError):
                    signing.initialize()
                run.assert_not_called()
                self.assertEqual((root / "release.keystore").read_bytes(), b"existing")

    def test_overlay_signer_uses_the_configured_release_key(self):
        config = {"storeFile": "/dummy/release.keystore", "storeType": "PKCS12", "keyAlias": "weather-widget",
                  "storePassword": "store-secret", "keyPassword": "key-secret"}
        with patch.object(signing, "read_config", return_value=config), patch.object(signing.subprocess, "run") as run:
            signing.sign(Path("apksigner"), Path("unsigned.apk"), Path("overlay.apk"))
            arguments = run.call_args.args[0]
            self.assertIn("/dummy/release.keystore", arguments)
            self.assertNotIn("store-secret", arguments)
            self.assertNotIn("key-secret", arguments)
            self.assertEqual(run.call_args.kwargs["env"]["WEATHER_KEY_PASSWORD"], "key-secret")


if __name__ == "__main__":
    unittest.main()
