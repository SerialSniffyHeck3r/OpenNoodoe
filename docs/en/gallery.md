# Gallery slots and replacement

User slots 1–6 correspond to payload slot bytes 0–5. `ContentGenerator.gallery` decodes the selected image, center-crops to 480×480 in RGB565, JPEG-encodes at quality 80, then appends the zero-based slot as **one byte after the JPEG**. The transmitted file is compressed JPEG plus that byte, not a raw RGB565 framebuffer.

Compute MD5 over the JPEG **including the appended slot byte**. The filename is `<md5>.jpg`. The CRC32 rule also covers the appended byte, followed by calculation-only zero padding. The same photo in different slots therefore has different content identities.

`installGallery` builds the gallery manifest from locally confirmed slot payloads, replacing only the selected slot with the new payload. The gallery content ID is MD5 of the concatenated filenames in slot order. It sends a FILE transaction at location 0600 using the ordinary MD5 filename identity. Transfer IDs count files in that transaction; they are not the JPEG slot marker.

Local preview images are editable drafts. `GallerySlotStore` separately preserves confirmed transmitted bytes in `gallery-committed`. A changed preview must not replace that committed payload before the upload completes. This is why failed replacement no longer silently changes the other slots' manifest. After successful task DONE, the selected payload and sent state are committed locally. Older saved slots are migrated into that store as needed.

The client cannot reconstruct pre-existing photos that were installed from another phone and are absent from its local store. The local manifest records what this app knows; a full dashboard gallery download protocol is not implemented.

Example: replacing visible slot 6 appends `05`, hashes the new bytes, rebuilds the ordered gallery identity and sends BEGIN/control/data/terminate/DONE. A field trace used data chunks 11816 + 11816 + 883 bytes; the last file byte was the slot marker. An observed DONE took about 6.8 seconds, motivating the 20-second task timeout.

Source: [`ContentGenerator.gallery`](../../android/app/src/main/java/io/opennoodoe/app/ContentGenerator.java), [`GallerySlotStore`](../../android/app/src/main/java/io/opennoodoe/app/GallerySlotStore.java), `installGallery` in [`NoodoeService`](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java).


[한국어](../ko/gallery.md) · [Index](../../README.md)
