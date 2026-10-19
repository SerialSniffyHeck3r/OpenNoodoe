#!/usr/bin/env python3
"""Package user-authored widget JSON and local JPG/PNG assets; never downloads content."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

CLOCK = {'ClockDigitWidget', 'WatchHandWidget'}
SPEED = {'SpeedDigitWidget', 'SpeedNeedleWidget', 'SpeedBarWidget', 'OdometerWidget', 'BatteryWidget'}


def location(configuration):
    names = {w.get('name') for w in configuration['widgets']}
    types = ({0x200} if names & CLOCK else set()) | ({0x400} if names & SPEED else set())
    if len(types) != 1:
        raise ValueError('configuration must identify exactly one clock or speedometer type')
    return types.pop()


def replace_refs(value, names):
    if isinstance(value, dict):
        return {k: replace_refs(v, names) for k, v in value.items()}
    if isinstance(value, list):
        return [replace_refs(v, names) for v in value]
    return names.get(value, value) if isinstance(value, str) else value


def pack(configuration, assets, target):
    location(configuration)
    assets = Path(assets).resolve()
    names, files = {}, {}
    for path in sorted(assets.iterdir()):
        if not path.is_file() or path.suffix.lower() not in ('.jpg', '.png'):
            continue
        if not path.resolve().is_relative_to(assets):
            raise ValueError('asset link escapes input directory')
        data = path.read_bytes()
        suffix = path.suffix.lower()
        if not data.startswith(b'\x89PNG\r\n\x1a\n' if suffix == '.png' else b'\xff\xd8'):
            raise ValueError('invalid image signature: ' + path.name)
        name = hashlib.md5(data).hexdigest() + suffix
        names[path.name] = name
        files[name] = data
    if not files:
        raise ValueError('no JPG/PNG assets')
    config = replace_refs(configuration, names)
    def check_refs(value):
        if isinstance(value, dict):
            for v in value.values(): check_refs(v)
        elif isinstance(value, list):
            for v in value: check_refs(v)
        elif isinstance(value, str) and value.lower().endswith(('.jpg', '.png')) and value not in files:
            raise ValueError('unresolved image reference: ' + value)
    check_refs(config)
    root = {'files': sorted(files), 'configuration': config}
    data = json.dumps(root, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    identity = hashlib.md5(data).hexdigest()
    files[identity + '.cfg'] = data
    if len(files) > 256 or sum(map(len, files.values())) > 64 * 1024 * 1024:
        raise ValueError('bundle exceeds Android import limits')
    target = Path(target)
    # Exclusive creation prevents accidentally replacing a previously authored bundle.
    with zipfile.ZipFile(target, 'x', compression=zipfile.ZIP_DEFLATED) as out:
        for name in sorted(files):
            item = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(item, files[name])
    validate(target)
    return identity


def validate(path):
    files = {}
    total = 0
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            name = info.filename.replace('\\', '/').rsplit('/', 1)[-1]
            if info.is_dir() or not name.endswith(('.cfg', '.jpg', '.png')):
                continue
            if name in files or not re.fullmatch(r'[0-9a-fA-F]{32}\.(cfg|jpg|png)', name):
                raise ValueError('duplicate or non-MD5 filename: ' + name)
            total += info.file_size
            if total > 64 * 1024 * 1024 or len(files) >= 256:
                raise ValueError('bundle exceeds Android import limits')
            with archive.open(info) as entry:
                data = entry.read(64 * 1024 * 1024 + 1)
            if len(data) != info.file_size or hashlib.md5(data).hexdigest() != name[:32].lower():
                raise ValueError('size/MD5 mismatch: ' + name)
            files[name] = data
    cfgs = [n for n in files if n.endswith('.cfg')]
    if len(cfgs) != 1:
        raise ValueError('exactly one CFG required')
    root = json.loads(files[cfgs[0]])
    declared = root['files']
    if len(declared) != len(set(declared)) or set(declared) != set(files) - set(cfgs):
        raise ValueError('files[] does not match bundle assets')
    return {'content_id': cfgs[0][:32], 'location': location(root['configuration']),
            'files': len(files), 'bytes': total}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    check = sub.add_parser('check'); check.add_argument('zip')
    create = sub.add_parser('pack')
    create.add_argument('configuration'); create.add_argument('assets'); create.add_argument('output')
    args = parser.parse_args()
    if args.action == 'check':
        print(json.dumps(validate(args.zip), indent=2))
    else:
        cfg = json.loads(Path(args.configuration).read_text(encoding='utf-8'))
        print(pack(cfg, args.assets, args.output))
