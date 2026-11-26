#!/usr/bin/env python3
"""Create an Android Enterprise QR provisioning payload for this DPC.

Requires: pip install -r qr-tools/requirements.txt
Requires Android build-tools' apksigner on PATH when --apk is provided.
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import subprocess
import sys
from pathlib import Path


def signer_checksum(apk_path: Path) -> str:
    result = subprocess.run(
        ["apksigner", "verify", "--print-certs", str(apk_path)],
        check=True,
        capture_output=True,
        text=True,
    )
    match = re.search(
        r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)",
        result.stdout + "\n" + result.stderr,
    )
    if not match:
        raise RuntimeError("Could not locate the APK signer SHA-256 certificate digest in apksigner output.")
    digest_hex = match.group(1).replace(":", "")
    digest = bytes.fromhex(digest_hex)
    return base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-url", required=True, help="HTTPS URL from which Setup Wizard can download the APK")
    parser.add_argument("--apk", type=Path, required=True, help="Signed APK whose signer checksum is embedded in the QR")
    parser.add_argument("--package", default="com.example.lockdowndpc", help="DPC application ID")
    parser.add_argument("--receiver", default=".AdminReceiver", help="DeviceAdminReceiver class (default: .AdminReceiver)")
    parser.add_argument("--enrollment-id", default="", help="Optional non-secret enrollment identifier")
    parser.add_argument("--out", type=Path, default=Path("lockdown-dpc-qr.png"), help="PNG output path")
    parser.add_argument("--json-out", type=Path, default=None, help="Optional provisioning JSON output path")
    args = parser.parse_args()

    if not args.apk_url.lower().startswith("https://"):
        parser.error("--apk-url must be HTTPS")
    if not args.apk.is_file():
        parser.error(f"APK not found: {args.apk}")

    checksum = signer_checksum(args.apk)
    payload = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
            f"{args.package}/{args.package}{args.receiver if args.receiver.startswith('.') else '.' + args.receiver}",
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.apk_url,
    }
    if args.enrollment_id:
        payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = {
            "enrollmentId": args.enrollment_id
        }

    qr_json = json.dumps(payload, separators=(",", ":"))
    if args.json_out:
        args.json_out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    try:
        import qrcode
    except ImportError:
        print("Missing dependency. Run: pip install -r qr-tools/requirements.txt", file=sys.stderr)
        return 2

    image = qrcode.make(qr_json, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=4)
    image.save(args.out)
    print(f"Wrote QR: {args.out}")
    if args.json_out:
        print(f"Wrote provisioning JSON: {args.json_out}")
    print("QR payload:")
    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
