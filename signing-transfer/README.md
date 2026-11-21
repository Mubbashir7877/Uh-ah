# Uh-Ah debug signing key transfer

The keystore matches the local Uh-Ah debug APK for `com.example.uh_ah`,
version 1.0, version code 1. The signing alias is `androiddebugkey`, and the
certificate SHA-256 fingerprint is:

```text
a7ff87aa60d755a6d3c18b70c4114ffdef50b1565145698abd017a7a9daff633
```

## Decrypt on Windows

Use Python 3.10 or later. From the checkout of `secure-signing-transfer`,
run the following in PowerShell. Paste the separately transferred Base64 key
at the hidden prompt. The output is a new file in your user profile.

```powershell
py -m pip install cryptography
py .\signing-transfer\keystore_transfer.py decrypt .\signing-transfer\uh-ah-original-debug.keystore.enc "$env:USERPROFILE\uh-ah-original-debug.keystore"
keytool -list -v -keystore "$env:USERPROFILE\uh-ah-original-debug.keystore" -alias androiddebugkey
```

Use the standard Android debug keystore credentials at the keytool prompt and
compare the SHA-256 fingerprint with the value above. Keep the decrypted
keystore and transfer key outside the checkout. When using a private key file
instead of the prompt, pass its path with `--key-file`.

## File format

The binary file starts with the eight ASCII bytes `UH-AH-KS` and a one-byte
version (`0x01`), followed by a 12-byte random nonce, the encrypted keystore,
and a 16-byte GCM authentication tag. AES-256-GCM authenticates the nine-byte
header as associated data. The key is 32 random bytes encoded in standard
Base64. The utility authenticates before writing any plaintext and requires
a new output path for either operation.
