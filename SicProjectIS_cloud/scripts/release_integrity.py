"""Hash executable jar contents independently of ZIP timestamps and entry ordering."""
import hashlib
import zipfile


def jar_payload_sha256(path):
    digest=hashlib.sha256()
    with zipfile.ZipFile(path) as archive:
        for name in sorted(entry.filename for entry in archive.infolist() if not entry.is_dir()):
            encoded=name.encode("utf-8")
            digest.update(len(encoded).to_bytes(8,"big"));digest.update(encoded)
            content=hashlib.sha256()
            with archive.open(name) as entry:
                for chunk in iter(lambda:entry.read(1024*1024),b""):content.update(chunk)
            digest.update(content.digest())
    return digest.hexdigest()
