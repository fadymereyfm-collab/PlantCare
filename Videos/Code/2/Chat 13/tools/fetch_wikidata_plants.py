#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fetch_wikidata_plants.py — Pulls plant data from Wikidata for future
catalog expansion (target: 5000+ rows).

WHY THIS SCRIPT EXISTS:
The in-tree `build_catalog.py` ships with ~1500 hand-curated plants —
enough for a launch but not the full 5000+ target the user wants.
Wikidata is the highest-quality free source for binomial + family +
multilingual common names. The PlantCare CI / dev sandbox doesn't
allow outbound traffic to query.wikidata.org, so this script is
designed to be run LOCALLY by a developer with normal internet.

PIPELINE:
  1. SPARQL query against Wikidata Query Service (WDQS) — fetches
     species (taxon rank "species") that have BOTH a German Wikipedia
     article AND a binomial name AND a family classification.
  2. For each species, resolve:
       - scientificName  (Wikidata P225 — taxon name)
       - family          (Wikidata P171 → P225, walk up to family rank)
       - de_name         (German Wikipedia label, fallback to scientific)
       - category        (heuristic: indoor/outdoor/herbal/cacti/edible)
  3. De-duplicate against the existing plants.csv (case-insensitive
     scientific name match).
  4. Write `tools/wikidata_export.csv` — same 8-column schema as
     `app/src/main/assets/plants.csv`. The user reviews/edits, then
     either appends rows manually to plants.csv or merges via
     build_catalog.py's CURATED_NEW pipeline.

USAGE (locally, with internet):
    pip install requests
    python3 tools/fetch_wikidata_plants.py --limit 3000

OUTPUT:
    tools/wikidata_export.csv

CAVEATS:
- WDQS imposes a 60-second timeout per query. We chunk the request
  into family-by-family blocks so a single query never times out.
- Some Wikidata entries don't carry P171 (family) directly — we walk
  the parent-taxon chain up to 3 hops. Plants past 3 hops fall back
  to "" and the importer skips them.
- Names that look like cultivars ("Rosa 'Schneewittchen'") are
  filtered out — the catalog already covers cultivars manually.
"""

import argparse
import csv
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS_CSV = ROOT / "app" / "src" / "main" / "assets" / "plants.csv"
OUTPUT_CSV = ROOT / "tools" / "wikidata_export.csv"

WDQS_URL = "https://query.wikidata.org/sparql"
USER_AGENT = "PlantCareAndroid/1.0 (https://github.com/PlantCare; contact via GitHub issues) Python-urllib"

# ----------------------------------------------------------------------
# SPARQL — pulls plant species with German labels by family
# ----------------------------------------------------------------------
SPARQL_BY_FAMILY = """
SELECT DISTINCT ?taxon ?taxonName ?taxonLabelDe ?familyName WHERE {
  # Species level only, must have a binomial name.
  ?taxon wdt:P31 wd:Q16521 ;
         wdt:P105 wd:Q7432 ;
         wdt:P225 ?taxonName .
  # Walk up to FAMILY name. P171+ traverses parent taxa transitively.
  ?taxon wdt:P171+ ?family .
  ?family wdt:P105 wd:Q35409 ;
          wdt:P225 ?familyName .
  FILTER(?familyName = "%s")
  # Must have a German Wikipedia article — proxy for "common in DACH market".
  ?article schema:about ?taxon ;
           schema:isPartOf <https://de.wikipedia.org/> .
  OPTIONAL {
    ?taxon rdfs:label ?taxonLabelDe .
    FILTER(LANG(?taxonLabelDe) = "de")
  }
}
LIMIT %d
"""

# Families to pull (matches PlantCareDefaults coverage so any new species
# already has family-aware care defaults waiting for it).
TARGET_FAMILIES = [
    "Rosaceae", "Asteraceae", "Lamiaceae", "Brassicaceae", "Fabaceae",
    "Solanaceae", "Apiaceae", "Cucurbitaceae", "Poaceae", "Liliaceae",
    "Amaryllidaceae", "Iridaceae", "Orchidaceae", "Cactaceae",
    "Crassulaceae", "Asphodelaceae", "Asparagaceae", "Araceae", "Moraceae",
    "Marantaceae", "Bromeliaceae", "Arecaceae", "Polypodiaceae",
    "Nephrolepidaceae", "Dryopteridaceae", "Begoniaceae", "Gesneriaceae",
    "Piperaceae", "Urticaceae", "Saxifragaceae", "Geraniaceae", "Malvaceae",
    "Theaceae", "Vitaceae", "Passifloraceae", "Tropaeolaceae", "Violaceae",
    "Primulaceae", "Caryophyllaceae", "Euphorbiaceae", "Sapindaceae",
    "Fagaceae", "Betulaceae", "Salicaceae", "Pinaceae", "Cupressaceae",
    "Taxaceae", "Ericaceae", "Hydrangeaceae", "Ranunculaceae", "Boraginaceae",
    "Apocynaceae", "Plantaginaceae", "Onagraceae", "Papaveraceae",
    "Polygonaceae", "Amaranthaceae", "Caprifoliaceae", "Adoxaceae",
    "Oleaceae", "Magnoliaceae", "Lauraceae", "Rutaceae", "Anacardiaceae",
    "Juglandaceae", "Aizoaceae", "Acanthaceae", "Verbenaceae", "Zingiberaceae",
    "Convolvulaceae", "Hypericaceae", "Grossulariaceae", "Berberidaceae",
    "Buxaceae", "Aquifoliaceae", "Araliaceae", "Bignoniaceae", "Strelitziaceae",
    "Musaceae", "Heliconiaceae", "Nymphaeaceae", "Paeoniaceae", "Cycadaceae",
    "Gentianaceae", "Actinidiaceae", "Annonaceae", "Schisandraceae",
]


def cultivar_or_hybrid(name: str) -> bool:
    """Filter out cultivars and named hybrids — the curated catalog
    already lists the popular ones, and Wikidata has thousands of
    one-off cultivars we don't want to import en masse."""
    return ("'" in name) or (" × " in name) or name.endswith(" hybrida")


def fetch_family(family: str, limit: int) -> list[dict]:
    """Run one SPARQL query for a single family. Returns parsed JSON
    bindings. Errors are swallowed and printed — the caller decides
    whether to retry."""
    query = SPARQL_BY_FAMILY % (family, limit)
    encoded = urllib.parse.urlencode({"query": query, "format": "json"})
    req = urllib.request.Request(
        WDQS_URL + "?" + encoded,
        headers={"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=70) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        return data.get("results", {}).get("bindings", [])
    except Exception as e:
        print(f"  ERR  {family}: {e}", file=sys.stderr)
        return []


def load_existing_scientific_names() -> set[str]:
    """Lowercased binomials already in plants.csv — used for de-dup."""
    seen: set[str] = set()
    if not ASSETS_CSV.exists():
        return seen
    with ASSETS_CSV.open(encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, [])
        try:
            sci_idx = [h.lower() for h in header].index("scientificname")
        except ValueError:
            return seen
        for row in reader:
            if len(row) > sci_idx:
                v = row[sci_idx].strip().lower()
                if v:
                    seen.add(v)
    return seen


def classify_category(family: str, name: str) -> str:
    """Reuses build_catalog.py heuristics — keep these in sync."""
    fam = (family or "").lower()
    if fam in {"cactaceae", "aizoaceae"} or "cact" in name.lower():
        return "cacti"
    if fam in {"crassulaceae"}:
        return "cacti"
    if fam in {"lamiaceae", "apiaceae"}:
        return "herbal"
    if fam in {"pinaceae", "cupressaceae", "taxaceae", "fagaceae",
               "betulaceae", "salicaceae", "sapindaceae", "platanaceae"}:
        return "outdoor"
    return "indoor"


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--limit", type=int, default=200,
                   help="Max species to pull per family.")
    p.add_argument("--families", type=str, default="",
                   help="Comma-separated subset of families to fetch (debug).")
    args = p.parse_args()

    families = [f.strip() for f in args.families.split(",") if f.strip()] \
        if args.families else TARGET_FAMILIES
    seen = load_existing_scientific_names()
    print(f"Existing catalog: {len(seen)} unique scientific names")
    print(f"Querying {len(families)} families, up to {args.limit} per family...")

    rows: list[dict] = []
    for i, fam in enumerate(families, 1):
        print(f"[{i}/{len(families)}] {fam} ...", flush=True)
        bindings = fetch_family(fam, args.limit)
        for b in bindings:
            sci = b.get("taxonName", {}).get("value", "").strip()
            if not sci or sci.lower() in seen:
                continue
            if cultivar_or_hybrid(sci):
                continue
            de_label = b.get("taxonLabelDe", {}).get("value", "").strip()
            # If no German label, fall back to the scientific name —
            # localisation can be filled in later by hand.
            de_name = de_label or sci
            cat = classify_category(fam, de_name)
            rows.append({
                "name": de_name,
                "scientificName": sci,
                "family": fam,
                "category": cat,
                "lighting": "",
                "soil": "",
                "fertilizing": "",
                "watering": "",
            })
            seen.add(sci.lower())
        # WDQS fair-use: 1 req/s is generous enough.
        time.sleep(1.0)

    OUTPUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["name", "scientificName", "family", "category",
                    "lighting", "soil", "fertilizing", "watering"])
        for r in rows:
            w.writerow([r["name"], r["scientificName"], r["family"], r["category"],
                        r["lighting"], r["soil"], r["fertilizing"], r["watering"]])

    print(f"\nOK  wrote {len(rows)} new species → {OUTPUT_CSV.relative_to(ROOT)}")
    print(f"    review the file, then either append manually to plants.csv")
    print(f"    or extend build_catalog.py CURATED_NEW with the rows you keep.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
