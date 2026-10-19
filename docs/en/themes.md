# Compiled themes and authoring

A dashboard theme is a **JSON widget layout plus raster assets**. It is not necessarily one flattened bitmap. The transfer package contains one `<MD5>.cfg` and its declared `<MD5>.jpg` / `<MD5>.png` assets. CFG is UTF-8 JSON with `files` and `configuration.widgets`. `files` lists image filenames, not the CFG itself. The creation's content ID is the raw 16-byte MD5 of the exact CFG bytes.

Changing whitespace in CFG changes its identity. Renaming an image without rewriting the references breaks the package. Replacing image bytes requires new image MD5, filename, widget references, `files`, serialized CFG and CFG MD5. MD5 is the device's content identity, not a security signature.

## Import rules

`importCreationZip` reads .cfg/.jpg/.png entries, flattens directory components, rejects duplicate basenames, verifies each 32-hex filename against file bytes, requires exactly one CFG and resolves every `files[]` entry. Undeclared payloads and duplicate declarations fail. Limit: 256 accepted files and 64 MiB expanded accepted data. The importer does not install source Creation/appData ZIPs. There is no official-asset compiler bundled in the APK.

The type detector recognizes ClockDigitWidget/WatchHandWidget as clock (0200); SpeedDigitWidget/SpeedNeedleWidget/SpeedBarWidget/OdometerWidget/BatteryWidget as speedometer (0400). Mixed types are rejected. A BackgroundWidget alone does not identify a clock theme. Weather/POI recognizers remain in research code but product policy rejects those locations.

## Make your own package

Author the widget configuration and your own images, then use [pack_theme.py](../../examples/pack_theme.py). It hashes local images, rewrites exact filename references recursively, creates `files`, hashes the CFG and writes a deterministic ZIP. It does not fetch themes or turn an arbitrary illustration into a complete clock renderer. Supply the widget image arrays and layout your design needs.

The schema below records field names and array shapes recovered from compiled clock/speedometer bundles; it includes no extracted artwork. Full-screen backgrounds are 480×480. Glyph image sizes depend on the widget; do not resize every asset to 480×480.

Imported templates can be customized in the app: JPEG background at quality 92, glyph recoloring, period/weekday visibility, speed bar color/type. After changes it rebuilds hashes and CFG references. Templates and created themes stay in private app storage. The app ships with none and has no Internet permission.

Transfer order follows the original Java HashMap construction (`creation` key for CFG, stem for images), not "CFG is always file 1". `officialCreationOrder` is the source of that compatibility behavior; log `TRANSFER_FILE_ORDER` when comparing clients.

| Widget | Fields / shape |
|---|---|
| BackgroundWidget | `imageType: "single"`, `images: [filename]` |
| ClockDigitWidget | `format`, `digitImages` (10), `punctuationImages`, `monthImages`, `periodImages`, `weekdayImages` |
| DateWidget | `format`, `centerX`, `centerY`, same glyph arrays; period 2, weekday 7 |
| SpeedDigitWidget | `images` (10 digit images) |
| SpeedBarWidget | `speedBarColor`, `speedBarType` |
| OdometerWidget | `images` (10), `positions` (6 objects with integer x/y) |

```json
{
  "files": ["<image-md5>.jpg", "<digit-md5>.png"],
  "configuration": {
    "widgets": [
      {"name": "BackgroundWidget", "imageType": "single", "images": ["<image-md5>.jpg"]}
    ]
  }
}
```

This fragment illustrates the container, not a complete clock. / 이 조각은 컨테이너 구조 예시이며 완성된 시계가 아닙니다.

Source: [`ContentGenerator`](../../android/app/src/main/java/io/opennoodoe/app/ContentGenerator.java), [`ThemeRepository`](../../android/app/src/main/java/io/opennoodoe/app/ThemeRepository.java), [`ThemeAuthoringOptions`](../../android/app/src/main/java/io/opennoodoe/app/ThemeAuthoringOptions.java).


[한국어](../ko/themes.md) · [Index](../../README.md)
