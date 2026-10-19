# Runnable examples

Python 3.9+ standard library only; Java cross-check requires JDK 17+ (`JAVA_HOME` or PATH). Run from the repository root:

```sh
python examples/protocol.py
python examples/protocol.py --decode "5a ff 14 00 00 00 00 00 00 a5 5a 05 01 00 00 00 00 00 00 fb"
python -m unittest discover -s examples -v
python examples/check_java.py
```

`protocol.py` provides inner commands, outer sequences, ACKs, padded CRC32, negotiate/control/data payloads and the gallery suffix. It has no radio access: use the Android service for the working socket/reader/retry/transaction implementation. The protocol JSON/TSV vectors include time, riding, gallery stages and device info. All vectors and the annotated exchange are **synthetic**, not HCI captures. The `abc` file fixture is not an actual photo.

`ProtocolCheck.java` compiles against the actual production Java classes and compares all 12 vectors, CRCs, negotiation, ACK-only framing and navigation length. This catches differences between the reference Python and Android serializer. Python tests also exercise malformed frames, slot hashing, deterministic packages and damaged ZIP rejection.

## Package an authored theme

Prepare `configuration.json` containing the widget configuration object (the object with `widgets`, without the outer `files` wrapper). Put your own JPG/PNG images in a directory. Use their basenames in widget fields.

```sh
python examples/pack_theme.py pack configuration.json my-assets my-theme.zip
python examples/pack_theme.py check my-theme.zip
```

The output must not already exist. Import the resulting ZIP in OpenNoodoe. The tool generates content hashes and references; it does not create missing digit images, infer a design from a background, use the official compiler or download assets. See [widget fields and import limits](../docs/en/themes.md). The tiny generated PNG in unit tests is only a packaging fixture, not a usable dashboard theme.

[한국어](README.ko.md)
