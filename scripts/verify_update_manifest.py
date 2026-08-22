#!/usr/bin/env python3
"""Strict verifier/CI helper for KinogoATV signed update manifests."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from typing import Any
from urllib.parse import urlsplit


MAX_ENVELOPE_CHARS = 256 * 1024
MAX_PAYLOAD_BYTES = 64 * 1024
MAX_SIGNATURE_BYTES = 2 * 1024
MAX_APK_SIZE_BYTES = 200 * 1024 * 1024
MAX_CLOCK_SKEW_SECONDS = 24 * 60 * 60
MAX_MANIFEST_LIFETIME_SECONDS = 90 * 24 * 60 * 60
MAX_DOWNLOAD_URLS = 4
MAX_URL_CHARS = 8 * 1024
VERSION_NAME = re.compile(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?\Z")
UNSIGNED_INTEGER = re.compile(r"0|[1-9]\d*\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
IPV4_LITERAL = re.compile(r"(?:\d{1,3}\.){3}\d{1,3}\Z")
ENVELOPE_FIELDS = {"schema", "payload", "signature"}
PAYLOAD_FIELDS = {
    "versionName",
    "versionCode",
    "assetName",
    "assetSizeBytes",
    "sha256",
    "issuedAtEpochSeconds",
    "expiresAtEpochSeconds",
    "downloadUrls",
}


class ManifestError(ValueError):
    pass


class JsonInteger(int):
    def __new__(cls, raw: str) -> "JsonInteger":
        value = int.__new__(cls, raw)
        value.raw = raw
        return value


def _pairs_without_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ManifestError("JSON contains duplicate fields")
        result[key] = value
    return result


def _reject_constant(_: str) -> None:
    raise ManifestError("JSON contains a non-finite number")


def _load_json_object(text: str, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            text,
            object_pairs_hook=_pairs_without_duplicates,
            parse_int=JsonInteger,
            parse_float=lambda _: (_ for _ in ()).throw(
                ManifestError("JSON number must be an unsigned integer")
            ),
            parse_constant=_reject_constant,
        )
    except (json.JSONDecodeError, UnicodeError) as error:
        raise ManifestError(f"{label} is not valid JSON") from error
    if not isinstance(value, dict):
        raise ManifestError(f"{label} must be a JSON object")
    return value


def _required_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise ManifestError(f"{label} must be a non-empty string")
    return value


def _required_unsigned_integer(value: Any, label: str) -> int:
    if not isinstance(value, JsonInteger) or not UNSIGNED_INTEGER.fullmatch(value.raw):
        raise ManifestError(f"{label} must be an unsigned integer")
    return int(value)


def _required_base64(value: Any, maximum: int, label: str) -> bytes:
    encoded = _required_string(value, label)
    if len(encoded) > maximum * 2:
        raise ManifestError(f"{label} is too large")
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as error:
        raise ManifestError(f"{label} is not valid base64") from error
    if len(decoded) > maximum or base64.b64encode(decoded).decode("ascii") != encoded:
        raise ManifestError(f"{label} is not canonical base64")
    return decoded


def _validate_download_url(raw_url: str, asset_name: str) -> None:
    if not 1 <= len(raw_url) <= MAX_URL_CHARS:
        raise ManifestError("Download URL length is invalid")
    if any(ord(character) < 32 or ord(character) == 127 or character == "\\" for character in raw_url):
        raise ManifestError("Download URL contains a forbidden character")
    try:
        parsed = urlsplit(raw_url)
        port = parsed.port
    except ValueError as error:
        raise ManifestError("Download URL is invalid") from error
    host = (parsed.hostname or "").lower()
    if (
        parsed.scheme.lower() != "https"
        or parsed.username is not None
        or parsed.password is not None
        or port is not None
        or parsed.fragment
        or not parsed.path.startswith("/")
        or not parsed.path.endswith("/" + asset_name)
        or "." not in host
        or host == "localhost"
        or IPV4_LITERAL.fullmatch(host)
        or ":" in host
    ):
        raise ManifestError("Download URL is not an allowed public HTTPS artifact address")


def load_manifest(path: Path, now_epoch_seconds: int | None = None) -> dict[str, Any]:
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise ManifestError("Manifest envelope is not UTF-8") from error
    if len(text) > MAX_ENVELOPE_CHARS:
        raise ManifestError("Manifest envelope is too large")
    envelope = _load_json_object(text, "Manifest envelope")
    if set(envelope) != ENVELOPE_FIELDS:
        raise ManifestError("Manifest envelope fields are invalid")
    if _required_unsigned_integer(envelope["schema"], "schema") != 1:
        raise ManifestError("Manifest schema is unsupported")
    payload_bytes = _required_base64(envelope["payload"], MAX_PAYLOAD_BYTES, "payload")
    signature = _required_base64(envelope["signature"], MAX_SIGNATURE_BYTES, "signature")
    if not payload_bytes:
        raise ManifestError("Manifest payload is empty")
    if not signature:
        raise ManifestError("Manifest signature is empty")
    try:
        payload_text = payload_bytes.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise ManifestError("Manifest payload is not UTF-8") from error
    payload = _load_json_object(payload_text, "Manifest payload")
    if set(payload) != PAYLOAD_FIELDS:
        raise ManifestError("Manifest payload fields are invalid")

    version_name = _required_string(payload["versionName"], "versionName")
    if not VERSION_NAME.fullmatch(version_name):
        raise ManifestError("Version name is invalid")
    version_code = _required_unsigned_integer(payload["versionCode"], "versionCode")
    if version_code <= 0:
        raise ManifestError("Version code must be positive")
    asset_name = _required_string(payload["assetName"], "assetName")
    expected_asset_name = f"KinogoATV-{version_name}-code{version_code}.apk"
    if asset_name != expected_asset_name:
        raise ManifestError("Asset name does not match the version")
    asset_size = _required_unsigned_integer(payload["assetSizeBytes"], "assetSizeBytes")
    if not 1 <= asset_size <= MAX_APK_SIZE_BYTES:
        raise ManifestError("Asset size is outside the updater limit")
    sha256 = _required_string(payload["sha256"], "sha256")
    if not SHA256.fullmatch(sha256):
        raise ManifestError("SHA-256 must be lowercase hexadecimal")
    issued_at = _required_unsigned_integer(payload["issuedAtEpochSeconds"], "issuedAtEpochSeconds")
    expires_at = _required_unsigned_integer(payload["expiresAtEpochSeconds"], "expiresAtEpochSeconds")
    now = int(time.time()) if now_epoch_seconds is None else now_epoch_seconds
    if issued_at > now + MAX_CLOCK_SKEW_SECONDS:
        raise ManifestError("Manifest is not valid yet")
    if expires_at <= now:
        raise ManifestError("Manifest has expired")
    if expires_at <= issued_at or expires_at - issued_at > MAX_MANIFEST_LIFETIME_SECONDS:
        raise ManifestError("Manifest lifetime is invalid")
    urls = payload["downloadUrls"]
    if not isinstance(urls, list) or not 1 <= len(urls) <= MAX_DOWNLOAD_URLS:
        raise ManifestError("Download URL list is invalid")
    if any(not isinstance(url, str) for url in urls):
        raise ManifestError("Download URL must be a string")
    if len(set(urls)) != len(urls):
        raise ManifestError("Download URLs must be unique")
    for url in urls:
        _validate_download_url(url, asset_name)

    return {
        "payload_bytes": payload_bytes,
        "signature": signature,
        "version_name": version_name,
        "version_code": version_code,
        "asset_name": asset_name,
        "asset_size_bytes": asset_size,
        "sha256": sha256,
        "issued_at_epoch_seconds": issued_at,
        "expires_at_epoch_seconds": expires_at,
        "download_urls": urls,
    }


def _write_github_output(path: Path, metadata: dict[str, Any]) -> None:
    values = {
        "release_tag": "v" + metadata["version_name"],
        "version_name": metadata["version_name"],
        "version_code": str(metadata["version_code"]),
        "asset_name": metadata["asset_name"],
        "asset_size_bytes": str(metadata["asset_size_bytes"]),
        "sha256": metadata["sha256"],
    }
    with path.open("a", encoding="utf-8", newline="\n") as output:
        for key, value in values.items():
            if "\n" in value or "\r" in value:
                raise ManifestError("GitHub output value is invalid")
            output.write(f"{key}={value}\n")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _verify_signature(
    metadata: dict[str, Any],
    certificate_path: Path,
    expected_certificate_sha256: str | None,
    openssl_command: str,
) -> None:
    certificate_text = certificate_path.read_text(encoding="ascii", errors="strict")
    if certificate_text.count("-----BEGIN CERTIFICATE-----") != 1 or certificate_text.count(
        "-----END CERTIFICATE-----"
    ) != 1:
        raise ManifestError("Exactly one APK signing certificate is required")
    with tempfile.TemporaryDirectory(prefix="kinogo-manifest-") as temporary:
        temporary_path = Path(temporary)
        public_key = temporary_path / "public-key.pem"
        certificate_der = temporary_path / "certificate.der"
        payload = temporary_path / "payload.bin"
        signature = temporary_path / "signature.bin"
        payload.write_bytes(metadata["payload_bytes"])
        signature.write_bytes(metadata["signature"])
        commands = (
            [openssl_command, "x509", "-in", str(certificate_path), "-pubkey", "-noout", "-out", str(public_key)],
            [openssl_command, "x509", "-in", str(certificate_path), "-outform", "DER", "-out", str(certificate_der)],
            [
                openssl_command,
                "dgst",
                "-sha256",
                "-verify",
                str(public_key),
                "-signature",
                str(signature),
                str(payload),
            ],
        )
        for command in commands:
            result = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode != 0:
                raise ManifestError("Manifest signature does not match the APK signer")
        actual_certificate_sha256 = _sha256(certificate_der)
        if expected_certificate_sha256 is not None:
            expected = expected_certificate_sha256.lower()
            if not SHA256.fullmatch(expected) or actual_certificate_sha256 != expected:
                raise ManifestError("APK signing certificate is not the expected update identity")


def inspect_command(args: argparse.Namespace) -> None:
    metadata = load_manifest(args.manifest)
    if args.github_output is not None:
        _write_github_output(args.github_output, metadata)
    print(
        f"Manifest metadata valid: {metadata['asset_name']} "
        f"({metadata['asset_size_bytes']} bytes, {len(metadata['download_urls'])} URL(s))"
    )


def verify_command(args: argparse.Namespace) -> None:
    metadata = load_manifest(args.manifest)
    if args.apk.name != metadata["asset_name"]:
        raise ManifestError("Downloaded APK filename does not match signed metadata")
    if args.apk.stat().st_size != metadata["asset_size_bytes"]:
        raise ManifestError("Downloaded APK size does not match signed metadata")
    if _sha256(args.apk) != metadata["sha256"]:
        raise ManifestError("Downloaded APK SHA-256 does not match signed metadata")
    for required_url in args.required_download_url:
        if required_url not in metadata["download_urls"]:
            raise ManifestError("Manifest does not contain the required deployment URL")
    _verify_signature(
        metadata,
        args.certificate,
        args.expected_certificate_sha256,
        args.openssl,
    )
    print(f"Manifest, APK digest, and APK signing identity verified: {metadata['asset_name']}")


def self_test_command(_: argparse.Namespace) -> None:
    now = int(time.time())
    payload = {
        "versionName": "9.8.7",
        "versionCode": 987,
        "assetName": "KinogoATV-9.8.7-code987.apk",
        "assetSizeBytes": 123,
        "sha256": "a" * 64,
        "issuedAtEpochSeconds": now,
        "expiresAtEpochSeconds": now + 3600,
        "downloadUrls": ["https://updates.example.org/KinogoATV-9.8.7-code987.apk"],
    }
    payload_bytes = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    envelope = {
        "schema": 1,
        "payload": base64.b64encode(payload_bytes).decode("ascii"),
        "signature": base64.b64encode(b"synthetic-self-test-signature").decode("ascii"),
    }
    with tempfile.TemporaryDirectory(prefix="kinogo-manifest-self-test-") as temporary:
        path = Path(temporary) / "manifest.json"
        path.write_text(json.dumps(envelope, separators=(",", ":")), encoding="utf-8")
        metadata = load_manifest(path, now_epoch_seconds=now)
        if metadata["asset_name"] != payload["assetName"]:
            raise ManifestError("Manifest helper self-test failed")
        duplicate = '{"schema":1,"schema":1,"payload":"AA==","signature":"AA=="}'
        path.write_text(duplicate, encoding="utf-8")
        try:
            load_manifest(path, now_epoch_seconds=now)
        except ManifestError:
            pass
        else:
            raise ManifestError("Manifest helper accepted duplicate JSON fields")
    print("verify_update_manifest.py self-test passed")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    inspect_parser = subparsers.add_parser("inspect", help="validate and decode signed metadata")
    inspect_parser.add_argument("--manifest", type=Path, required=True)
    inspect_parser.add_argument("--github-output", type=Path)
    inspect_parser.set_defaults(handler=inspect_command)

    verify_parser = subparsers.add_parser("verify", help="verify manifest against a downloaded APK")
    verify_parser.add_argument("--manifest", type=Path, required=True)
    verify_parser.add_argument("--apk", type=Path, required=True)
    verify_parser.add_argument("--certificate", type=Path, required=True)
    verify_parser.add_argument("--expected-certificate-sha256")
    verify_parser.add_argument("--required-download-url", action="append", default=[])
    verify_parser.add_argument("--openssl", default=os.environ.get("OPENSSL", "openssl"))
    verify_parser.set_defaults(handler=verify_command)

    self_test_parser = subparsers.add_parser("self-test", help="run parser safety checks")
    self_test_parser.set_defaults(handler=self_test_command)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        args.handler(args)
        return 0
    except (ManifestError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
