"""Collect exact local dependency notices after a locked full-editor build.

Reads public source/package manifests only. No credentials, environment files or
private media. Missing license text fails closed; output is not legal approval.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib
from urllib.parse import urlparse

source, crate, destination = Path(sys.argv[1]).resolve(), sys.argv[2], Path(sys.argv[3]).resolve()
if destination.exists():
    raise SystemExit('Choose a fresh notices output directory; existing files are kept.')
cargo_home = Path(os.environ.get('CARGO_HOME', str(Path.home() / '.cargo'))).resolve()
cargo = os.environ.get('CARGO', 'cargo')
result = subprocess.run([cargo, 'tree', '-p', crate, '--target', 'wasm32-unknown-unknown', '--locked', '--offline', '--edges', 'normal,build', '--prefix', 'none', '--format', '{p} :: {l}'], cwd=source, capture_output=True, text=True, check=True)
packages = {}
for line in result.stdout.splitlines():
    match = re.match(r'^([A-Za-z0-9_-]+) v([^ ]+)(.*?) :: (.*?)(?: \(\*\))?$', line)
    if match:
        name, version, origin, license_id = match.groups()
        packages[(name, version, origin)] = license_id
roots = [source] + list((cargo_home / 'registry/src').glob('*')) + list((cargo_home / 'git/checkouts').glob('*/*'))
index = {}
for root in roots:
    manifests = root.glob('*/Cargo.toml') if root.parent.name == 'src' else root.rglob('Cargo.toml')
    for manifest in manifests:
        if any(part in ('target', '.git', 'node_modules') for part in manifest.relative_to(root).parts):
            continue
        try:
            package = tomllib.loads(manifest.read_text()).get('package', {})
            name, version = package.get('name'), package.get('version')
            if isinstance(version, dict):
                for parent in manifest.parents:
                    workspace = parent / 'Cargo.toml'
                    if workspace.exists():
                        inherited = tomllib.loads(workspace.read_text()).get('workspace', {}).get('package', {}).get('version')
                        if inherited:
                            version = inherited
                            break
                    if parent == root:
                        break
            if isinstance(name, str) and isinstance(version, str):
                index.setdefault((name, version), []).append((manifest, root, package))
        except (ValueError, OSError):
            continue
notices = []
missing = []
fallback_root = Path(__file__).resolve().parent / 'licenses/dependency-fallbacks'
fallback_pins = json.loads((fallback_root / 'pins.json').read_text())['packages']
def repository_root(value):
    parsed = urlparse(value or '')
    if parsed.scheme == 'https' and parsed.hostname == 'github.com':
        return 'https://github.com/' + '/'.join(parsed.path.strip('/').split('/')[:2]).removesuffix('.git')
    return ''
legal_name = re.compile(r'(^|[-_.])(licen[sc]e|unlicense|copying|notice|ofl|ufl|copyright)', re.I)
for number, ((name, version, origin), license_id) in enumerate(sorted(packages.items())):
    candidates = index.get((name, version), [])
    commit = re.search(r'#([0-9a-f]{7,40})', origin)
    if commit:
        candidates = [candidate for candidate in candidates if any(re.fullmatch(r'[0-9a-f]{7,40}', part) and (part.startswith(commit.group(1)) or commit.group(1).startswith(part)) for part in candidate[0].parts)]
    local = re.search(r'\((/[^)]+)\)', origin)
    if local:
        candidates = [candidate for candidate in candidates if candidate[0].parent == Path(local.group(1))]
    if not candidates:
        missing.append(f'{name}@{version}: source not found')
        continue
    manifest, root, package = candidates[0]
    files = set()
    # Root licenses also apply to git workspace members. Never walk outside public source roots.
    for parent in manifest.parents:
        if not parent.is_relative_to(root):
            break
        for item in parent.iterdir():
            if legal_name.search(item.name):
                if item.is_file():
                    files.add(item)
                elif item.is_dir():
                    files.update(child for child in item.rglob('*') if child.is_file())
        if parent == root:
            break
    for item in manifest.parent.rglob('*'):
        if item.is_file() and (legal_name.search(item.name) or ('fonts' in item.parts and item.suffix.lower() in ('.txt', '.attribution'))):
            files.add(item)
    declared = package.get('license-file')
    if isinstance(declared, str):
        item = (manifest.parent / declared).resolve()
        if item.is_relative_to(root) and legal_name.search(item.name) and item.is_file():
            files.add(item)
    fallback = None
    if not files:
        fallback = fallback_pins.get(f'{name}@{version}')
        vcs_file = manifest.parent / '.cargo_vcs_info.json'
        vcs = json.loads(vcs_file.read_text()) if vcs_file.exists() else {}
        if not fallback or fallback['repository'] != repository_root(package.get('repository') or package.get('homepage')) or fallback['revision'] != vcs.get('git', {}).get('sha1'):
            missing.append(f'{name}@{version}: license text not found ({license_id})')
            continue
        for pin in fallback['files']:
            if Path(pin['file']).name != pin['file']:
                raise SystemExit('Invalid fallback notice path.')
            item = fallback_root / pin['file']
            data = item.read_bytes()
            if len(data) != pin['bytes'] or hashlib.sha256(data).hexdigest() != pin['sha256']:
                raise SystemExit('Pinned fallback notice bytes changed.')
            files.add(item)
    files = {item for item in files if not item.is_symlink() and (item.resolve().is_relative_to(root) or (fallback and item.resolve().is_relative_to(fallback_root)))}
    if not files:
        missing.append(f'{name}@{version}: no public source notice files')
        continue
    copied = []
    folder = destination / f'{number:03d}-{name}-{version}'
    for item in sorted(files):
        data = item.read_bytes()
        if len(data) > 2 * 1024 * 1024:
            raise SystemExit(f'Unexpectedly large notice for {name}; review public source manually.')
        digest = hashlib.sha256(data).hexdigest()
        target = folder / f'{digest[:12]}-{item.name}'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        copied.append({'file': str(target.relative_to(destination)), 'bytes': len(data), 'sha256': digest})
    public_source = origin.strip().strip('()') if 'https://' in origin else (f'https://crates.io/crates/{name}/{version}' if not local else 'pinned-editor-source')
    if fallback:
        public_source = fallback['repository'] + '/tree/' + fallback['revision'] + '/' + vcs.get('path_in_vcs', '')
    notices.append({'name': name, 'version': version, 'license': license_id, 'source': public_source, 'notices': copied})
if missing:
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'MISSING.json').write_text(json.dumps(missing, indent=2) + '\n')
    raise SystemExit('Missing dependency license evidence: ' + '; '.join(missing))
(destination / 'index.json').write_text(json.dumps({'version': 1, 'crate': crate, 'packages': notices}, indent=2) + '\n')
print(f'Collected notices for {len(notices)} pinned dependency packages.')
