import argparse
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_eureka_ha import HAVerifier
from startup_common import StartupError
import prepare_verification_env


class EurekaHATests(unittest.TestCase):
    def test_override_only_preserves_credentials_and_reuses_one_frozen_discovery_jar(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2] / "target/startup-tests") as directory:
            root = Path(directory)
            env_file = root / "existing.env"
            env_file.write_text("ORIGINAL_SECRET=keep-this-value\n", encoding="utf-8")
            release = root / "release"
            release.mkdir()
            manifest = {"applications": {}}
            for name in prepare_verification_env.APPLICATIONS:
                jar = release / name / "application.jar"
                jar.parent.mkdir(); jar.write_bytes(name.encode())
                manifest["applications"][name] = {"file": f"{name}/application.jar",
                    "sha256": __import__("hashlib").sha256(jar.read_bytes()).hexdigest()}
            (release / "release-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            override = root / "ha.compose.yaml"
            arguments = ["prepare_verification_env.py", "--override-only", "--release-dir", str(release),
                         "--env-file", str(env_file), "--compose-override", str(override)]
            with patch.object(prepare_verification_env, "ROOT", root), patch("sys.argv", arguments), \
                 patch.object(prepare_verification_env.rsa, "generate_private_key") as generate:
                self.assertEqual(prepare_verification_env.main(), 0)
                generate.assert_not_called()
                with self.assertRaises(FileExistsError):
                    prepare_verification_env.main()
            self.assertEqual(env_file.read_text(encoding="utf-8"), "ORIGINAL_SECRET=keep-this-value\n")
            text = override.read_text(encoding="utf-8")
            self.assertIn("  discovery-peer:", text)
            mounts = [json.loads(line.partition("volumes: ")[2]) for line in text.splitlines() if "volumes: " in line]
            discovery_sources = [mount[0]["source"] for mount in mounts if "discovery-server" in mount[0]["source"]]
            self.assertEqual(discovery_sources, [str((release / "discovery-server/application.jar").resolve())] * 2)

    def verifier(self):
        args = argparse.Namespace(timeout=30, registry_only=True, observation_seconds=30)
        deployment = Mock(project="test-ha", root=Path(__file__).resolve().parents[2])
        deployment.summary.return_value = {"artifacts": {"discovery-server": {"jar": "C:/release/application.jar"}}}
        runner = Mock()
        report = {"passed": False, "checks": [], "faults": []}
        verifier = HAVerifier(args, deployment, runner, report)
        self.addCleanup(verifier.session.close)
        return verifier

    def test_fresh_probe_has_unique_hostname_and_no_discovery_service_alias(self):
        verifier = self.verifier()
        source = {"Config": {"Labels": {"com.docker.compose.service": "discovery-server"},
                             "Entrypoint": ["java", "-jar", "/application/application.jar"], "Image": "test-image"},
                  "NetworkSettings": {"Networks": {"test-ha_default": {}}}}
        verifier.runner.capture.side_effect = [b"probe-id", b'[{"NetworkSettings":{"Ports":{"8761/tcp":[{"HostIp":"127.0.0.1","HostPort":"49123"}]}}}]']
        with patch("verify_eureka_ha.inspect_project", return_value=[source]):
            name, base = verifier.start_probe("http://discovery-server:8761/eureka/")
        command = verifier.runner.capture.call_args_list[0].args[0]
        self.assertEqual(command[:3], ["docker", "run", "-d"])
        self.assertEqual(command[command.index("--hostname") + 1], name)
        self.assertIn("com.docker.compose.service=sic-ha-probe", command)
        self.assertNotIn("--network-alias", command)
        self.assertEqual(base, "http://127.0.0.1:49123")

    def test_failed_probe_check_still_restores_stopped_node_and_removes_probe(self):
        verifier = self.verifier()
        container = {"Id": "node-id", "Name": "node", "Config": {"Labels": {
            "com.docker.compose.service": "discovery-server"}}, "State": {"Running": True}}
        verifier.replication = Mock()
        verifier.baseline = Mock(return_value={})
        def wait(action, description):
            if action is verifier.baseline:
                return {}
            raise StartupError("probe failed")
        verifier.wait = wait
        def start_probe(urls):
            verifier.probes.add("test-probe")
            return "test-probe", "http://127.0.0.1:12345"
        verifier.start_probe = start_probe
        verifier.remove_probe = Mock(side_effect=lambda name: verifier.probes.discard(name))
        with patch("verify_eureka_ha.inspect_project", return_value=[container]), \
             patch("verify_eureka_ha.owned_container", return_value=True), \
             patch("verify_eureka_ha.check_artifacts"):
            with self.assertRaisesRegex(StartupError, "probe failed"):
                verifier.execute({})
        commands = [call.args[0] for call in verifier.runner.stream.call_args_list]
        self.assertIn(["docker", "stop", "--time", "0", "node-id"], commands)
        self.assertIn(["docker", "start", "node-id"], commands)
        verifier.remove_probe.assert_called_once_with("test-probe")
        self.assertFalse(verifier.report["passed"])
        self.assertEqual(verifier.report["cleanupErrors"], [])
        self.assertFalse(verifier.stopped)
        self.assertFalse(verifier.probes)


if __name__ == "__main__":
    unittest.main()
