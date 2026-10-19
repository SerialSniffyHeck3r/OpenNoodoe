#!/usr/bin/env python3
"""Offline stock SR1.5 codec. No Bluetooth access or network requests."""
import argparse
import hashlib
import json
import struct
import zlib

MAX_PAYLOAD = 11834


def command(command_id, attribute, payload=b''):
    return struct.pack('<2sBBHI', b'\xa5\x5a', command_id, attribute, 0, len(payload)) + payload


def sequence(payload=b'', control=0, packet=0, ack=0, session=0):
    if len(payload) > MAX_PAYLOAD or control & ~0x50 or session not in (0, 1):
        raise ValueError('invalid sequence fields')
    size = 9 + len(payload) + bool(payload)
    header = struct.pack('<2sHBBBBB', b'\x5a\xff', size, control, packet, ack, session, 0)
    return header + payload + (bytes([(-sum(payload)) & 255]) if payload else b'')


def decode_sequence(data):
    if len(data) < 9 or data[:2] != b'\x5a\xff':
        raise ValueError('invalid sequence header')
    _, size, control, packet, ack, session, reserved = struct.unpack('<2sHBBBBB', data[:9])
    if size != len(data) or size > 11844 or control & ~0x50 or session > 1:
        raise ValueError('invalid sequence fields')
    payload = data[9:-1] if size > 9 else b''
    if size > 9 and (sum(payload) + data[-1]) & 255:
        raise ValueError('checksum mismatch')
    return dict(control=control, packet=packet, ack=ack, session=session,
                reserved=reserved, payload=payload.hex())


def padded_crc32(data):
    return zlib.crc32(data + b'\0' * (-len(data) % 4)) & 0xffffffff


def negotiate(task, kind, location, size, operation, content_id):
    if len(content_id) != 16:
        raise ValueError('content ID must be 16 raw bytes')
    return struct.pack('<HHHIB', task, kind, location, size, operation) + content_id


def file_control(task, operation, transfer, identity, crc, size, filename=''):
    name = filename.encode('utf-8')
    if len(identity) != 16:
        raise ValueError('identity must be 16 raw bytes')
    return struct.pack('<HHH16sIIH', task, operation, transfer, identity, crc, size, len(name)) + name


def file_data(task, transfer, data):
    if len(data) > 11818:
        raise ValueError('data exceeds sequence payload capacity')
    return struct.pack('<HHH', task, transfer, 1) + data


def gallery_payload(jpeg, slot):
    if not 0 <= slot < 6 or not jpeg.startswith(b'\xff\xd8') or not jpeg.endswith(b'\xff\xd9'):
        raise ValueError('expected JPEG bytes and zero-based slot 0..5')
    # The Android generator handles crop/resize/quality. This function only adds the wire suffix.
    data = jpeg + bytes([slot])
    return data, hashlib.md5(data).hexdigest() + '.jpg'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decode', help='one complete outer frame as hex')
    args = parser.parse_args()
    if args.decode:
        print(json.dumps(decode_sequence(bytes.fromhex(args.decode)), indent=2))
    else:
        print(sequence(command(5, 1)).hex(' '))
