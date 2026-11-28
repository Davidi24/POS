#!/usr/bin/env python3
"""
The app's translations (user, 2026-11-18). The app's words are written in English in the code; their Italian, Spanish and
Albanian sit in shared/src/commonMain/composeResources/files/i18n/strings.tsv (columns en, it, es, sq, keyed by the
English text, `{}` where a value goes; a translation may reorder values with {1}, {2}).

  python3 i18n.py stats              how many of the app's texts are translated
  python3 i18n.py missing [N]        the next N texts still to translate (default 200), one a line
  python3 i18n.py add FILE           add rows written as "en | it | es | sq" (one a line) to the table, after checking them;
                                     a line with only the English keeps it the same in every language (codes, brands)
  python3 i18n.py unused             rows whose English no longer appears in the code
  python3 i18n.py prune              removes those rows
  python3 i18n.py --server CMD       the same for the server's table (emails, guest pages); `--server fill` copies the
                                     rows the app's table already has
"""
import os, re, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(HERE, '..', 'shared', 'src', 'commonMain', 'kotlin')
SERVER = os.path.join(HERE, '..', '..', 'back-end', 'src', 'main', 'java')
TABLE = os.path.join(HERE, '..', 'shared', 'src', 'commonMain', 'composeResources', 'files', 'i18n', 'strings.tsv')
APP_TABLE = TABLE
# The server's own table (emails, guest pages, link pages): `--server` before the command works on it.
SERVER_TABLE = os.path.join(SERVER, '..', 'resources', 'i18n', 'server.tsv')
LANGS = ['en', 'it', 'es', 'sq']
# Words that must never be translated (each language by its own name).
KEEP = {'English', 'Italiano', 'Español', 'Shqip'}
SKIP_FILES = re.compile(r'(Mock|Sample|Fake|Preview|Gallery)[A-Za-z]*\.kt$|/data/api/|Api\.kt$')

def esc(s):
    return s.replace('\\', '\\\\').replace('\t', '\\t').replace('\n', '\\n')

def kotlin_literals(src):
    i, n, out, joined = 0, len(src), [], False
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i); i = n if j < 0 else j; continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3); i = n if j < 0 else j + 3; continue
        if c == "'":
            j = src.find("'", i + 2) if src.startswith("'\\", i) else i + 2
            i = j + 1; continue
        if c == '"':
            j, buf = i + 1, []
            while j < n:
                d = src[j]
                if d == '\\' and j + 1 < n:
                    e = src[j + 1]
                    if e == 'u':
                        buf.append(chr(int(src[j + 2:j + 6], 16))); j += 6; continue
                    buf.append({'n': '\n', 't': '\t', '"': '"', '\\': '\\', '$': '$', "'": "'"}.get(e, e)); j += 2; continue
                if d == '$' and j + 1 < n and src[j + 1] == '{':
                    k, lvl = j + 2, 1
                    while k < n and lvl > 0:
                        if src[k] == '{': lvl += 1
                        elif src[k] == '}': lvl -= 1
                        elif src[k] == '"':
                            k2 = k + 1
                            while k2 < n and src[k2] != '"':
                                if src[k2] == '\\': k2 += 1
                                k2 += 1
                            k = k2
                        k += 1
                    buf.append('\x00'); j = k; continue
                if d == '$' and j + 1 < n and (src[j + 1].isalpha() or src[j + 1] == '_'):
                    k = j + 1
                    while k < n and (src[k].isalnum() or src[k] in '_.'): k += 1
                    if src[k - 1] == '.': k -= 1
                    buf.append('\x00'); j = k; continue
                if d == '"' or d == '\n': break
                buf.append(d); j += 1
            text = ''.join(buf)
            # "A sentence that goes on " + "on the next line": one text, as the app shows it.
            if joined: out[-1] += text
            else: out.append(text)
            i = j + 1
            k = i
            while k < n and src[k] in ' \t\r\n': k += 1
            joined = False
            if k < n and src[k] == '+':
                k += 1
                while k < n and src[k] in ' \t\r\n': k += 1
                if k < n and src[k] == '"' and not src.startswith('"""', k):
                    joined = True; i = k
            continue
        i += 1
    return out

CODEY = re.compile(r"^[A-Z0-9_]+$|^[a-z][A-Za-z0-9]*$|^[a-z0-9_.\-/]+$|^#|^\^|://|^/|\.(png|jpg|jpeg|kt|csv|pdf|svg|xml|json|tsv|txt)$|^%|^[A-Za-z]+\.[A-Za-z]+\(|^\{\}[-_/]|[-_/]\{\}$")

def wanted(raw):
    key = raw.replace('\x00', '{}')
    t = key.strip()
    if not t or t in KEEP: return None
    # Sample catalogue lines ("Mussels, live|ADR-2001|kg|…") are mock data, not words of the app.
    if '|' in t and ' | ' not in t: return None
    letters = re.sub(r'\{\}', '', t)
    if len(re.findall(r'[A-Za-z]', letters)) < 2: return None
    if CODEY.search(t): return None
    if ' ' not in t and re.search(r'[-_/]', t) and '{}' in t: return None
    return key

def app_texts():
    found = collections.OrderedDict()
    for dp, _, fs in sorted(os.walk(APP)):
        for f in sorted(fs):
            p = os.path.join(dp, f)
            if not f.endswith('.kt') or SKIP_FILES.search(p) or '/core/i18n/' in p: continue
            for raw in kotlin_literals(open(p, encoding='utf-8').read()):
                key = wanted(raw)
                if key and key not in found: found[key] = os.path.relpath(p, APP)
    # Messages the server sends that the app shows as they are (errors, refusals). A message built with + becomes one
    # sentence with {} where the values go ("Unknown currency " + code + ". Use…" → "Unknown currency {}. Use…").
    msg = re.compile(r'(?:Exception|throw new [A-Za-z]+)\(\s*(?=")|message\s*=\s*(?=")')
    for dp, _, fs in sorted(os.walk(SERVER)):
        for f in sorted(fs):
            if not f.endswith('.java'): continue
            src = open(os.path.join(dp, f), encoding='utf-8').read()
            for m in msg.finditer(src):
                s = java_message(src, m.end())
                key = wanted(s) if s else None
                if key and ' ' in key and key[0].isupper() and key not in found: found[key] = 'server:' + f
            # Permission and role names and what they do (AppPermission / AppRole: CODE("Name", "What it does", …)): the app
            # shows them as the server sends them.
            if f in ('AppPermission.java', 'AppRole.java'):
                for m in re.finditer(r'^\s+[A-Z_]+\(\s*"([^"]+)",\s*"([^"]+)"', src, re.M):
                    for text in (m.group(1), m.group(2)):
                        key = wanted(text)
                        if key and key not in found: found[key] = 'server:' + f
            # Texts the server makes now and the app shows later (a booking's money lines): Words.en("…").
            for m in re.finditer(r'Words\.en\(\s*(?=")', src):
                text, _ = java_literal(src, m.end())
                key = wanted(text.replace('\x00', '{}'))
                if key and key not in found: found[key] = 'server:' + f
    return found

def java_escape(seq):
    return {'n': '\n', 't': '\t', 's': ' '}.get(seq, seq)

def java_literal(src, i):
    """The string literal or text block at i as Java makes it, and where it ends."""
    n = len(src)
    if src.startswith('"""', i):
        j = src.index('\n', i) + 1
        end = src.index('"""', j)
        while src[end - 1] == '\\': end = src.index('"""', end + 3)
        raw = src[j:end]
        lines = raw.split('\n')
        # The closing delimiter's own line counts for the indentation when it stands alone.
        closing_alone = lines[-1].strip() == ''
        indents = [len(l) - len(l.lstrip(' \t')) for l in lines[:-1] if l.strip()]
        if closing_alone: indents.append(len(lines[-1]))
        else: indents.append(len(lines[-1]) - len(lines[-1].lstrip(' \t')))
        cut = min(indents) if indents else 0
        kept = [l[cut:].rstrip(' \t') for l in lines]
        if closing_alone: kept[-1] = ''
        text = '\n'.join(kept)
        out, k = [], 0
        while k < len(text):
            if text[k] == '\\' and k + 1 < len(text):
                if text[k + 1] == '\n': k += 2; continue
                out.append(java_escape(text[k + 1])); k += 2; continue
            out.append(text[k]); k += 1
        return ''.join(out), end + 3
    j, buf = i + 1, []
    while j < n and src[j] != '"':
        if src[j] == '\\' and j + 1 < n:
            buf.append(java_escape(src[j + 1])); j += 2; continue
        buf.append(src[j]); j += 1
    return ''.join(buf), j + 1

def java_message(src, i):
    """The Java expression at i (string literals joined by +) as one text, \x00 for each value in it."""
    n, out, depth = len(src), [], 0
    while i < n:
        while i < n and src[i] in ' \t\r\n': i += 1
        if i >= n: break
        if src[i] == '"':
            text, i = java_literal(src, i)
            out.append(text)
        else:
            # A value: up to the next + (or the end of the argument) at this depth.
            start = i
            while i < n:
                c = src[i]
                if c in '([{': depth += 1
                elif c in ')]}':
                    if depth == 0: break
                    depth -= 1
                elif c == '"':
                    i += 1
                    while i < n and src[i] != '"':
                        if src[i] == '\\': i += 1
                        i += 1
                elif depth == 0 and c in '+,;': break
                i += 1
            if i == start: break
            out.append('\x00')
        while i < n and src[i] in ' \t\r\n': i += 1
        if i < n and src[i] == '+': i += 1; continue
        break
    text = ''.join(out)
    # "…".formatted(x) / %s, %d: values too.
    return re.sub(r'%[sd]', '\x00', text)

def server_texts():
    """The server's words: templates given to Words.t / Words.en, and the messages it sends."""
    found = collections.OrderedDict()
    call = re.compile(r'Words\.(t|en)\(')
    for dp, _, fs in sorted(os.walk(SERVER)):
        for f in sorted(fs):
            if not f.endswith('.java') or f == 'Words.java': continue
            src = open(os.path.join(dp, f), encoding='utf-8').read()
            for m in call.finditer(src):
                i = m.end()
                if m.group(1) == 't':
                    # Past the language: up to the comma at this depth.
                    depth = 0
                    while i < len(src):
                        c = src[i]
                        if c in '([{': depth += 1
                        elif c in ')]}':
                            if depth == 0: break
                            depth -= 1
                        elif c == ',' and depth == 0: i += 1; break
                        i += 1
                while i < len(src) and src[i] in ' \t\r\n': i += 1
                if i >= len(src) or src[i] != '"': continue
                text, _ = java_literal(src, i)
                key = text.replace('\x00', '{}')
                if key.strip() and key not in found: found[key] = 'server:' + f
    for key, where in app_texts().items():
        if where.startswith('server:') and key not in found: found[key] = where
    return found

def table():
    rows = collections.OrderedDict()
    if os.path.exists(TABLE):
        for line in open(TABLE, encoding='utf-8').read().split('\n')[1:]:
            if line.strip():
                cells = line.split('\t'); rows[cells[0]] = cells
    return rows

def holes(s):
    return len(re.findall(r'\{\d*\}', s))

def main():
    global TABLE
    server = '--server' in sys.argv
    if server:
        sys.argv.remove('--server')
        TABLE = SERVER_TABLE
        os.makedirs(os.path.dirname(TABLE), exist_ok=True)
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'stats'
    texts, rows = (server_texts() if server else app_texts()), table()
    if server and cmd == 'fill':
        # Rows the app's table already has (the server's messages) are copied over.
        app_rows = collections.OrderedDict()
        for line in open(APP_TABLE, encoding='utf-8').read().split('\n')[1:]:
            if line.strip():
                cells = line.split('\t'); app_rows[cells[0]] = cells
        copied = 0
        for k in texts:
            e = esc(k)
            if e not in rows and e in app_rows: rows[e] = app_rows[e]; copied += 1
        with open(TABLE, 'w', encoding='utf-8') as out:
            out.write('\t'.join(LANGS) + '\n')
            for k in texts:
                e = esc(k)
                if e in rows: out.write('\t'.join(rows[e][:4]) + '\n')
        print(f'copied {copied} rows from the app')
        return
    escaped = {esc(k): k for k in texts}
    # Words with spaces around them in the code are kept without the spaces (the app looks them up without them).
    for k in list(escaped):
        if k.strip() != k and k.strip(): escaped.setdefault(k.strip(), escaped[k])
    def done(k): return k in rows or k.strip() in rows
    if cmd == 'stats':
        keys = [k for k in escaped if k.strip() == k]
        finished = sum(1 for k in keys if k in rows)
        print(f'{finished} of {len(keys)} texts translated ({len(rows)} rows in the table)')
    elif cmd == 'missing':
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 200
        left = [k for k in escaped if k.strip() == k and not done(k)]
        for k in left[:n]: print(k)
        print(f'# {len(left)} left', file=sys.stderr)
    elif cmd == 'unused':
        for k in rows:
            if k not in escaped: print(k)
    elif cmd == 'prune':
        gone = [k for k in rows if k not in escaped]
        with open(TABLE, 'w', encoding='utf-8') as out:
            out.write('\t'.join(LANGS) + '\n')
            for k in escaped:
                if k in rows: out.write('\t'.join(rows[k][:4]) + '\n')
        print(f'removed {len(gone)} rows no longer in the code')
    elif cmd == 'add':
        bad, added = [], 0
        for no, line in enumerate(open(sys.argv[2], encoding='utf-8').read().split('\n'), 1):
            if not line.strip() or line.startswith('#'): continue
            cells = [c.strip() for c in line.split(' | ')]
            # One cell = the same in every language (codes, brands, examples).
            if len(cells) == 1: cells = cells * 4
            if len(cells) != 4: bad.append(f'{no}: {len(cells)} cells: {line[:80]}'); continue
            en = cells[0]
            if en not in escaped: bad.append(f'{no}: not in the code: {en[:80]}'); continue
            same = all(c == en for c in cells[1:])
            if not same and any(holes(c) != en.count('{}') for c in cells[1:]): bad.append(f'{no}: values differ: {en[:80]}'); continue
            rows[en] = cells; added += 1
        with open(TABLE, 'w', encoding='utf-8') as out:
            out.write('\t'.join(LANGS) + '\n')
            for k in escaped:
                if k in rows: out.write('\t'.join(rows[k][:4]) + '\n')
            for k, cells in rows.items():
                if k not in escaped: out.write('\t'.join(cells[:4]) + '\n')
        print(f'added {added}'); [print('  ' + b) for b in bad]
    else:
        print(__doc__)

if __name__ == '__main__':
    main()
