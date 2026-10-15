import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
TEST_TMP = SCRIPTS.parent / "target" / "startup-tests"
TEST_TMP.mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(SCRIPTS))

from startup_common import (Redactor, RunLogger, StartupError, display_command, expected_volumes,
                            parse_json_bytes, read_env, validate_env, validate_ports, validate_storage)
from start_backend import build_command, validate_reset_confirmation, validate_rsa


class StartupTests(unittest.TestCase):
    def env_file(self, text):
        directory = tempfile.TemporaryDirectory(dir=TEST_TMP)
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / ".env"
        path.write_text(text, encoding="utf-8")
        return path

    def test_env_literals_comments_and_optional_empty(self):
        values = read_env(self.env_file("# x\nA=plain\nB='value$1'\nC=\n"))
        self.assertEqual(values, {"A": "plain", "B": "value$1", "C": ""})

    def test_env_rejects_duplicate_placeholder_control_and_expression(self):
        cases = ["A=1\nA=2", "A=CHANGE_ME", "DOCKER_HOST=x", "A=${B}", "export A=1"]
        for value in cases:
            with self.subTest(value=value), self.assertRaises(StartupError):
                read_env(self.env_file(value))

    def test_required_environment_fresh_and_history(self):
        values = {key: "valid-value-123" for key in ("MYSQL_ROOT_PASSWORD", "IAM_DB_PASSWORD",
            "PROJECT_DB_PASSWORD", "WORKFLOW_DB_PASSWORD", "MATERIAL_DB_PASSWORD",
            "NOTIFICATION_DB_PASSWORD", "RABBITMQ_USERNAME", "RABBITMQ_PASSWORD", "REDIS_PASSWORD",
            "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "SERVICE_TOKEN_BOOTSTRAP_SECRET",
            "JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY")}
        validate_env(values, "history")
        with self.assertRaises(StartupError):
            validate_env(values, "fresh")
        values.update(BOOTSTRAP_ADMIN_USERNAME="admin", BOOTSTRAP_ADMIN_PASSWORD="1234567890")
        validate_env(values, "fresh")

    def test_redaction(self):
        redactor = Redactor({"DB_PASSWORD": "secret-value", "NORMAL": "visible"})
        result = redactor.redact("secret-value eyJabc.def.ghi /x?token=hello&x=1 visible")
        self.assertNotIn("secret-value", result)
        self.assertNotIn("hello", result)
        self.assertNotIn("eyJabc", result)
        self.assertIn("visible", result)

    def test_json_utf8_array_and_ndjson(self):
        redactor = Redactor()
        self.assertEqual(parse_json_bytes('[{"Command":"java – 中文"}]'.encode(), "array", redactor)[0]["Command"], "java – 中文")
        data = b'{"Service":"mysql"}\n{"Service":"redis"}\n'
        self.assertEqual(len(parse_json_bytes(data, "ndjson", redactor)), 2)

    def test_json_error_contains_location_without_secret(self):
        redactor = Redactor({"PASSWORD": "top-secret"})
        with self.assertRaises(StartupError) as caught:
            parse_json_bytes(b'{"x":"top-secret",bad}', "bad", redactor)
        self.assertIn("line=", str(caught.exception))
        self.assertNotIn("top-secret", str(caught.exception))

    def test_storage_history_and_cross_project_guards(self):
        project = "guard"
        volumes = [{"Name": name, "Labels": {"com.docker.compose.project": project,
                    "com.docker.compose.volume": logical}} for logical, name in expected_volumes(project).items()]
        validate_storage(project, [], volumes, True)
        with self.assertRaises(StartupError):
            validate_storage(project, [], volumes[:-1], True)
        containers = [{"Name": "foreign", "Labels": {"com.docker.compose.project": "other"},
                       "Mounts": [{"Type": "volume", "Name": volumes[0]["Name"]}]}]
        with self.assertRaises(StartupError):
            validate_storage(project, containers, volumes, False)

    def test_container_compose_labels_are_read_from_docker_inspect_config(self):
        project = "guard"
        volumes = [{"Name": name, "Labels": {"com.docker.compose.project": project,
                    "com.docker.compose.volume": logical}} for logical, name in expected_volumes(project).items()]
        containers = [{"Name": "/guard-mysql-1", "Config": {"Labels": {
            "com.docker.compose.project": project, "com.docker.compose.service": "mysql"}},
            "Mounts": [{"Type": "volume", "Name": expected_volumes(project)["mysql-data"]}]}]
        validate_storage(project, containers, volumes, True)

    def test_command_display_keeps_argument_boundaries(self):
        rendered = display_command(["docker", "compose", "--env-file", "C:/a b/.env"])
        if os.name == "nt":
            self.assertIn('"C:/a b/.env"', rendered)
        else:
            self.assertEqual(__import__("shlex").split(rendered)[-1], "C:/a b/.env")

    def test_build_command_uses_compose_global_plain_progress(self):
        command = build_command(["docker", "compose", "-p", "guard"])
        self.assertEqual(command[4:7], ["--progress", "plain", "build"])

    def test_port_conflict_from_foreign_running_container(self):
        container = {"Name": "/foreign", "Config": {"Labels": {
            "com.docker.compose.project": "other"}}, "State": {"Running": True},
            "NetworkSettings": {"Ports": {"8080/tcp": [{"HostPort": "8080"}]}}}
        with self.assertRaises(StartupError):
            validate_ports("guard", [container])

    def test_reset_confirmation_is_exact(self):
        validate_reset_confirmation("RESET:guard", "guard")
        for value in ("reset:guard", "RESET:other", "RESET:guard "):
            with self.subTest(value=value), self.assertRaises(StartupError):
                validate_reset_confirmation(value, "guard")

    def test_rsa_pair_validation_uses_derived_public_key(self):
        marker = bytes.fromhex("300d06092a864886f70d0101010500")
        private, public = b"prefix" + marker + b"suffix", b"public-der"

        class Runner:
            def capture(self, argv):
                self.argv = argv
                return public

        runner = Runner()
        validate_rsa({"JWT_PRIVATE_KEY": __import__("base64").b64encode(private).decode(),
                      "JWT_PUBLIC_KEY": __import__("base64").b64encode(public).decode()}, runner)
        self.assertIn("openssl", runner.argv[0])

    def test_logger_writes_latest_and_rotates_to_twenty(self):
        with tempfile.TemporaryDirectory(dir=TEST_TMP) as directory:
            root = Path(directory)
            logs = root / "logs"; logs.mkdir()
            for index in range(24):
                path = logs / f"startup-20000101-000000-{index:06d}.log"
                path.write_text("old", encoding="utf-8")
                os.utime(path, (index, index))
            logger = RunLogger(root, Redactor())
            logger.write("hello"); logger.close()
            self.assertIn("hello", (logs / "latest.log").read_text(encoding="utf-8"))
            self.assertLessEqual(len(list(logs.glob("startup-*.log"))), 20)


if __name__ == "__main__":
    unittest.main()
