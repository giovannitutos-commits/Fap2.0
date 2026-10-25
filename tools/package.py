#!/usr/bin/env python3
"""Copia o APK recompilado, remove assinaturas antigas e adiciona o dex extra."""
import re, sys, zipfile

src, extra, out = sys.argv[1:4]
SIG = re.compile(r"META-INF/(MANIFEST\.MF|.*\.(SF|RSA|DSA|EC))$")

with zipfile.ZipFile(src) as zin:
    names = zin.namelist()
    idx = [int(m.group(1) or 1) for n in names if (m := re.fullmatch(r"classes(\d*)\.dex", n))]
    nxt = max(idx) + 1
    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            if SIG.match(info.filename):
                continue
            data = zin.read(info.filename)
            ctype = zipfile.ZIP_STORED if info.filename == "resources.arsc" else info.compress_type
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = ctype
            zi.external_attr = info.external_attr
            zout.writestr(zi, data)
        zi = zipfile.ZipInfo(f"classes{nxt}.dex", date_time=(2024, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, open(extra, "rb").read())
print(f"adicionado classes{nxt}.dex")
