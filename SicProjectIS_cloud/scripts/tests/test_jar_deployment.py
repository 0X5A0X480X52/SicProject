import argparse
import hashlib
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_verification_env as generator
import wait_verification_ready as readiness
from deployment_settings import eureka_ui, check_ui_bindings, check_running_ui_bindings
from replica_common import SERVICES
from startup_common import StartupError

TEST_TMP = Path(__file__).resolve().parents[2] / "target/startup-tests"
TEST_TMP.mkdir(parents=True, exist_ok=True)


class JarDeploymentTests(unittest.TestCase):
    def fixture(self):
        directory = tempfile.TemporaryDirectory(dir=TEST_TMP)
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        release = root / "release"
        release.mkdir()
        manifest = {"applications": {}}
        for name in generator.APPLICATIONS:
            jar = release / name / "application.jar"
            jar.parent.mkdir(); jar.write_bytes(name.encode())
            manifest["applications"][name] = {"file": f"{name}/application.jar",
                "sha256": hashlib.sha256(jar.read_bytes()).hexdigest()}
        (release / "release-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        env_file = root / "existing.env"
        env_file.write_text("JWT_PRIVATE_KEY=keep-original-key\nBOOTSTRAP_ADMIN_PASSWORD=keep-original-password\n"
                            "SIC_PROJECT_REPLICAS=3\n", encoding="utf-8")
        override = root / "existing.compose.yaml"
        return root, release, env_file, override

    def run_generator(self, arguments):
        with patch("sys.argv", ["prepare_verification_env.py", *map(str, arguments)]), redirect_stdout(io.StringIO()):
            return generator.main()

    def test_update_reuses_frozen_mounts_and_credentials_and_backs_up_old_override(self):
        root, release, env_file, override = self.fixture()
        self.run_generator(["--override-only", "--env-file", env_file, "--compose-override", override, "--release-dir", release])
        old_override = override.read_bytes()
        env_file.write_text(env_file.read_text(encoding="utf-8") + "EUREKA_UI_ENABLED=true\n", encoding="utf-8")
        credentials = env_file.read_bytes()
        with patch.object(generator.rsa, "generate_private_key") as generate:
            self.run_generator(["--update-override", "--env-file", env_file, "--compose-override", override])
            generate.assert_not_called()
        self.assertEqual(env_file.read_bytes(), credentials)
        backups = list(root.glob("existing.compose.yaml.backup-*"))
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_bytes(), old_override)
        text = override.read_text(encoding="utf-8")
        self.assertIn("EUREKA_UI_ENABLED=true", text)
        self.assertIn("host_ip:", text)
        recovered = generator.existing_jars(override, env_file)
        self.assertEqual(recovered["project-service"], (release / "project-service/application.jar").resolve())
        self.assertNotIn("keep-original", text)
        self.assertEqual(sum("discovery-server" in value and "volumes:" in value for value in text.splitlines()), 2)

    def test_update_can_read_legacy_generator_output_without_switching_jars(self):
        _, release, env_file, override = self.fixture()
        self.run_generator(["--override-only", "--env-file", env_file, "--compose-override", override, "--release-dir", release])
        legacy = "\n".join(line for line in override.read_text(encoding="utf-8").splitlines() if not line.startswith("#")) + "\n"
        override.write_text(legacy, encoding="utf-8")
        self.run_generator(["--update-override", "--env-file", env_file, "--compose-override", override])
        self.assertEqual(generator.existing_jars(override, env_file)["discovery-server"],
                         (release / "discovery-server/application.jar").resolve())

    def test_invalid_settings_or_tampered_release_do_not_change_existing_files(self):
        root, release, env_file, override = self.fixture()
        self.run_generator(["--override-only", "--env-file", env_file, "--compose-override", override, "--release-dir", release])
        original = override.read_bytes()
        env_file.write_text(env_file.read_text(encoding="utf-8") + "EUREKA_UI_ENABLED=yes\n", encoding="utf-8")
        credentials = env_file.read_bytes()
        with self.assertRaisesRegex(StartupError, "true or false"):
            self.run_generator(["--update-override", "--env-file", env_file, "--compose-override", override])
        self.assertEqual(override.read_bytes(), original)
        self.assertEqual(env_file.read_bytes(), credentials)
        self.assertEqual(list(root.glob("*.backup-*")), [])
        (release / "project-service/application.jar").write_bytes(b"tampered")
        with self.assertRaisesRegex(StartupError, "hash differs"):
            self.run_generator(["--update-override", "--env-file", env_file, "--compose-override", override])
        self.assertEqual(override.read_bytes(), original)

    def test_ui_values_are_explicit_and_cannot_create_random_or_conflicting_ports(self):
        self.assertFalse(eureka_ui({}).enabled)
        self.assertTrue(eureka_ui({"EUREKA_UI_ENABLED": "true"}).enabled)
        for values in ({"EUREKA_UI_ENABLED": "0"}, {"EUREKA_UI_PORT_A": "0"},
                       {"EUREKA_UI_BIND_IP": "localhost"},
                       {"EUREKA_UI_ENABLED": "true", "EUREKA_UI_PORT_B": "8761"},
                       {"EUREKA_UI_ENABLED": "true", "EUREKA_UI_PORT_A": "8080"}):
            with self.subTest(values=values), self.assertRaises(StartupError):
                eureka_ui(values)

    def test_stale_ui_configuration_and_stale_running_bindings_are_rejected(self):
        config = {"services": {node: {} for node in ("discovery-server", "discovery-peer")}}
        check_ui_bindings(config, {})
        with self.assertRaisesRegex(StartupError, "update-override"):
            check_ui_bindings(config, {"EUREKA_UI_ENABLED": "true"})
        containers = [{"Config": {"Labels": {"com.docker.compose.service": node}}, "State": {"Running": True},
                       "NetworkSettings": {"Ports": {"8761/tcp": None}}} for node in config["services"]]
        check_running_ui_bindings(containers, eureka_ui({}))
        with self.assertRaisesRegex(StartupError, "Compose up"):
            check_running_ui_bindings(containers, eureka_ui({"EUREKA_UI_ENABLED": "true"}))

    def test_readiness_failure_on_missing_replica_precedes_business_login(self):
        directory = tempfile.TemporaryDirectory(dir=TEST_TMP)
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        env_file, report_path = root / "test.env", root / "report.json"
        env_file.write_text("BOOTSTRAP_ADMIN_USERNAME=admin\nBOOTSTRAP_ADMIN_PASSWORD=private-password\n", encoding="utf-8")
        deployment = Mock()
        deployment.replicas = {name: 1 for name in SERVICES}
        deployment.replicas["project-service"] = 3
        deployment.config = {"services": {"discovery-server": {}, "discovery-peer": {}}}
        deployment.summary.return_value = {"replicas": deployment.replicas}
        arguments = ["wait_verification_ready.py", "--project-name", "test", "--env-file", str(env_file),
                     "--report-path", str(report_path), "--registry-stable-seconds", "0"]
        with patch.object(readiness, "ROOT", root), patch("sys.argv", arguments), \
             patch.object(readiness, "compose_files", return_value=[]), \
             patch.object(readiness, "load_deployment", return_value=deployment), \
             patch.object(readiness, "check_artifacts"), \
             patch.object(readiness, "inspect_project", return_value=[]), \
             patch.object(readiness, "wait_instances", side_effect=StartupError("project-service expected 3, actual 2")), \
             patch.object(readiness.requests, "Session") as session, redirect_stdout(io.StringIO()) as output:
            self.assertEqual(readiness.main(), 1)
            session.assert_not_called()
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertFalse(report["passed"])
        self.assertEqual(report["replicas"]["project-service"], 3)
        self.assertIn("expected 3", report["error"])
        self.assertNotIn("private-password", output.getvalue())

    def test_read_routes_retry_unavailable_but_reject_false_success(self):
        session = Mock()
        good = Mock(status_code=200, headers={"X-Trace-Id": "example"})
        good.json.return_value = {"success": True}
        session.get.side_effect = [Mock(status_code=503), good, good, good, Mock(status_code=403, headers={})]
        report = {"checks": []}
        with patch.object(readiness.time, "sleep"):
            readiness.read_routes(session, "http://127.0.0.1", float("inf"), report)
        self.assertEqual(len(report["checks"]), 4)
        good.json.return_value = {"success": False}
        session.get.side_effect = None; session.get.return_value = good
        with self.assertRaisesRegex(StartupError, "unsuccessful"):
            readiness.read_routes(session, "http://127.0.0.1", float("inf"), {"checks": []})


if __name__ == "__main__":
    unittest.main()
