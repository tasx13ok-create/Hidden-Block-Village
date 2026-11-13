"""Read-only, metadata-only inventory of a world's CustomNPCs folder (compatibility intake).

Never executes scripts or commands and never copies dialogue/quest/script text into its report:
strings are reported only as lengths, files by SHA256. The output is aggregate metadata suitable
for reviewed public evidence (counts, ID graph, broken references, dependency namespaces).

Format assumption (unverified against the real recovered files): CustomNPCs 1.12 stores dialogs
as ``dialogs/<category>/<id>.json``, quests as ``quests/<category>/<id>.json`` and clones as
``clones/<tab>/<name>.json`` using its NBT-JSON writer (strict JSON plus typed numeric suffixes
such as ``1b``/``2s``/``3L``/``1.0f``/``1.0d`` and ``[I; ...]`` arrays). The parser accepts both
strict JSON and that dialect; anything else is reported as unparsed, never guessed.
"""
import hashlib
import json
from pathlib import Path
import re

MAX_FILE_BYTES = 4 * 1024 * 1024
MAX_FILES = 20000
_NAMESPACED = re.compile(r'^([a-z0-9_.-]{1,64}):[a-z0-9_./-]{1,128}$')


def nbt_json_to_json(text):
    """Strip CustomNPCs NBT-JSON typed suffixes outside strings so json can parse it."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == '\\' else 1
            out.append(text[i:j + 1])
            i = j + 1
            continue
        if c == '[' and re.match(r'\[\s*[BIL]\s*;', text[i:i + 6]):
            out.append('[')
            i = text.index(';', i) + 1
            continue
        if c in '-0123456789':
            m = re.match(r'-?\d+(\.\d+)?([eE][-+]?\d+)?', text[i:])
            if m:
                out.append(m.group(0))
                i += len(m.group(0))
                if i < n and text[i] in 'bBsSlLfFdD':
                    i += 1
                continue
        out.append(c)
        i += 1
    return ''.join(out)


def parse(raw):
    text = raw.decode('utf-8', 'replace')
    try:
        return json.loads(text)
    except ValueError:
        return json.loads(nbt_json_to_json(text))


def _walk(value):
    if isinstance(value, dict):
        for k, v in value.items():
            yield k, v
            yield from _walk(v)
    elif isinstance(value, list):
        for v in value:
            yield from _walk(v)


def _namespaces(doc):
    found = set()
    for key, value in _walk(doc):
        if isinstance(value, str):
            m = _NAMESPACED.match(value)
            if m and m.group(1) != 'minecraft':
                found.add(m.group(1))
    return found


def _int(doc, *keys):
    for k in keys:
        v = doc.get(k) if isinstance(doc, dict) else None
        if isinstance(v, int) and not isinstance(v, bool):
            return v
    return None


def inventory(world_dir):
    world_dir = Path(world_dir)
    root = world_dir / 'customnpcs'
    report = {'schema': 1, 'scope': 'metadata only; no text, script or command content copied; nothing executed',
              'customnpcs_present': root.is_dir(), 'files': 0, 'by_area': {}, 'unparsed': [],
              'dialogs': {}, 'quests': {}, 'clones': [], 'scripts': {'files': 0, 'total_chars': 0},
              'commands': {'dialog_options_with_commands': 0, 'total_command_chars': 0},
              'dependency_namespaces': [], 'broken_references': [], 'file_hashes': []}
    if not root.is_dir():
        return report
    namespaces = set()
    files = sorted(p for p in root.rglob('*') if p.is_file() and not p.is_symlink())
    if len(files) > MAX_FILES:
        raise ValueError('More than %d files under customnpcs; refusing unbounded scan' % MAX_FILES)
    dialog_links, quest_links = [], []
    clone_count = 0
    for path in files:
        rel = path.relative_to(root).as_posix()
        area = rel.split('/', 1)[0] if '/' in rel else '(root)'
        if area == 'clones':
            # Clone file names are character names; never publish them.
            clone_count += 1
            parts = rel.split('/')
            rel = '/'.join(parts[:-1] + ['clone_%d%s' % (clone_count, path.suffix)])
        report['by_area'][area] = report['by_area'].get(area, 0) + 1
        report['files'] += 1
        size = path.stat().st_size
        if size > MAX_FILE_BYTES:
            report['unparsed'].append({'path': rel, 'reason': 'too_large', 'bytes': size})
            continue
        raw = path.read_bytes()
        report['file_hashes'].append({'path': rel, 'bytes': size, 'sha256': hashlib.sha256(raw).hexdigest()})
        if path.suffix in ('.js', '.py', '.lua') or area == 'scripts' and path.suffix != '.json':
            report['scripts']['files'] += 1
            report['scripts']['total_chars'] += len(raw.decode('utf-8', 'replace'))
            continue
        if path.suffix != '.json':
            continue
        try:
            doc = parse(raw)
        except ValueError:
            report['unparsed'].append({'path': rel, 'reason': 'not_json_or_nbt_json'})
            continue
        namespaces |= _namespaces(doc)
        for key, value in _walk(doc):
            if key in ('Script', 'ScriptContent', 'Scripts') and isinstance(value, str):
                report['scripts']['embedded_script_chars'] = report['scripts'].get('embedded_script_chars', 0) + len(value)
        if area == 'dialogs':
            did = _int(doc, 'DialogId') if isinstance(doc, dict) else None
            if did is None:
                try:
                    did = int(path.stem)
                except ValueError:
                    did = None
            options = []
            for k, v in _walk(doc):
                if k == 'Option' and isinstance(v, dict):
                    options.append(v)
            entry = {'category': rel.split('/')[1] if rel.count('/') >= 2 else '', 'options': len(options),
                     'title_chars': len(doc.get('DialogTitle', '')) if isinstance(doc.get('DialogTitle'), str) else 0,
                     'text_chars': len(doc.get('DialogText', '')) if isinstance(doc.get('DialogText'), str) else 0}
            quest = _int(doc, 'DialogQuest')
            if quest is not None and quest >= 0:
                entry['quest'] = quest
                quest_links.append((did, quest))
            for opt in options:
                target = _int(opt, 'Dialog')
                if target is not None and target >= 0:
                    dialog_links.append((did, target))
                cmd = opt.get('DialogCommand')
                if isinstance(cmd, str) and cmd.strip():
                    report['commands']['dialog_options_with_commands'] += 1
                    report['commands']['total_command_chars'] += len(cmd)
            report['dialogs'][str(did)] = entry
        elif area == 'quests':
            qid = _int(doc, 'QuestId', 'Id')
            if qid is None:
                try:
                    qid = int(path.stem)
                except ValueError:
                    qid = None
            report['quests'][str(qid)] = {'category': rel.split('/')[1] if rel.count('/') >= 2 else '',
                                          'next_quest': _int(doc, 'NextQuestId')}
        elif area == 'clones':
            report['clones'].append({'tab': rel.split('/')[1] if rel.count('/') >= 2 else '',
                                     'entity_id': doc.get('id') if isinstance(doc.get('id'), str) else None,
                                     'has_script_data': any(k.lower().startswith('script') for k, _ in _walk(doc)),
                                     'sha256': report['file_hashes'][-1]['sha256']})
    dialogs = set(report['dialogs'])
    quests = set(report['quests'])
    for src, dst in dialog_links:
        if str(dst) not in dialogs:
            report['broken_references'].append({'from_dialog': src, 'to_dialog': dst})
    for src, q in quest_links:
        if str(q) not in quests:
            report['broken_references'].append({'from_dialog': src, 'to_quest': q})
    report['dependency_namespaces'] = sorted(namespaces)
    report['proposed_mapping'] = {
        'dialogs': 'translate to Residents DialogueBook pages only after review; options with commands '
                   'need an explicit owned action (DialogueBook has no command action)',
        'quests': 'map to gate/contract objectives through the adapter in content/expeditions/README.md',
        'clones': 'keep as an optional retained CustomNPCs profile or re-author as an original resident; '
                  'scripted behavior is unsupported until tested',
    }
    return report
