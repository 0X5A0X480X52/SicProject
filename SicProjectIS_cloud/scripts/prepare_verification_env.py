"""Create isolated test credentials and a Compose override using already packaged application jars."""
from __future__ import annotations
import argparse
import base64
import json
import secrets
import hashlib
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[1]
APPLICATIONS = {"config-server": "platform", "discovery-server": "platform",
    "gateway-service": "services", "iam-service": "services", "project-service": "services",
    "workflow-service": "services", "material-service": "services", "notification-service": "services"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / "target/verification.env")
    parser.add_argument("--compose-override", type=Path, default=ROOT / "target/verification.compose.yaml")
    parser.add_argument("--gateway-port", type=int, default=18080)
    parser.add_argument("--release-dir", type=Path, help="Use a frozen, hash-verified release instead of target jars")
    args = parser.parse_args()
    jars={name:ROOT/directory/name/"target"/(name+"-1.0.0-SNAPSHOT-exec.jar") for name,directory in APPLICATIONS.items()}
    if args.release_dir:
        release=args.release_dir.resolve()
        manifest=json.loads((release/"release-manifest.json").read_text(encoding="utf-8"))
        if set(manifest["applications"])!=set(APPLICATIONS):raise ValueError("Release must contain all eight applications")
        for name in APPLICATIONS:
            entry=manifest["applications"][name];jar=(release/entry["file"]).resolve()
            if not jar.is_relative_to(release) or hashlib.sha256(jar.read_bytes()).hexdigest()!=entry["sha256"]:
                raise ValueError("Release jar path or hash differs: "+name)
            jars[name]=jar
    if any(not jar.is_file() for jar in jars.values()):raise FileNotFoundError("Package all eight applications first")
    if not 1024 <= args.gateway_port <= 65535:
        raise ValueError("Gateway port must be between 1024 and 65535")
    for path in (args.env_file, args.compose_override):
        if path.exists():
            raise FileExistsError("Refusing to replace existing verification configuration: " + str(path))
        path.parent.mkdir(parents=True, exist_ok=True)
    lines = []
    for line in (ROOT / ".env.example").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            name, value = line.split("=", 1)
            lines.append((name, secrets.token_urlsafe(32) if value.startswith("CHANGE_ME") else value))
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    values = dict(lines)
    values["JWT_PRIVATE_KEY"] = base64.b64encode(key.private_bytes(serialization.Encoding.DER,
        serialization.PrivateFormat.PKCS8, serialization.NoEncryption())).decode()
    values["JWT_PUBLIC_KEY"] = base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
    args.env_file.write_text("\n".join(name + "=" + value for name, value in values.items()) + "\n", encoding="utf-8")
    args.env_file.chmod(0o600)
    override = ["services:"]
    for name, directory in APPLICATIONS.items():
        jar = jars[name]
        override += [f"  {name}:", "    build: !reset null", "    image: eclipse-temurin:21-jre-alpine",
            "    entrypoint: " + json.dumps(["java", "-Xms64m", "-Xmx384m", "-jar", "/application/application.jar"]),
            "    volumes: " + json.dumps([{"type": "bind", "source": str(jar.resolve()),
                "target": "/application/application.jar", "read_only": True}])]
        if name in ("config-server", "discovery-server"):
            override += ["    ports: !reset []"]
        if name == "gateway-service":
            override += [f'    ports: !override ["127.0.0.1:{args.gateway_port}:8080"]']
    override += ["  rabbitmq:", "    ports: !reset []", "  minio:", "    ports: !reset []"]
    args.compose_override.write_text("\n".join(override) + "\n", encoding="utf-8")
    print("Created isolated credentials and Compose override; secret values were not printed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
