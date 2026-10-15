"""Shared validation for the host ports generated for JAR deployments."""
from __future__ import annotations

import ipaddress
import re
from dataclasses import dataclass

from startup_common import StartupError


def host_port(value, name):
    if not re.fullmatch(r"[1-9][0-9]*", str(value)) or not 1024 <= int(value) <= 65535:
        raise StartupError(f"{name} must be an integer between 1024 and 65535")
    return int(value)


@dataclass(frozen=True)
class EurekaUI:
    enabled: bool
    bind_ip: str
    port_a: int
    port_b: int

    def summary(self):
        return {"enabled": self.enabled, "bindIp": self.bind_ip, "ports": [self.port_a, self.port_b]}


def eureka_ui(values):
    flag = str(values.get("EUREKA_UI_ENABLED", "false")).lower()
    if flag not in ("true", "false"):
        raise StartupError("EUREKA_UI_ENABLED must be true or false")
    bind_ip = values.get("EUREKA_UI_BIND_IP", "127.0.0.1")
    try:
        ipaddress.ip_address(bind_ip)
    except ValueError as exc:
        raise StartupError("EUREKA_UI_BIND_IP must be an IPv4 or IPv6 address") from exc
    port_a = host_port(values.get("EUREKA_UI_PORT_A", "8761"), "EUREKA_UI_PORT_A")
    port_b = host_port(values.get("EUREKA_UI_PORT_B", "8762"), "EUREKA_UI_PORT_B")
    gateway = host_port(values.get("GATEWAY_PORT", "8080"), "GATEWAY_PORT")
    if flag == "true" and len({port_a, port_b, gateway}) != 3:
        raise StartupError("Enabled Eureka UI ports and GATEWAY_PORT must be distinct")
    return EurekaUI(flag == "true", bind_ip, port_a, port_b)


def check_ui_bindings(config, values):
    settings = eureka_ui(values)
    for node, port in (("discovery-server", settings.port_a), ("discovery-peer", settings.port_b)):
        ports = config["services"][node].get("ports", [])
        if not settings.enabled:
            valid = not ports
        else:
            valid = (len(ports) == 1 and int(ports[0]["target"]) == 8761
                     and str(ports[0].get("published")) == str(port)
                     and ports[0].get("host_ip") == settings.bind_ip
                     and ports[0].get("protocol", "tcp") == "tcp")
        if not valid:
            raise StartupError(f"{node} host port bindings differ from EUREKA_UI_* settings; "
                               "regenerate the JAR override with --update-override, then run Compose up again")
    return settings


def check_running_ui_bindings(containers, settings):
    result = {}
    for node, port in (("discovery-server", settings.port_a), ("discovery-peer", settings.port_b)):
        selected = [item for item in containers if item.get("Config", {}).get("Labels", {}).get(
            "com.docker.compose.service") == node and item.get("State", {}).get("Running")]
        if len(selected) != 1:
            raise StartupError(f"Expected exactly one running {node} for host port verification")
        bindings = selected[0].get("NetworkSettings", {}).get("Ports", {}).get("8761/tcp") or []
        expected = [{"HostIp": settings.bind_ip, "HostPort": str(port)}] if settings.enabled else []
        if bindings != expected:
            raise StartupError(f"Running {node} port bindings differ from env/Compose; run Compose up again")
        result[node] = bindings
    return result
