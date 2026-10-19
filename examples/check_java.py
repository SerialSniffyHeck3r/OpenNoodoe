#!/usr/bin/env python3
"""Compile the production protocol classes and check public wire vectors (JDK 17+)."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
def tool(name):
    home = os.environ.get('JAVA_HOME')
    path = Path(home)/'bin'/(name+'.exe' if os.name=='nt' else name) if home else None
    return str(path) if path and path.is_file() else shutil.which(name) or name

with tempfile.TemporaryDirectory(prefix='opennoodoe-codec-') as out:
    sources = sorted((root/'android/app/src/main/java/io/opennoodoe/app/protocol').glob('*.java'))
    subprocess.run([tool('javac'), '-encoding', 'UTF-8', '-d', out,
                    *map(str, sources), str(root/'examples/ProtocolCheck.java')], check=True)
    subprocess.run([tool('java'), '-cp', out, 'ProtocolCheck',
                    str(root/'protocol/test-vectors/stock.tsv')], check=True)
