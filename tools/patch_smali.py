#!/usr/bin/env python3
"""Injeta AlertBridge.init(this) no FapApplication.onCreate()."""
import pathlib, re, sys

root = pathlib.Path(sys.argv[1])
files = list(root.glob("smali*/br/com/folha/fap/FapApplication.smali"))
if len(files) != 1:
    sys.exit(f"FapApplication.smali nao encontrado de forma unica: {files}")

f = files[0]
txt = f.read_text()
CALL = "invoke-static {p0}, Lbr/com/folha/fap/alerts/AlertBridge;->init(Landroid/app/Application;)V"
if CALL in txt:
    print("ja aplicado")
    sys.exit(0)

pat = re.compile(
    r"(\.method public onCreate\(\)V.*?"
    r"invoke-super \{p0\}, Landroid/app/Application;->onCreate\(\)V\n)",
    re.S,
)
new, n = pat.subn(lambda m: m.group(1) + "\n    " + CALL + "\n", txt, count=1)
if n != 1:
    sys.exit("Ponto de injecao (super.onCreate) nao encontrado em FapApplication.onCreate")
f.write_text(new)
print("patch aplicado em", f)
