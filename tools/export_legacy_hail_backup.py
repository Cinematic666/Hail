#!/usr/bin/env python3
"""Create a Hail full-backup archive from an installed legacy app using root.

Termux:
  pkg install python
  python export_legacy_hail_backup.py --package com.aistra.hail.enhanced

For local tests, --source-dir may point to a copied Android app data directory.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import subprocess
import sys
import tarfile
import time
import xml.etree.ElementTree as ET
import zipfile

FORMAT = "com.aistra.hail.full-backup"
SCHEMA_VERSION = 1
MAX_FILE_BYTES = 8 * 1024 * 1024
MAX_TOTAL_BYTES = 40 * 1024 * 1024
EXCLUDED_KEYS = {"working_mode", "biometric_login", "auto_freeze_after_lock"}
SENSITIVE_FRAGMENTS = ("password", "token", "secret", "credential")
PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$")


def safe_relative(name: str) -> str:
    normalized = name.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    path = PurePosixPath(normalized)
    if not normalized or normalized.startswith("/") or path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ValueError(f"Unsafe app-data path: {name}")
    if len(normalized) > 512:
        raise ValueError(f"App-data path is too long: {name}")
    return normalized


def read_root_tar(package: str, user_id: int) -> dict[str, bytes]:
    app_data = f"/data/user/{user_id}/{package}"
    command = f"tar -C {shlex.quote(app_data)} -cf - files"
    result = subprocess.run(["su", "-c", command], capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(
            "Could not read the installed app data through root. "
            "Grant Termux root access and try again. "
            + result.stderr.decode("utf-8", "replace")[-800:]
        )
    return read_tar_bytes(result.stdout)


def read_tar_bytes(data: bytes) -> dict[str, bytes]:
    result: dict[str, bytes] = {}
    total = 0
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:") as archive:
        for member in archive:
            name = safe_relative(member.name)
            if not name.startswith("files/"):
                continue
            if member.isdir():
                continue
            if not member.isfile():
                raise ValueError(f"Unsupported non-regular app-data entry: {name}")
            if member.size < 0 or member.size > MAX_FILE_BYTES:
                raise ValueError(f"App-data file is too large: {name}")
            stream = archive.extractfile(member)
            if stream is None:
                raise ValueError(f"Could not read app-data file: {name}")
            content = stream.read(MAX_FILE_BYTES + 1)
            if len(content) != member.size or len(content) > MAX_FILE_BYTES:
                raise ValueError(f"Invalid app-data file size: {name}")
            total += len(content)
            if total > MAX_TOTAL_BYTES:
                raise ValueError("App data is too large for a portable backup")
            relative = name.removeprefix("files/")
            if relative in result:
                raise ValueError(f"Duplicate app-data file: {relative}")
            result[relative] = content
    return result


def read_local_source(source_dir: Path) -> tuple[dict[str, bytes], bytes]:
    files_root = source_dir / "files"
    if not files_root.is_dir():
        raise ValueError(f"Missing files directory in {source_dir}")
    files: dict[str, bytes] = {}
    total = 0
    for current, directories, names in os.walk(files_root, followlinks=False):
        directories[:] = [name for name in directories if not (Path(current) / name).is_symlink()]
        for name in names:
            path = Path(current) / name
            if path.is_symlink() or not path.is_file():
                continue
            relative = safe_relative(path.relative_to(files_root).as_posix())
            content = path.read_bytes()
            if len(content) > MAX_FILE_BYTES:
                raise ValueError(f"App-data file is too large: {relative}")
            total += len(content)
            if total > MAX_TOTAL_BYTES:
                raise ValueError("App data is too large for a portable backup")
            files[relative] = content
    prefs_dir = source_dir / "shared_prefs"
    candidates = list(prefs_dir.glob("*_preferences.xml")) if prefs_dir.exists() else []
    prefs_xml = candidates[0].read_bytes() if candidates else b"<map />"
    return files, prefs_xml


def read_root_preferences(package: str, user_id: int) -> bytes:
    path = f"/data/user/{user_id}/{package}/shared_prefs/{package}_preferences.xml"
    result = subprocess.run(
        ["su", "-c", f"cat {shlex.quote(path)}"],
        capture_output=True
    )
    if result.returncode == 0:
        return result.stdout
    # A missing default-preferences file is normal for a fresh installation.
    check = subprocess.run(["su", "-c", f"test -d {shlex.quote('/data/user/' + str(user_id) + '/' + package)}"],
                           capture_output=True)
    if check.returncode != 0:
        raise RuntimeError("The selected app data directory does not exist or root access was denied.")
    return b"<map />"


def parse_preferences(xml_bytes: bytes) -> list[dict]:
    try:
        root = ET.fromstring(xml_bytes or b"<map />")
    except ET.ParseError as error:
        raise ValueError(f"Could not parse Android SharedPreferences XML: {error}") from error
    if root.tag != "map":
        raise ValueError("Unexpected SharedPreferences XML root")
    entries = []
    seen = set()
    for node in root:
        key = node.attrib.get("name", "")
        normalized = key.lower()
        if (not key or len(key) > 256 or key in EXCLUDED_KEYS
                or any(fragment in normalized for fragment in SENSITIVE_FRAGMENTS)):
            continue
        kind = node.tag
        value = None
        if kind == "string":
            kind, value = "string", node.text or ""
        elif kind == "boolean":
            raw = node.attrib.get("value", "").lower()
            if raw not in ("true", "false"):
                continue
            kind, value = "boolean", raw == "true"
        elif kind in ("int", "long"):
            try:
                value = int(node.attrib["value"])
            except (KeyError, ValueError):
                continue
        elif kind == "float":
            try:
                value = float(node.attrib["value"])
            except (KeyError, ValueError):
                continue
            if not math.isfinite(value):
                continue
        elif kind == "set":
            kind = "set"
            value = [child.text or "" for child in node if child.tag == "string"]
            if len(value) > 256 or any(len(item) > 8192 for item in value):
                continue
        else:
            continue
        if kind == "string" and len(value) > 8192:
            continue
        if key in seen:
            raise ValueError(f"Duplicate preference key: {key}")
        seen.add(key)
        entries.append({"key": key, "type": kind, "value": value})
    entries.sort(key=lambda item: item["key"])
    if len(entries) > 512:
        raise ValueError("Too many app preferences")
    return entries


def create_archive(
    files: dict[str, bytes],
    preferences: list[dict],
    package: str,
    output: Path
) -> tuple[int, str]:
    files = dict(files)
    # A never-used Hail install may not have written these lazy-loaded defaults yet.
    files.setdefault("v1/tags.json", b'[{"tag":"Default","id":0}]')
    files.setdefault("v1/apps.json", b"[]")
    for state_path in ("v1/tags.json", "v1/apps.json"):
        if len(files[state_path]) > 5 * 1024 * 1024:
            raise ValueError(f"Tracked-app state file exceeds the Hail import limit: {state_path}")
    if len(files) > 254:
        raise ValueError("Unexpected number of app-data files")
    manifest = {
        "format": FORMAT,
        "schemaVersion": SCHEMA_VERSION,
        "applicationId": package,
        "versionName": "legacy-root-migration",
        "versionCode": 0,
        "createdAtEpochMs": int(time.time() * 1000),
        "fileCount": len(files),
        "preferenceCount": len(preferences),
        "migrationSource": "root-private-data-export",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, separators=(",", ":")))
        archive.writestr(
            "preferences.json",
            json.dumps(
                {"schemaVersion": SCHEMA_VERSION, "entries": preferences},
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        )
        for relative, content in sorted(files.items()):
            safe_relative(relative)
            if len(content) > MAX_FILE_BYTES:
                raise ValueError(f"App-data file is too large: {relative}")
            archive.writestr(f"files/{relative}", content)
    with zipfile.ZipFile(output, "r") as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"Archive verification failed at {bad}")
        names = set(archive.namelist())
        if not {"manifest.json", "preferences.json", "files/v1/tags.json", "files/v1/apps.json"} <= names:
            raise ValueError("Archive verification failed: required entries are missing")
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    return len(files), digest


def main() -> int:
    parser = argparse.ArgumentParser(description="Export Hail private app data into a portable full backup.")
    parser.add_argument("--package", default="com.aistra.hail.enhanced",
                        help="Installed Hail package ID (default: com.aistra.hail.enhanced)")
    parser.add_argument("--user-id", type=int, default=0, help="Android user ID (default: 0)")
    parser.add_argument("--source-dir", type=Path,
                        help="Local copy of app data for testing; bypasses root/Termux access")
    parser.add_argument("--output", type=Path, default=Path("Hail-Enhanced-Migration-Backup.zip"))
    args = parser.parse_args()

    if not PACKAGE_RE.fullmatch(args.package):
        parser.error("Invalid Android package ID")
    try:
        if args.source_dir:
            files, prefs_xml = read_local_source(args.source_dir)
        else:
            files = read_root_tar(args.package, args.user_id)
            prefs_xml = read_root_preferences(args.package, args.user_id)
        preferences = parse_preferences(prefs_xml)
        count, digest = create_archive(files, preferences, args.package, args.output)
        print("Backup created and ZIP integrity verified.")
        print(f"File entries: {count}")
        print(f"Restorable preferences: {len(preferences)}")
        print(f"Output: {args.output.resolve()}")
        print(f"SHA-256: {digest}")
        print("Keep this archive outside the app before uninstalling the old build.")
        print("Working mode, biometric login and auto-freeze activation are intentionally not migrated.")
        return 0
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
