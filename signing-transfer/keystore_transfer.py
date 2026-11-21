"""Encrypt or decrypt a keystore using the format documented in README.md."""

from __future__ import annotations

import argparse
import base64
import binascii
import getpass
import os
import secrets
import warnings
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


MAGIC = b"UH-AH-KS\x01"
NONCE_SIZE = 12
TAG_SIZE = 16


def read_key(key_file: Path | None) -> bytes:
    if key_file is None:
        with warnings.catch_warnings():
            warnings.simplefilter("error", getpass.GetPassWarning)
            encoded = getpass.getpass("Transfer key (input is hidden): ").strip()
    else:
        encoded = key_file.read_text(encoding="ascii").strip()

    try:
        key = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("Expected a Base64-encoded 32-byte transfer key.") from exc
    if len(key) != 32:
        raise ValueError("Expected a Base64-encoded 32-byte transfer key.")
    return key


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("encrypt", "decrypt"))
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--key-file", type=Path, help="Read the Base64 key from a private file.")
    args = parser.parse_args()

    try:
        cipher = AESGCM(read_key(args.key_file))
        source = args.source.read_bytes()
        if args.operation == "encrypt":
            nonce = secrets.token_bytes(NONCE_SIZE)
            result = MAGIC + nonce + cipher.encrypt(nonce, source, MAGIC)
        else:
            payload_start = len(MAGIC) + NONCE_SIZE
            if not source.startswith(MAGIC) or len(source) < payload_start + TAG_SIZE:
                raise ValueError("Invalid or unsupported encrypted keystore format.")
            nonce = source[len(MAGIC):payload_start]
            result = cipher.decrypt(nonce, source[payload_start:], MAGIC)

        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0)
        fd = os.open(args.destination, flags, 0o600)
        with os.fdopen(fd, "wb") as output:
            output.write(result)
    except InvalidTag:
        parser.exit(1, "Authentication failed. Check the transfer key and encrypted file.\n")
    except getpass.GetPassWarning:
        parser.exit(1, "Hidden input is unavailable. Use a private --key-file.\n")
    except (OSError, ValueError) as exc:
        parser.exit(1, f"{exc}\n")


if __name__ == "__main__":
    main()
