"""Generate credentials once, and create or update a JAR Compose override without replacing credentials."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import secrets
import shutil
import tempfile
from datetime import datetime
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from deployment_settings import eureka_ui
from replica_common import resolve_replicas
from startup_common import StartupError, read_env

ROOT = Path(__file__).resolve().parents[1]
APPLICATIONS = {"config-server": "platform", "discovery-server": "platform",
    "gateway-service": "services", "iam-service": "services", "project-service": "services",
    "workflow-service": "services", "material-service": "services", "notification-service": "services"}
METADATA_PREFIX = "# sic-jar-deployment: "


def verify_jars(jars):
    if set(jars) != set(APPLICATIONS):
        raise StartupError("JAR override must contain all eight application artifacts")
    for name, jar in jars.items():
        if not jar.is_file():
            raise FileNotFoundError(f"Missing application JAR: {name}")
        manifest_path = jar.parent.parent / "release-manifest.json"
        if manifest_path.is_file():
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            entry = manifest.get("applications", {}).get(name)
            if (not entry or (manifest_path.parent / entry["file"]).resolve() != jar.resolve()
                    or hashlib.sha256(jar.read_bytes()).hexdigest() != entry["sha256"]):
                raise StartupError(f"Frozen release JAR path or hash differs: {name}")


def existing_jars(path, env_file):
    text = path.read_text(encoding="utf-8-sig")
    metadata_line = next((line for line in text.splitlines() if line.startswith(METADATA_PREFIX)), None)
    if metadata_line:
        metadata = json.loads(metadata_line[len(METADATA_PREFIX):])
        if Path(metadata["envFile"]).resolve() != env_file.resolve():
            raise StartupError("Existing override belongs to a different env file")
        return {name: Path(value).resolve() for name, value in metadata["jars"].items()}
    # Earlier generator output stored mounts as JSON arrays. Updating must never
    # silently switch an existing frozen deployment to mutable target JARs.
    jars, service = {}, None
    for line in text.splitlines():
        if line.startswith("  ") and not line.startswith("   ") and line.endswith(":"):
            service = line.strip().removesuffix(":")
        if service in APPLICATIONS and line.startswith("    volumes: "):
            for mount in json.loads(line.partition("volumes: ")[2]):
                if mount.get("target") == "/application/application.jar" and mount.get("read_only") is True:
                    jars[service] = Path(mount["source"]).resolve()
    if set(jars) != set(APPLICATIONS):
        raise StartupError("Cannot recover all JAR mounts from this override; specify --release-dir explicitly")
    return jars


def load_jars(args):
    if args.release_dir:
        release = args.release_dir.resolve()
        manifest = json.loads((release / "release-manifest.json").read_text(encoding="utf-8"))
        if set(manifest["applications"]) != set(APPLICATIONS):
            raise StartupError("Release must contain all eight applications")
        jars = {}
        for name in APPLICATIONS:
            entry = manifest["applications"][name]
            jar = (release / entry["file"]).resolve()
            if not jar.is_relative_to(release) or hashlib.sha256(jar.read_bytes()).hexdigest() != entry["sha256"]:
                raise StartupError("Release JAR path or hash differs: " + name)
            jars[name] = jar
    elif args.update_override:
        jars = existing_jars(args.compose_override, args.env_file)
    else:
        jars = {name: (ROOT / directory / name / "target" / (name + "-1.0.0-SNAPSHOT-exec.jar")).resolve()
                for name, directory in APPLICATIONS.items()}
    verify_jars(jars)
    return jars


def new_credentials(gateway_port):
    values = {}
    for line in (ROOT / ".env.example").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            name, value = line.split("=", 1)
            values[name] = secrets.token_urlsafe(32) if value.startswith("CHANGE_ME") else value
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    values["GATEWAY_PORT"] = str(gateway_port)
    values["JWT_PRIVATE_KEY"] = base64.b64encode(key.private_bytes(serialization.Encoding.DER,
        serialization.PrivateFormat.PKCS8, serialization.NoEncryption())).decode()
    values["JWT_PUBLIC_KEY"] = base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
    return values


def render_override(jars, env_file, settings):
    metadata = {"version": 1, "envFile": str(env_file.resolve()),
                "jars": {name: str(jar.resolve()) for name, jar in jars.items()}}
    override = ["# Generated by prepare_verification_env.py; configure replicas and ports in the env file.",
                METADATA_PREFIX + json.dumps(metadata),
                "# EUREKA_UI_ENABLED=" + str(settings.enabled).lower() + "; regenerate after changing this boolean.",
                "services:"]
    for name in [*APPLICATIONS, "discovery-peer"]:
        jar = jars["discovery-server" if name == "discovery-peer" else name]
        override += [f"  {name}:", "    build: !reset null", "    image: eclipse-temurin:21-jre-alpine",
            "    entrypoint: " + json.dumps(["java", "-Xms64m", "-Xmx384m", "-jar", "/application/application.jar"]),
            "    volumes: " + json.dumps([{"type": "bind", "source": str(jar.resolve()),
                "target": "/application/application.jar", "read_only": True}])]
        if name in ("discovery-server", "discovery-peer") and settings.enabled:
            variable, default = ("EUREKA_UI_PORT_A", 8761) if name == "discovery-server" else ("EUREKA_UI_PORT_B", 8762)
            override += ["    ports: !override", "      - target: 8761",
                         f'        published: "${{{variable}:-{default}}}"',
                         '        host_ip: "${EUREKA_UI_BIND_IP:-127.0.0.1}"', "        protocol: tcp"]
        elif name in ("config-server", "discovery-server", "discovery-peer"):
            override += ["    ports: !reset []"]
        if name == "gateway-service":
            override += ['    ports: !override ["127.0.0.1:${GATEWAY_PORT:-8080}:8080"]']
    override += ["  rabbitmq:", "    ports: !reset []", "  minio:", "    ports: !reset []"]
    return "\n".join(override) + "\n"


def update_override(path, text):
    backup = path.with_name(path.name + ".backup-" + datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + secrets.token_hex(3))
    shutil.copyfile(path, backup)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, delete=False, suffix=".yaml") as stream:
            temporary = Path(stream.name)
            stream.write(text)
        os.replace(temporary, path)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()
    return backup


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / "target/verification.env")
    parser.add_argument("--compose-override", type=Path, default=ROOT / "target/verification.compose.yaml")
    parser.add_argument("--gateway-port", type=int, default=8080, help="Initial GATEWAY_PORT for new credentials only")
    parser.add_argument("--release-dir", type=Path, help="Frozen, hash-verified release to mount")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--override-only", action="store_true", help="Create a new override, reading existing env settings if present")
    mode.add_argument("--update-override", action="store_true", help="Back up and update an existing override; never write the env file")
    args = parser.parse_args()
    if args.env_file.resolve() == args.compose_override.resolve():
        raise StartupError("Env file and Compose override must be different files")
    if args.update_override:
        if not args.env_file.is_file() or not args.compose_override.is_file():
            raise FileNotFoundError("--update-override requires an existing env file and override")
    else:
        for path in ([args.compose_override] if args.override_only else [args.env_file, args.compose_override]):
            if path.exists():
                raise FileExistsError("Refusing to replace existing verification configuration: " + str(path))
    if not 1024 <= args.gateway_port <= 65535:
        raise StartupError("Gateway port must be between 1024 and 65535")
    jars = load_jars(args)
    if args.override_only or args.update_override:
        values = read_env(args.env_file) if args.env_file.is_file() else {}
    else:
        values = new_credentials(args.gateway_port)
    resolve_replicas({}, values, {})
    settings = eureka_ui(values)
    text = render_override(jars, args.env_file, settings)
    args.compose_override.parent.mkdir(parents=True, exist_ok=True)
    if not args.override_only and not args.update_override:
        args.env_file.parent.mkdir(parents=True, exist_ok=True)
        with args.env_file.open("x", encoding="utf-8") as stream:
            stream.write("\n".join(name + "=" + value for name, value in values.items()) + "\n")
        args.env_file.chmod(0o600)
    if args.update_override:
        backup = update_override(args.compose_override, text)
        print(f"Updated JAR override; backup={backup}; existing credentials unchanged.")
    else:
        with args.compose_override.open("x", encoding="utf-8") as stream:
            stream.write(text)
        print("Created HA JAR override" + ("; existing credentials unchanged." if args.override_only else
              " and isolated credentials; secret values were not printed."))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
