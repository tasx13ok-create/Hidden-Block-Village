#!/usr/bin/env python3
"""QA only: copy a production jar, rewriting SRG name constants (field_/func_) in its classes to MCP
names so its reflection works in the readable-name dev client. Never shipped."""
import csv, os, struct, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
base = os.path.expanduser(os.environ.get('MCP_CSV_DIR', '~/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_stable/39/'))
names = {}
for f in ('fields.csv', 'methods.csv'):
    for row in csv.DictReader(open(base + f)): names[row['searge']] = row['name']
def rewrite(data):
    assert data[:4] == b'\xca\xfe\xba\xbe'
    n = struct.unpack('>H', data[8:10])[0]; i = 10; outp = [data[:10]]; k = 1; changed = 0
    while k < n:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i+1:i+3])[0]; s = data[i+3:i+3+ln]
            t = s.decode('utf-8', 'surrogatepass')
            if t in names: s = names[t].encode(); changed += 1
            outp.append(bytes([1]) + struct.pack('>H', len(s)) + s); i += 3 + ln
        else:
            size = {3:5,4:5,5:9,6:9,7:3,8:3,9:5,10:5,11:5,12:5,15:4,16:3,18:5,19:3,20:3}[tag]
            outp.append(data[i:i+size]); i += size
            if tag in (5, 6): k += 1
        k += 1
    outp.append(data[i:])
    return b''.join(outp), changed
total = 0
with zipfile.ZipFile(src) as z, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as o:
    for info in z.infolist():
        d = z.read(info.filename)
        if info.filename.startswith('village/bijuu/') and info.filename.endswith('.class'):
            d, c = rewrite(d); total += c
        o.writestr(info, d)
print('rewrote', total, 'constants ->', out)
