#!/usr/bin/env python3
"""Fail if an oss APK refers to Google Play services or another Google closed-source library.

F-Droid and IzzyOnDroid reject such dependencies. Usage: check-oss-apk.py <apk>.
Looks at every dex file and at the binary manifest (as UTF-8 and UTF-16, the two ways Android
stores its string pool), and at the native libraries of the Play-only recogniser.
"""
import sys
import zipfile

FORBIDDEN = [
    "com/google/android/gms/",
    "com/google/android/play/",
    "com/google/firebase/",
    "com/google/mlkit/",
    "com/android/billingclient/",
]
FORBIDDEN_LIBS = ("libsherpa-onnx", "libonnxruntime")


def main(path: str) -> int:
    found = []
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if name.startswith("lib/") and any(lib in name for lib in FORBIDDEN_LIBS):
                found.append(f"{name}: prebuilt native library of the play flavor")
            if name.endswith(".dex"):
                data = apk.read(name)
                needles = {p: p.encode() for p in FORBIDDEN}
            elif name == "AndroidManifest.xml":
                data = apk.read(name)
                needles = {p: p.replace("/", ".").encode() for p in FORBIDDEN}
                needles.update({p + "#16": p.replace("/", ".").encode("utf-16-le") for p in FORBIDDEN})
            else:
                continue
            for label, needle in needles.items():
                if needle in data:
                    found.append(f"{name}: {label.removesuffix('#16')}")
    if found:
        print(f"{path} is not free of Google services:")
        for line in sorted(set(found)):
            print(f"  {line}")
        return 1
    print(f"{path}: no Google Play services, Firebase or prebuilt play-flavor libraries")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: check-oss-apk.py <apk>")
    sys.exit(main(sys.argv[1]))
