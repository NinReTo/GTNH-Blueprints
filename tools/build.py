"""Сборка Java 8 по именам SRG из локальной установки Forge. В итоговый JAR попадает только наш код."""

import lzma
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile


root = Path(__file__).resolve().parents[1]
game, libraries = map(Path, sys.argv[1:])
out = root / "build"
out.mkdir(exist_ok=True)


def one(base, pattern):
    paths = sorted(base.rglob(pattern))
    if len(paths) != 1:
        raise SystemExit(f"Нужен один файл {pattern} в {base}, найдено: {len(paths)}")
    return paths[0]


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


forge = one(libraries / "net/minecraftforge/forge", "forge-1.7.10-10.13.4.1614-1.7.10.jar")
asm = one(libraries, "asm-all-5.0.3.jar")
gson = one(libraries, "gson-2.10.1.jar")
client = game / (game.name + ".jar")
mappings = out / "joined.srg"
with zipfile.ZipFile(forge) as jar:
    mappings.write_bytes(lzma.decompress(jar.read("deobfuscation_data-1.7.10.lzma")))

remap = root / "tools/SrgCompile.java"
dependencies = []
for source in (client, forge):
    target = out / ("srg-" + source.name)
    if not target.exists() or target.stat().st_mtime < max(source.stat().st_mtime, remap.stat().st_mtime):
        temp = target.with_suffix(".tmp")
        run("java", "-cp", asm, remap, mappings, source, temp)
        temp.replace(target)
    dependencies.append(target)

patterns = ("gregtech-*.jar",
            "structurelib-*.jar", "appliedenergistics2-*.jar", "gtnhlib-*.jar", "chisel-*.jar",
            "modularui-*.jar", "modularui2-*.jar", "Railcraft-*.jar")
local_jars = out / "dependencies"
local_jars.mkdir(exist_ok=True)
sources = [one(game / "mods", pattern) for pattern in patterns]
sources += [gson, one(libraries, "lwjgl-2.9.4-nightly-20150209.jar")]
for source in sources:
    target = local_jars / source.name
    if not target.exists() or target.stat().st_mtime != source.stat().st_mtime:
        shutil.copy2(source, target)
    dependencies.append(target)
classpath = ";".join(map(str, dependencies))
with tempfile.TemporaryDirectory(prefix="classes-", dir=out) as classes:
    run("javac", "--release", "8", "-Xlint:-options", "-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", classes,
        *sorted((root / "src/main/java").rglob("*.java")))
    run("jar", "--create", "--file", out / "gtnh-blueprints-0.1.4.jar", "-C", classes, "dev",
        "-C", root / "src/main/resources", ".")
