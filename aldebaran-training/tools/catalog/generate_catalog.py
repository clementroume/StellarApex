#!/usr/bin/env python3
"""Generates the private catalog migrations of Aldebaran (lot E, D190 / D197).

Reads the catalog of a local database (muscles, movements and catalog WODs, the WODs with a
``code``) and writes repeatable Flyway migrations to ``src/main/resources/db/catalog`` (git-ignored:
the catalog is part of the value of the application and never enters the repository):

    R__10_Muscles.sql            R__20_Benchmarks.sql
    R__11_Movements.sql          R__21_Girls.sql
    R__12_Movement_Chains.sql    R__22_Heroes.sql
    R__13_Usual_Names.sql        R__30_Season_2011.sql ... R__45_Season_2026.sql

Each file upserts by stable code and never deletes a catalog entry: Flyway replays a file when its
checksum changes, so a fix only replays its own file and a new season is a new file. Rows that only
describe an entry (roles, adjustments, usual names, equipment, Scaled lines...) are deleted and
inserted again for the entries of the file; nothing references them. WOD lines are updated in place
by (WOD code, order): scores reference them, so the generator never deletes one. A removal (entry
or WOD line) goes through a versioned migration written by hand; the generator lists what
disappeared since its last run (``catalog-manifest.json``) and writes nothing until it is run again
with ``--allow-removals``.

The script only uses the standard library and reads the database through ``docker exec <container>
psql`` (the credentials are those of the container). Usage, from ``aldebaran-training``:

    python3 tools/catalog/generate_catalog.py [--container castor-db] [--out DIR]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

SCHEMA = "aldebaran"
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "src/main/resources/db/catalog"
MANIFEST = "catalog-manifest.json"

# Columns never copied: identity, audit, optimistic lock (set by the SQL)
SKIPPED = {"id", "created_at", "updated_at", "version"}

# Catalog WOD files by category (competitions go to their season file)
REFERENCE_FILES = {"BENCHMARK": "R__20_Benchmarks", "GIRLS": "R__21_Girls", "HERO": "R__22_Heroes"}
COMPETITIONS = ["OPEN", "REGIONALS", "QUARTERFINALS", "SEMIFINALS", "GAMES"]
FIRST_SEASON = 2011
OTHER_FILE = "R__29_Other_Wods"


# --- Database access -------------------------------------------------------------------------


class Database:
    """Reads the catalog tables through psql in the database container."""

    def __init__(self, container: str):
        self.container = container
        self.types: dict[str, dict[str, str]] = {}

    def query(self, sql: str):
        command = [
            "docker", "exec", "-i", self.container, "sh", "-c",
            'psql -X -q -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
        ]
        result = subprocess.run(command, input=sql, capture_output=True, text=True, check=False)
        if result.returncode != 0:
            sys.exit(f"psql failed: {result.stderr.strip()}")
        return json.loads(result.stdout or "null")

    def rows(self, table: str) -> list[dict]:
        return self.query(f"SELECT COALESCE(json_agg(t), '[]') FROM {SCHEMA}.{table} t;")

    def columns(self, table: str) -> dict[str, str]:
        """Column name → SQL type, in table order."""
        if table not in self.types:
            rows = self.query(
                "SELECT COALESCE(json_agg(json_build_object('c', column_name, 't', data_type)"
                " ORDER BY ordinal_position), '[]') FROM information_schema.columns"
                f" WHERE table_schema = '{SCHEMA}' AND table_name = '{table}';")
            if not rows:
                sys.exit(f"Unknown table {SCHEMA}.{table}")
            self.types[table] = {r["c"]: r["t"] for r in rows}
        return self.types[table]


# --- SQL rendering ---------------------------------------------------------------------------


def literal(value, sql_type: str) -> str:
    """A typed SQL literal (every literal is cast, so VALUES lists never guess a type)."""
    if value is None:
        return f"NULL::{sql_type}"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return f"{value!r}::{sql_type}"
    text = str(value).replace("'", "''")
    return f"'{text}'::{sql_type}"


def values(rows: list[list[str]]) -> str:
    return ",\n".join("  (" + ", ".join(row) + ")" for row in rows)


def in_list(codes) -> str:
    return ", ".join(literal(c, "varchar") for c in sorted(codes))


class Catalog:
    """The catalog read from the database, with every reference translated to stable codes."""

    def __init__(self, db: Database):
        self.db = db
        self.muscles = db.rows("muscles")
        self.movements = db.rows("movements")
        self.wods = [w for w in db.rows("wods") if w.get("code")]
        self.muscle_code = {m["id"]: m["code"] for m in self.muscles}
        self.movement_code = {m["id"]: m["code"] for m in self.movements}
        self.wod_code = {w["id"]: w["code"] for w in self.wods}
        self.lines = [l for l in db.rows("wod_movements") if l["wod_id"] in self.wod_code]
        self.line_key = {l["id"]: (self.wod_code[l["wod_id"]], l["order_index"]) for l in self.lines}
        self.scaled = [s for s in db.rows("wod_scaled_lines") if s["wod_movement_id"] in self.line_key]
        self.scaled_key = {s["id"]: self.line_key[s["wod_movement_id"]] for s in self.scaled}

    def children(self, table: str, owner: str, owner_codes: dict) -> list[dict]:
        return [r for r in self.db.rows(table) if r[owner] in owner_codes]


# --- Statements ------------------------------------------------------------------------------


def upsert_entities(db: Database, table: str, rows: list[dict], overrides: dict | None = None) -> str:
    """INSERT ... ON CONFLICT (code) DO UPDATE for the entries, in the given order."""
    overrides = overrides or {}
    types = db.columns(table)
    cols = [c for c in types if c not in SKIPPED and c not in ("parent_id",)]
    has_version = "version" in types
    insert_cols = cols + ["created_at", "updated_at"] + (["version"] if has_version else [])
    body = []
    for row in rows:
        vals = [literal(overrides.get(c, row.get(c)) if c in overrides else row.get(c), types[c])
                for c in cols]
        vals += ["now()", "now()"] + (["0"] if has_version else [])
        body.append(vals)
    updates = [f"{c} = EXCLUDED.{c}" for c in cols if c != "code"] + ["updated_at = now()"]
    if has_version:
        updates.append(f"version = {table}.version + 1")
    return (f"INSERT INTO {table} ({', '.join(insert_cols)}) VALUES\n{values(body)}\n"
            f"ON CONFLICT (code) DO UPDATE SET\n  " + ",\n  ".join(updates) + ";\n")


def replace_children(db: Database, table: str, rows: list[dict], refs: dict, owner: str,
                     owner_sql: str | None, owner_codes) -> str:
    """Deletes the rows of the owners of the file, then inserts them again.

    ``refs`` maps a foreign key column to ``(alias, join SQL, code(s) of the row)``: the row's
    referenced entry is found again by its code.
    """
    types = db.columns(table)
    plain = [c for c in types if c not in SKIPPED and c not in refs]
    out = []
    if owner_sql and owner_codes:  # None when the rows were just deleted with their owner (cascade)
        out.append(f"DELETE FROM {table} WHERE {owner} IN ({owner_sql.format(codes=in_list(owner_codes))});\n")
    if not rows:
        return "".join(out)
    ref_cols, ref_names, joins, selects = [], [], [], []
    for col, (alias, join, key_names, _) in refs.items():
        ref_cols.append(col)
        ref_names += key_names
        joins.append(join)
        selects.append(f"{alias}.id")
    body = []
    for row in rows:
        vals = []
        for col, (_, _, key_names, key_of) in refs.items():
            key = key_of(row[col])
            key = key if isinstance(key, tuple) else (key,)
            vals += [literal(k, "integer" if isinstance(k, int) else "varchar") for k in key]
        vals += [literal(row.get(c), types[c]) for c in plain]
        body.append(vals)
    names = ref_names + plain
    out.append(
        f"INSERT INTO {table} ({', '.join(ref_cols + plain)})\n"
        f"SELECT {', '.join(selects + ['v.' + c for c in plain])}\n"
        f"FROM (VALUES\n{values(body)}\n) AS v({', '.join(names)})\n"
        + "\n".join(joins) + ";\n")
    return "".join(out)


def ref_entity(table: str, alias: str, key: str, codes: dict) -> tuple:
    return (alias, f"JOIN {table} {alias} ON {alias}.code = v.{key}", [key], codes.get)


def ref_line(catalog: Catalog, alias: str) -> tuple:
    return (alias,
            f"JOIN wods {alias}_w ON {alias}_w.code = v.{alias}_wod\n"
            f"JOIN wod_movements {alias} ON {alias}.wod_id = {alias}_w.id"
            f" AND {alias}.order_index = v.{alias}_ord",
            [f"{alias}_wod", f"{alias}_ord"], catalog.line_key.get)


def ref_scaled(catalog: Catalog, alias: str) -> tuple:
    return (alias,
            f"JOIN wods {alias}_w ON {alias}_w.code = v.{alias}_wod\n"
            f"JOIN wod_movements {alias}_l ON {alias}_l.wod_id = {alias}_w.id"
            f" AND {alias}_l.order_index = v.{alias}_ord\n"
            f"JOIN wod_scaled_lines {alias} ON {alias}.wod_movement_id = {alias}_l.id",
            [f"{alias}_wod", f"{alias}_ord"], catalog.scaled_key.get)


MOVEMENT_OWNER = "SELECT id FROM movements WHERE code IN ({codes})"
WOD_OWNER = "SELECT id FROM wods WHERE code IN ({codes})"
LINE_OWNER = ("SELECT l.id FROM wod_movements l JOIN wods w ON w.id = l.wod_id"
              " WHERE w.code IN ({codes})")


# --- Files -----------------------------------------------------------------------------------


def header(name: str, what: str) -> str:
    return (f"-- {name}: {what}.\n"
            "-- Generated by aldebaran-training/tools/catalog/generate_catalog.py: never edit by hand.\n"
            "-- Repeatable migration (upsert by stable code, never deletes a catalog entry).\n\n"
            f"SET search_path TO {SCHEMA};\n\n")


def muscles_file(c: Catalog) -> str:
    db = c.db
    rows = sorted(c.muscles, key=lambda m: (m["region"] or "", m["code"]))
    codes = {m["code"] for m in rows}
    parents = [[literal(m["code"], "varchar"), literal(c.muscle_code.get(m["parent_id"]), "varchar")]
               for m in rows]
    sql = header("R__10_Muscles", "the muscles, their functional groups and joint actions")
    sql += upsert_entities(db, "muscles", rows) + "\n"
    sql += (f"UPDATE muscles m SET parent_id = p.id\nFROM (VALUES\n{values(parents)}\n) AS v(code, parent)\n"
            "LEFT JOIN muscles p ON p.code = v.parent\nWHERE m.code = v.code;\n\n")
    sql += replace_children(db, "muscle_joint_actions", c.children("muscle_joint_actions", "muscle_id", c.muscle_code),
                            {"muscle_id": ref_entity("muscles", "mu", "muscle", c.muscle_code)},
                            "muscle_id", "SELECT id FROM muscles WHERE code IN ({codes})", codes)
    return sql


def movement_rows(c: Catalog):
    return sorted(c.movements, key=lambda m: (m["category"] or "", m["name"]))


def movements_file(c: Catalog) -> str:
    db = c.db
    rows = movement_rows(c)
    codes = {m["code"] for m in rows}
    own = ref_entity("movements", "mv", "movement", c.movement_code)
    muscle = ref_entity("muscles", "mu", "muscle", c.muscle_code)
    sql = header("R__11_Movements", "the movements, their muscle roles, adjustments, joint actions,"
                 " equipment and techniques")
    sql += upsert_entities(db, "movements", rows) + "\n"
    for table, refs in (
            ("movement_muscles", {"movement_id": own, "muscle_id": muscle}),
            ("movement_role_adjustments", {"movement_id": own, "muscle_id": muscle}),
            ("movement_joint_actions", {"movement_id": own}),
            ("movement_equipment", {"movement_id": own}),
            ("movement_techniques", {"movement_id": own}),
            ("movement_required_rules", {"movement_id": own}),
            ("movement_dumbbell_bases", {"movement_id": own})):
        sql += replace_children(db, table, c.children(table, "movement_id", c.movement_code), refs,
                                "movement_id", MOVEMENT_OWNER, codes) + "\n"
    return sql


def chains_file(c: Catalog) -> str:
    db = c.db
    codes = {m["code"] for m in c.movements}
    sql = header("R__12_Movement_Chains", "the chained movements and the movements done either way")
    sql += replace_children(
        db, "movement_components", c.children("movement_components", "chain_id", c.movement_code),
        {"chain_id": ref_entity("movements", "ch", "chain", c.movement_code),
         "component_id": ref_entity("movements", "co", "component", c.movement_code)},
        "chain_id", MOVEMENT_OWNER, codes) + "\n"
    sql += replace_children(
        db, "movement_accepted", c.children("movement_accepted", "choice_id", c.movement_code),
        {"choice_id": ref_entity("movements", "ch", "choice", c.movement_code),
         "accepted_id": ref_entity("movements", "ac", "accepted", c.movement_code)},
        "choice_id", MOVEMENT_OWNER, codes)
    return sql


def usual_names_file(c: Catalog) -> str:
    db = c.db
    codes = {m["code"] for m in c.movements}
    rows = sorted(c.children("movement_usual_names", "movement_id", c.movement_code),
                  key=lambda u: u["name_en"])
    sql = header("R__13_Usual_Names", "the usual names of the movements")
    sql += replace_children(db, "movement_usual_names", rows,
                            {"movement_id": ref_entity("movements", "mv", "movement", c.movement_code)},
                            "movement_id", MOVEMENT_OWNER, codes)
    return sql


def season_of(wod: dict) -> int | None:
    title = wod["title"]
    full = re.search(r"\b(20\d{2})\b", title)
    if full:
        return int(full.group(1))
    short = re.search(r"\b(\d{2})\.\d", title)
    return 2000 + int(short.group(1)) if short else None


def wod_file_of(wod: dict) -> str:
    category = wod.get("category")
    if category in REFERENCE_FILES:
        return REFERENCE_FILES[category]
    season = season_of(wod) if category in COMPETITIONS else None
    if season is None:
        return OTHER_FILE
    return f"R__{30 + season - FIRST_SEASON}_Season_{season}"


def natural(text: str):
    return [int(p) if p.isdigit() else p.lower() for p in re.split(r"(\d+)", text)]


def wod_sort_key(wod: dict):
    category = wod.get("category") or ""
    rank = COMPETITIONS.index(category) if category in COMPETITIONS else -1
    return (rank, natural(wod["title"]))


def wods_file(c: Catalog, name: str, wods: list[dict]) -> str:
    db = c.db
    wods = sorted(wods, key=wod_sort_key)
    codes = {w["code"] for w in wods}
    ids = {w["id"] for w in wods}
    lines = sorted((l for l in c.lines if l["wod_id"] in ids),
                   key=lambda l: (c.wod_code[l["wod_id"]], l["order_index"]))
    line_ids = {l["id"] for l in lines}
    scaled = [s for s in c.scaled if s["wod_movement_id"] in line_ids]
    scaled_ids = {s["id"] for s in scaled}

    sql = header(name, "catalog WODs")
    # Catalog WODs belong to the platform: no author, gym or source
    sql += upsert_entities(db, "wods", wods,
                           {"author_id": None, "gym_id": None, "source_wod_id": None}) + "\n"
    sql += replace_children(db, "wod_modalities", [r for r in db.rows("wod_modalities") if r["wod_id"] in ids],
                            {"wod_id": ref_entity("wods", "w", "wod", c.wod_code)},
                            "wod_id", WOD_OWNER, codes) + "\n"

    # WOD lines: updated in place by (WOD code, order), never deleted (scores reference them)
    types = db.columns("wod_movements")
    plain = [col for col in types if col not in SKIPPED and col not in ("wod_id", "movement_id")]
    body = [[literal(c.wod_code[l["wod_id"]], "varchar"), literal(c.movement_code[l["movement_id"]], "varchar")]
            + [literal(l.get(col), types[col]) for col in plain] for l in lines]
    if body:
        updates = ["movement_id = EXCLUDED.movement_id"] + [
            f"{col} = EXCLUDED.{col}" for col in plain if col != "order_index"]
        sql += (f"INSERT INTO wod_movements (wod_id, movement_id, {', '.join(plain)})\n"
                f"SELECT w.id, mv.id, {', '.join('v.' + col for col in plain)}\n"
                f"FROM (VALUES\n{values(body)}\n) AS v(wod, movement, {', '.join(plain)})\n"
                "JOIN wods w ON w.code = v.wod\nJOIN movements mv ON mv.code = v.movement\n"
                "ON CONFLICT (wod_id, order_index) DO UPDATE SET\n  " + ",\n  ".join(updates) + ";\n\n")

    line = ref_line(c, "l")
    for table in ("wod_movement_equipment", "wod_movement_techniques", "wod_movement_component_techniques"):
        rows = [r for r in db.rows(table) if r["wod_movement_id"] in line_ids]
        sql += replace_children(db, table, rows, {"wod_movement_id": line}, "wod_movement_id",
                                LINE_OWNER, codes) + "\n"
    sql += replace_children(db, "wod_scaled_lines", scaled,
                            {"wod_movement_id": line,
                             "movement_id": ref_entity("movements", "mv", "movement", c.movement_code)},
                            "wod_movement_id", LINE_OWNER, codes) + "\n"
    for table in ("wod_scaled_line_equipment", "wod_scaled_line_techniques"):
        rows = [r for r in db.rows(table) if r["wod_scaled_line_id"] in scaled_ids]
        # The scaled lines were just deleted (cascade) and inserted again: nothing to delete
        sql += replace_children(db, table, rows, {"wod_scaled_line_id": ref_scaled(c, "s")},
                                "wod_scaled_line_id", None, codes) + "\n"
    return sql


# --- Main ------------------------------------------------------------------------------------


def manifest_of(c: Catalog, files: dict[str, list[dict]]) -> dict:
    return {
        "muscles": sorted(m["code"] for m in c.muscles),
        "movements": sorted(m["code"] for m in c.movements),
        "wods": {name: {w["code"]: sorted(l["order_index"] for l in c.lines if l["wod_id"] == w["id"])
                        for w in wods} for name, wods in files.items()},
    }


def report_removals(previous: dict, current: dict) -> list[str]:
    gone = []
    for kind in ("muscles", "movements"):
        gone += [f"{kind[:-1]} {code}" for code in sorted(set(previous.get(kind, [])) - set(current[kind]))]
    old_wods = {code: lines for wods in previous.get("wods", {}).values() for code, lines in wods.items()}
    new_wods = {code: lines for wods in current["wods"].values() for code, lines in wods.items()}
    for code, lines in sorted(old_wods.items()):
        if code not in new_wods:
            gone.append(f"WOD {code}")
        else:
            gone += [f"line {order} of WOD {code}" for order in sorted(set(lines) - set(new_wods[code]))]
    return gone


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--container", default="castor-db", help="PostgreSQL container")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="output folder")
    parser.add_argument("--allow-removals", action="store_true",
                        help="write the files even when entries or WOD lines disappeared (the"
                             " versioned migration that removes them is written)")
    args = parser.parse_args()

    catalog = Catalog(Database(args.container))
    by_file: dict[str, list[dict]] = defaultdict(list)
    for wod in catalog.wods:
        by_file[wod_file_of(wod)].append(wod)

    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)
    manifest_path = out / MANIFEST
    previous = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    current = manifest_of(catalog, by_file)

    files = {
        "R__10_Muscles": muscles_file(catalog),
        "R__11_Movements": movements_file(catalog),
        "R__12_Movement_Chains": chains_file(catalog),
        "R__13_Usual_Names": usual_names_file(catalog),
    }
    for name, wods in sorted(by_file.items()):
        files[name] = wods_file(catalog, name, wods)

    gone = report_removals(previous, current) if previous else []
    if gone:
        print("No longer in the catalog since the last run (the files never delete: a versioned"
              " migration must remove them from the other databases):")
        for item in gone:
            print(f"  - {item}")
        if not args.allow_removals:
            sys.exit("Nothing written. Write the removal migration, then run again with"
                     " --allow-removals.")

    for stale in out.glob("R__*.sql"):
        if stale.stem not in files:
            stale.unlink()
            print(f"removed {stale.name} (no entry left)")
    for name, sql in files.items():
        (out / f"{name}.sql").write_text(sql, encoding="utf-8")
    manifest_path.write_text(json.dumps(current, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"{len(catalog.muscles)} muscles, {len(catalog.movements)} movements, "
          f"{len(catalog.wods)} catalog WODs in {len(files)} files → {out}")


if __name__ == "__main__":
    main()
