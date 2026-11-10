import struct
p = r'D:/Minecraft/异界随行诗/.minecraft/versions/otherworld_traveling_poems/apricity/hud/fonts/minecraft-regular.otf'
d = open(p, 'rb').read()
print('sfnt', d[:4], 'size', len(d))
num = struct.unpack('>H', d[4:6])[0]
tables = {}
for i in range(num):
    off = 12 + i * 16
    tag = d[off:off+4].decode('latin1')
    toff, tlen = struct.unpack('>II', d[off+8:off+16])
    tables[tag] = (toff, tlen)
print('tables:', sorted(tables))
if 'head' in tables:
    o = tables['head'][0]
    upm = struct.unpack('>H', d[o+18:o+20])[0]
    print('unitsPerEm', upm)
if 'OS/2' in tables:
    o = tables['OS/2'][0]
    ver = struct.unpack('>H', d[o:o+2])[0]
    print('OS/2 version', ver)
    # sxHeight, sCapHeight
    if ver >= 2:
        sxh = struct.unpack('>h', d[o+86:o+88])[0]
        scap = struct.unpack('>h', d[o+88:o+90])[0]
        print('sxHeight', sxh, 'sCapHeight', scap)
    upm2 = struct.unpack('>H', d[o+... ]) if False else None
if 'name' in tables:
    o, l = tables['name']
    cnt, strOff = struct.unpack('>HH', d[o+2:o+6])
    for i in range(cnt):
        r = o + 6 + i * 12
        pid, eid, lid, nid, ln, off = struct.unpack('>HHHHHH', d[r:r+12])
        s = d[o+strOff+off:o+strOff+off+ln]
        try:
            txt = s.decode('utf-16-be') if pid == 3 else s.decode('latin1')
        except Exception:
            continue
        if nid in (1, 2, 4, 6):
            print({1:'family',2:'subfamily',4:'fullname',6:'psname'}[nid], '=', txt)
