"""Translate the pinned Flowseal batch files to data consumed by the Android root runner."""
import json
import re
import shutil
import sys
from pathlib import Path

source, dest = map(Path, sys.argv[1:])
dest.mkdir(parents=True, exist_ok=True)
profiles = {}
for bat in sorted(source.glob("general*.bat")):
    _, sep, command = bat.read_text(encoding="utf-8-sig").partition('"%BIN%winws.exe"')
    if not sep:
        raise ValueError(bat)
    args = []
    capture = {}
    for token in command.replace("^\n", " ").split():
        token = token.replace("^!", "!").replace('"', '').replace('%GameFilterTCP%', '12').replace('%GameFilterUDP%', '12')
        for variable, directory in [('%BIN%', 'bin'), ('%LISTS%', 'lists')]:
            if variable in token:
                filename = token.split(variable)[1]
                if not re.fullmatch(r'[\w.-]+', filename):
                    raise ValueError(token)
                target = dest / filename
                original = source / directory / filename
                if original.is_file():
                    shutil.copyfile(original, target)
                elif filename in {'ipset-exclude-user.txt', 'list-exclude-user.txt', 'list-general-user.txt'}:
                    target.write_text('203.0.113.113/32\n' if filename.startswith('ipset') else 'domain.example.abc\n')
                else:
                    raise ValueError(filename)
                token = token.replace("^!", "!").replace(variable, '')
        if not token.startswith('--') or '%' in token:
            raise ValueError(token)
        if token.startswith('--wf-'):
            flag, ports = token[5:].split('=')
            capture[flag] = ports
        else:
            args.append(token)
    assert set(capture) == {'tcp', 'udp'}
    profiles[bat.stem] = {**capture, 'args': args}
(dest / 'profiles.json').write_text(json.dumps(profiles, indent=2) + '\n')
shutil.copyfile(source / 'LICENSE.txt', dest / 'LICENSE.txt')
