"""Writes the atlas muscle names (without .l/.r; a median muscle has no side) to .work/atlas-muscles.txt: the names muscles.json accepts."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import HERE, collect  # noqa: E402

names = sorted({m.name[:-2] if m.name.endswith((".l", ".r")) else m.name for m in collect("Muscular system")})
with open(os.path.join(HERE, ".work", "atlas-muscles.txt"), "w", encoding="utf-8") as handle:
    handle.write("\n".join(names) + "\n")
print("ATLAS", len(names), "names written")
