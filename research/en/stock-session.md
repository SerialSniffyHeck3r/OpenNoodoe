# 2026-08-31 stock session

Context: AK550 SAA1AA(KR), PCBA SR0701, running firmware 5.16, resource 5.14, boot 0.15. Device-specific MAC, serial, PIN and ODO are omitted here.

The app received four complete 82-byte DEVICE_INFO replies, two successful 11-byte RIDING_STATUS replies and two successful 139-byte OQC reads. Three MOBILE_STATUS writes returned success. Ten app-notification writes returned success, with visible display reported by the owner. Fifteen light commands yielded eight successes and seven invalid-state responses.

The light behavior correlated with ignition: key ON returned status 5/state 0; after C3=0, ON/OFF commands returned status 0 and the requested state, with visible operation. No connected command in that session needed a sequence retry or failed frame parsing. This establishes a stock-firmware phone protocol, independent of CFW.

The production block's firmware field differed from the running DEVICE_INFO version. Read live DEVICE_INFO when reporting firmware compatibility. Riding stop-duration units were not established; retain the raw integer.

[Research index](README.md) · [한국어](../ko/stock-session.md)
