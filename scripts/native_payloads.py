"""Recursive, content-aware native payload guard for distributable Java archives."""
import io
import re
import struct
import zipfile
from pathlib import PurePosixPath

NATIVE_NAME = re.compile(r"(?i)(?:\.(?:exe|dll|dylib|jnilib|a|lib|o|obj)$|\.so(?:\.\d+)*$)")
MACH = {bytes.fromhex(value) for value in ('feedface', 'cefaedfe', 'feedfacf', 'cffaedfe', 'bebafeca', 'cafebabf', 'bfbafeca')}
MAX_BYTES = 512 * 1024 * 1024


def inspect_archive(source, label=None, depth=0, budget=None):
    """Return offending paths; reject corrupt or oversized nested archives rather than skipping them."""
    label = label or str(source)
    budget = [MAX_BYTES] if budget is None else budget
    if depth > 12:
        raise ValueError(f'{label}: archive nesting exceeds inspection limit')
    violations = []
    with zipfile.ZipFile(source) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            location = f'{label}!/{name}'
            budget[0] -= entry.file_size
            if budget[0] < 0:
                raise ValueError(f'{label}: expanded inspection limit exceeded')
            with archive.open(entry) as stream:
                header = stream.read(16)
                base = PurePosixPath(name).name
                native = (NATIVE_NAME.search(base) or base.lower() in ('ffmpeg', 'ffprobe')
                          or header.startswith((b'\x7fELF', b'MZ', b'!<arch>\n', b'!<thin>\n'))
                          or header[:4] in MACH)
                if header[:4] == b'\xca\xfe\xba\xbe':
                    # This magic is shared with Java class files; a fat Mach-O has an architecture count here.
                    java_class = (name.endswith('.class') and len(header) >= 10
                                  and struct.unpack('>H', header[4:6])[0] in (0, 65535)
                                  and 45 <= struct.unpack('>H', header[6:8])[0] <= 100
                                  and struct.unpack('>H', header[8:10])[0] > 0)
                    native = native or not java_class
                if native:
                    violations.append(location)
                elif header.startswith(b'PK') or name.lower().endswith(('.jar', '.zip')):
                    nested = header + stream.read(MAX_BYTES + 1)
                    if len(nested) > MAX_BYTES:
                        raise ValueError(f'{location}: nested archive exceeds inspection limit')
                    violations.extend(inspect_archive(io.BytesIO(nested), location, depth + 1, budget))
    return violations
