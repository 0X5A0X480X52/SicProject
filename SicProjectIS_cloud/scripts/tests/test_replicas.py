import argparse
import json
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from replica_common import (SERVICES, BUSINESS, resolve_replicas, check_container_health, check_registry,
                            compose_files, compose_command, positive_count, strict_json, validate_deployment_ports)
from startup_common import StartupError


class ReplicaTests(unittest.TestCase):
    def test_stopped_project_port_release_is_bounded_and_foreign_container_is_rejected(self):
        container = {"Config": {"Labels": {"com.docker.compose.project": "own"}}, "State": {"Running": False},
                     "HostConfig": {"PortBindings": {"8080/tcp": [{"HostPort": "18080"}]}}}
        config = {"services": {"gateway-service": {"ports": [{"published": "18080", "host_ip": "127.0.0.1"}]}}}
        first, second = Mock(), Mock()
        first.bind.side_effect = OSError(10048, "temporarily occupied")
        with patch("replica_common.socket.socket", side_effect=[first, second]), patch("replica_common.time.sleep"):
            validate_deployment_ports("own", [container], config)
        first.close.assert_called_once(); second.close.assert_called_once()
        container["State"]["Running"] = True
        container["NetworkSettings"] = {"Ports": {"8080/tcp": [{"HostPort": "18080"}]}}
        with self.assertRaisesRegex(StartupError, "another project"):
            validate_deployment_ports("other", [container], config)
        container["State"]["Running"] = False
        busy = Mock(); busy.bind.side_effect = OSError(10048, "still occupied")
        with patch("replica_common.socket.socket", return_value=busy), patch("replica_common.time.monotonic", side_effect=[0, 21]):
            with self.assertRaises(StartupError):
                validate_deployment_ports("own", [container], config)

    def test_default_and_per_service_precedence(self):
        config = {"services": {"project-service": {"deploy": {"replicas": 2}}, "workflow-service": {"scale": 2}}}
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2] / "target/startup-tests") as directory:
            file = Path(directory) / "replicas.json"
            file.write_text(json.dumps({"project-service": 5, "iam-service": 2}))
            counts, sources = resolve_replicas(config, {"SIC_PROJECT_REPLICAS": "4"}, {"SIC_PROJECT_REPLICAS": "3"}, file,
                                               ["project-service=6"])
            self.assertEqual(counts["project-service"], 6)
            self.assertEqual(sources["project-service"], "command-line")
            self.assertEqual(counts["workflow-service"], 2)
            self.assertEqual(counts["iam-service"], 2)
            self.assertEqual(counts["material-service"], 1)
        self.assertEqual(resolve_replicas({}, {}, {})[0], {service: 1 for service in SERVICES})

    def test_invalid_counts_unknown_services_and_fixed_infrastructure(self):
        for value in (True, False, 0, -1, 1.0, "", "2.5", "01", " 2"):
            with self.subTest(value=value), self.assertRaises(StartupError):
                positive_count(value, "count")
        for overrides in (["mysql=2"], ["gateway-service=2"], ["unknown=2"], ["project-service=2", "project-service=3"]):
            with self.subTest(overrides=overrides), self.assertRaises(StartupError):
                resolve_replicas({}, {}, {}, cli=overrides)
        with self.assertRaises(StartupError):
            resolve_replicas({}, {"SIC_PROJET_REPLICAS": "2"}, {})
        with self.assertRaises(StartupError):
            strict_json('{"project-service":2,"project-service":3}')

    def test_compose_overrides_keep_base_and_argument_boundaries(self):
        root = Path(__file__).resolve().parents[2]
        paths = compose_files(root, [root / "docker-compose.yml"])
        self.assertEqual(paths, [root / "docker-compose.yml"])
        command = compose_command(root, Path("C:/credentials with spaces.env"), "isolated", paths)
        self.assertIn(str(Path("C:/credentials with spaces.env")), command)

    @staticmethod
    def container(index, healthy=True):
        return {"Id": f"id{index}", "Name": f"project-{index}", "Config": {"Hostname": f"host{index}", "Labels": {
            "com.docker.compose.service": "project-service"}}, "State": {"Running": True, "Health": {
                "Status": "healthy" if healthy else "unhealthy"}}, "NetworkSettings": {"Networks": {"default": {"IPAddress": f"172.22.0.{index}"}}}}

    def test_every_replica_is_checked_instead_of_last_service_entry(self):
        containers = [self.container(1, False), self.container(2)]
        with self.assertRaises(StartupError):
            check_container_health(containers, {"project-service": 2})
        with self.assertRaises(StartupError):
            check_container_health([self.container(1)], {"project-service": 2})
        self.assertEqual(len(check_container_health([self.container(1), self.container(2)], {"project-service": 2})), 2)

    def test_registry_counts_unique_ids_and_actual_container_addresses(self):
        containers, applications, expected = [], [], {service: 1 for service in SERVICES}
        for index, service in enumerate([*BUSINESS, "gateway-service"], 1):
            container = self.container(index)
            container["Config"]["Labels"]["com.docker.compose.service"] = service
            containers.append(container)
            port = {"iam-service": 9001, "project-service": 9002, "workflow-service": 9003,
                    "material-service": 9004, "notification-service": 9005, "gateway-service": 8080}[service]
            applications.append({"name": service.upper(), "instance": [{"instanceId": f"host{index}:{service}",
                "ipAddr": f"172.22.0.{index}", "port": {"$": port}, "status": "UP"}]})
        registry = {"applications": {"application": applications}}
        self.assertEqual(len(check_registry(containers, registry, expected)), 6)
        applications[1]["instance"].append(dict(applications[1]["instance"][0]))
        expected["project-service"] = 2
        with self.assertRaisesRegex(StartupError, "Duplicate"):
            check_registry(containers, registry, expected)
        applications[1]["instance"].pop()
        second = self.container(20)
        containers.append(second)
        applications[1]["instance"].append(dict(applications[1]["instance"][0], instanceId="different-id-same-address"))
        with self.assertRaisesRegex(StartupError, "one-to-one"):
            check_registry(containers, registry, expected)
        applications[1]["instance"].pop()
        containers.pop()
        expected["project-service"] = 1
        applications[1]["instance"][0]["ipAddr"] = "10.0.0.99"
        with self.assertRaisesRegex(StartupError, "does not match"):
            check_registry(containers, registry, expected)


if __name__ == "__main__":
    unittest.main()
