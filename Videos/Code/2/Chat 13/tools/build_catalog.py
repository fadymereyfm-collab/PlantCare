#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_catalog.py — Rebuilds plants.csv with the v17 8-column schema.

OUTPUT: app/src/main/assets/plants.csv with 8 columns:
    name, scientificName, family, category, lighting, soil, fertilizing, watering

WHY THIS EXISTS:
- The legacy plants.csv had 5 columns (name + 4 care texts).
- v17 added scientificName + family + category as first-class columns
  so PlantNet → catalog matching can use the Latin name as a primary
  key instead of fragile German-name heuristics.

WHAT THIS SCRIPT DOES NOW (v17.10 — rollback after a bigger expansion
that lacked verification):
  1. Reads the existing plants.csv (whatever schema, 5 or 8 columns).
  2. Keeps ONLY the rows that have curated German care prose in at
     least one of (lighting, soil, fertilizing, watering). These are
     the trusted ~505 rows that were hand-curated and care-reviewed.
  3. Backfills scientificName + family from CatalogFamilyMap (Python
     mirror of the Kotlin lookup table) for rows that don't have them.
  4. Writes the result back as 8-column CSV.

This script intentionally does NOT add unverified species. The earlier
mass-expansion (4044 extra entries from training-data recall) was rolled
back because plant-care misinformation can kill plants — see PROGRESS.md
for the rationale. New species should be sourced through
`tools/fetch_wikidata_plants.py`, which goes through Wikidata and
preserves provenance.

USAGE:
    python3 tools/build_catalog.py
"""

import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS_CSV = ROOT / "app" / "src" / "main" / "assets" / "plants.csv"


# -----------------------------------------------------------------------------
# Curated family / scientific-name map for the trusted ~505 entries.
# Mirrors the Kotlin CatalogFamilyMap.EXACT table — Python copy so the
# script doesn't need a Kotlin runtime. Keys are German trivial names.
# -----------------------------------------------------------------------------
CATALOG_FAMILY_MAP = {
    # Asphodelaceae
    "Aloe Vera": ("Aloe vera", "Asphodelaceae"),
    "Haworthia": ("Haworthia", "Asphodelaceae"),
    "Gasteria": ("Gasteria", "Asphodelaceae"),
    # Asparagaceae
    "Sansevieria": ("Dracaena trifasciata", "Asparagaceae"),
    "Bogenhanf": ("Dracaena trifasciata", "Asparagaceae"),
    "Drachenbaum": ("Dracaena", "Asparagaceae"),
    "Yucca-Palme": ("Yucca", "Asparagaceae"),
    "Yucca": ("Yucca", "Asparagaceae"),
    "Zamioculcas": ("Zamioculcas zamiifolia", "Asparagaceae"),
    "Glücksfeder": ("Zamioculcas zamiifolia", "Asparagaceae"),
    "Grünlilie": ("Chlorophytum comosum", "Asparagaceae"),
    "Schusterpalme": ("Aspidistra elatior", "Asparagaceae"),
    # Araceae
    "Einblatt": ("Spathiphyllum wallisii", "Araceae"),
    "Friedenslilie": ("Spathiphyllum", "Araceae"),
    "Philodendron": ("Philodendron", "Araceae"),
    "Efeutute": ("Epipremnum aureum", "Araceae"),
    "Fensterblatt": ("Monstera deliciosa", "Araceae"),
    "Monstera": ("Monstera deliciosa", "Araceae"),
    "Anthurie": ("Anthurium", "Araceae"),
    "Calla": ("Zantedeschia", "Araceae"),
    "Dieffenbachia": ("Dieffenbachia", "Araceae"),
    "Alocasia": ("Alocasia", "Araceae"),
    "Aglaonema": ("Aglaonema", "Araceae"),
    "Syngonium": ("Syngonium", "Araceae"),
    # Moraceae
    "Gummibaum": ("Ficus elastica", "Moraceae"),
    "Ficus Benjamini": ("Ficus benjamina", "Moraceae"),
    "Ficus": ("Ficus", "Moraceae"),
    "Birkenfeige": ("Ficus benjamina", "Moraceae"),
    "Geigenfeige": ("Ficus lyrata", "Moraceae"),
    # Orchidaceae
    "Orchidee": ("Phalaenopsis", "Orchidaceae"),
    "Phalaenopsis": ("Phalaenopsis", "Orchidaceae"),
    # Cactaceae
    "Kaktus": ("Cactaceae", "Cactaceae"),
    "Säulenkaktus": (None, "Cactaceae"),
    "Kugelkaktus": (None, "Cactaceae"),
    "Weihnachtskaktus": ("Schlumbergera", "Cactaceae"),
    "Osterkaktus": ("Hatiora gaertneri", "Cactaceae"),
    # Crassulaceae
    "Geldbaum": ("Crassula ovata", "Crassulaceae"),
    "Pfennigbaum": ("Crassula ovata", "Crassulaceae"),
    "Echeveria": ("Echeveria", "Crassulaceae"),
    "Sedum": ("Sedum", "Crassulaceae"),
    "Kalanchoe": ("Kalanchoe", "Crassulaceae"),
    "Flammendes Käthchen": ("Kalanchoe blossfeldiana", "Crassulaceae"),
    # Euphorbiaceae
    "Wolfsmilch": ("Euphorbia", "Euphorbiaceae"),
    "Christusdorn": ("Euphorbia milii", "Euphorbiaceae"),
    "Weihnachtsstern": ("Euphorbia pulcherrima", "Euphorbiaceae"),
    # Marantaceae
    "Calathea": ("Calathea", "Marantaceae"),
    "Korbmarante": ("Calathea", "Marantaceae"),
    "Marante": ("Maranta", "Marantaceae"),
    "Stromanthe": ("Stromanthe", "Marantaceae"),
    # Bromeliaceae
    "Tillandsie": ("Tillandsia", "Bromeliaceae"),
    "Bromelie": ("Bromelia", "Bromeliaceae"),
    "Guzmanie": ("Guzmania", "Bromeliaceae"),
    "Vriesea": ("Vriesea", "Bromeliaceae"),
    "Ananas": ("Ananas comosus", "Bromeliaceae"),
    # Arecaceae
    "Palme": ("Arecaceae", "Arecaceae"),
    "Bergpalme": ("Chamaedorea elegans", "Arecaceae"),
    "Kentiapalme": ("Howea forsteriana", "Arecaceae"),
    "Areca-Palme": ("Dypsis lutescens", "Arecaceae"),
    "Goldfruchtpalme": ("Dypsis lutescens", "Arecaceae"),
    "Dattelpalme": ("Phoenix", "Arecaceae"),
    # Ferns
    "Farne": ("Polypodiaceae", "Polypodiaceae"),
    "Farn": ("Polypodiaceae", "Polypodiaceae"),
    "Schwertfarn": ("Nephrolepis", "Nephrolepidaceae"),
    "Geweihfarn": ("Platycerium", "Polypodiaceae"),
    # Cyperaceae
    "Zyperngras": ("Cyperus", "Cyperaceae"),
    "Papyrus": ("Cyperus papyrus", "Cyperaceae"),
    # Amaryllidaceae
    "Amaryllis": ("Hippeastrum", "Amaryllidaceae"),
    "Clivie": ("Clivia miniata", "Amaryllidaceae"),
    "Hippeastrum": ("Hippeastrum", "Amaryllidaceae"),
    # Geraniaceae
    "Geranie": ("Pelargonium", "Geraniaceae"),
    "Pelargonie": ("Pelargonium", "Geraniaceae"),
    # Begoniaceae
    "Begonie": ("Begonia", "Begoniaceae"),
    # Gesneriaceae
    "Gloxinie": ("Sinningia speciosa", "Gesneriaceae"),
    "Usambaraveilchen": ("Saintpaulia", "Gesneriaceae"),
    # Lamiaceae
    "Lavendel": ("Lavandula", "Lamiaceae"),
    "Basilikum": ("Ocimum basilicum", "Lamiaceae"),
    "Rosmarin": ("Salvia rosmarinus", "Lamiaceae"),
    "Zitronenmelisse": ("Melissa officinalis", "Lamiaceae"),
    "Majoran": ("Origanum majorana", "Lamiaceae"),
    "Thymian": ("Thymus", "Lamiaceae"),
    "Pfefferminze": ("Mentha × piperita", "Lamiaceae"),
    "Salbei": ("Salvia officinalis", "Lamiaceae"),
    "Oregano": ("Origanum vulgare", "Lamiaceae"),
    # Solanaceae
    "Chili": ("Capsicum", "Solanaceae"),
    "Petunie": ("Petunia", "Solanaceae"),
    "Engelstrompete": ("Brugmansia", "Solanaceae"),
    # Tropaeolaceae
    "Kapuzinerkresse": ("Tropaeolum majus", "Tropaeolaceae"),
    # Primulaceae / Violaceae
    "Primel": ("Primula", "Primulaceae"),
    "Stiefmütterchen": ("Viola", "Violaceae"),
    # Malvaceae
    "Hibiskus": ("Hibiscus rosa-sinensis", "Malvaceae"),
    "Zimmerlinde": ("Sparrmannia africana", "Malvaceae"),
    # Theaceae
    "Kamelie": ("Camellia japonica", "Theaceae"),
    # Vitaceae
    "Weinrebe": ("Vitis", "Vitaceae"),
    # Passifloraceae
    "Passionsblume": ("Passiflora", "Passifloraceae"),
    # Piperaceae
    "Zwergpfeffer": ("Peperomia", "Piperaceae"),
    "Peperomia": ("Peperomia", "Piperaceae"),
    # Urticaceae
    "Pilea": ("Pilea peperomioides", "Urticaceae"),
    "Ufopflanze": ("Pilea peperomioides", "Urticaceae"),
    # Saxifragaceae
    "Steinbrech": ("Saxifraga", "Saxifragaceae"),
    # Apiaceae
    "Petersilie": ("Petroselinum crispum", "Apiaceae"),
    "Dill": ("Anethum graveolens", "Apiaceae"),
    "Schnittlauch": ("Allium schoenoprasum", "Amaryllidaceae"),
}


def infer_family_heuristic(name: str):
    """Same heuristic table as CatalogFamilyMap.inferFromName in Kotlin.
    Last-resort fallback for compound names not in the explicit map."""
    n = name.lower()
    rules = [
        ("kaktus", (None, "Cactaceae")),
        ("orchid", (None, "Orchidaceae")),
        ("palme", (None, "Arecaceae")),
        ("farn", (None, "Polypodiaceae")),
        ("philodendron", (None, "Araceae")),
        ("ficus", (None, "Moraceae")),
        ("monstera", (None, "Araceae")),
        ("aloe", (None, "Asphodelaceae")),
        ("yucca", (None, "Asparagaceae")),
        ("calathea", (None, "Marantaceae")),
        ("bromelie", (None, "Bromeliaceae")),
    ]
    for needle, info in rules:
        if needle in n:
            return info
    return (None, None)


def classify_legacy(name: str, lighting: str, watering: str) -> str:
    """Mirror PlantCategoryUtil.classify in Python — used to fill the
    `category` column for legacy rows that don't have it yet."""
    n = (name or "").lower()
    # Succulents / cacti-form keywords. "haworth" stem catches both Latin
    # "haworthia" and German "Haworthie". Multi-word entries ("euphorbia
    # trigona") avoid catching non-succulent Euphorbia species like
    # Weihnachtsstern (Euphorbia pulcherrima — kept as "indoor").
    if any(k in n for k in ("kaktus", "succul", "haworth", "echeveria", "sedum",
                            "lithops", "aloe", "cactus", "agave", "fetthenne",
                            "sempervivum", "kalanchoe",
                            "crassula", "adenium", "stapelia", "pachypodium",
                            "euphorbia trigona", "euphorbia milii", "christusdorn",
                            "wüstenrose", "aasblume", "huernia")):
        return "cacti"
    herbal_kw = (
        "basilikum", "rosmarin", "thymian", "salbei", "minze", "petersilie",
        "schnittlauch", "dill", "oregano", "majoran", "estragon", "kümmel",
        "anis", "kerbel", "liebstöckel", "koriander", "lavendel", "kamille",
        "kresse", "ringelblume", "lemongras", "zitronengras", "ingwer",
        "wermut", "stevia", "mariendistel", "johanniskraut", "baldrian",
        "schafgarbe", "spitzwegerich", "brennnessel", "frauenmantel",
    )
    if any(k in n for k in herbal_kw):
        return "herbal"
    outdoor_kw = (
        "baum", "rose", "tulpe", "kohl", "tomate", "salat", "möhre", "karotte",
        "gurke", "zucchini", "kürbis", "zwiebel", "kartoffel", "weizen",
        "gerste", "hafer", "roggen", "mais", "reis", "weide", "linde",
        "buche", "eiche", "ahorn", "fichte", "tanne", "kiefer", "lärche",
        "birke", "pappel", "esche", "kastanie", "walnuss", "apfel", "birne",
        "kirsche", "pflaume", "pfirsich", "aprikose", "weinrebe", "feige",
        "olive", "garten", "wiese", "feld", "park",
    )
    if any(k in n for k in outdoor_kw):
        return "outdoor"
    return "indoor"


def parse_csv(path: Path) -> list[dict]:
    """Read whatever schema plants.csv has — 5 cols (legacy) or 8 cols (v17)."""
    rows: list[dict] = []
    with path.open(encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, [])
        is_v17 = "scientificname" in [h.lower() for h in header]
        for parts in reader:
            if not parts or all(c.strip() == "" for c in parts):
                continue
            if is_v17:
                row = {
                    "name": parts[0].strip() if len(parts) > 0 else "",
                    "scientificName": parts[1].strip() if len(parts) > 1 else "",
                    "family": parts[2].strip() if len(parts) > 2 else "",
                    "category": parts[3].strip() if len(parts) > 3 else "",
                    "lighting": parts[4].strip() if len(parts) > 4 else "",
                    "soil": parts[5].strip() if len(parts) > 5 else "",
                    "fertilizing": parts[6].strip() if len(parts) > 6 else "",
                    "watering": parts[7].strip() if len(parts) > 7 else "",
                }
            else:
                row = {
                    "name": parts[0].strip() if len(parts) > 0 else "",
                    "scientificName": "",
                    "family": "",
                    "category": "",
                    "lighting": parts[1].strip() if len(parts) > 1 else "",
                    "soil": parts[2].strip() if len(parts) > 2 else "",
                    "fertilizing": parts[3].strip() if len(parts) > 3 else "",
                    "watering": parts[4].strip() if len(parts) > 4 else "",
                }
            if row["name"]:
                rows.append(row)
    return rows


def build_rows() -> list[dict]:
    """Trusted-only build: keep rows that have curated care prose,
    backfill scientificName + family + category."""
    if not ASSETS_CSV.exists():
        print("ERROR: plants.csv not found", file=sys.stderr)
        return []

    raw = parse_csv(ASSETS_CSV)
    out: list[dict] = []
    for r in raw:
        # Trust filter: at least one care-text column must be non-empty.
        # This is the gate that excludes the rolled-back unverified species.
        has_care = any([r["lighting"], r["soil"], r["fertilizing"], r["watering"]])
        if not has_care:
            continue
        sci = r["scientificName"]
        fam = r["family"]
        if not sci or not fam:
            info = CATALOG_FAMILY_MAP.get(r["name"])
            if info is not None:
                if not sci:
                    sci = info[0] or ""
                if not fam:
                    fam = info[1] or ""
            else:
                inferred = infer_family_heuristic(r["name"])
                if inferred[1] is not None:
                    if not sci:
                        sci = inferred[0] or ""
                    if not fam:
                        fam = inferred[1] or ""
        # Cacti heuristic is authoritative — the keyword list is narrow and
        # unambiguously succulent-only, so it overrides any pre-existing
        # category. Fixes the 6 succulents (Crassula ovata, Euphorbia trigona/
        # milii, Zebra-Haworthie, Adenium, Stapelia) that were stuck on
        # "indoor" because earlier pipeline runs predated the keyword fix.
        # For non-cacti, preserve the existing category if set.
        heuristic_cat = classify_legacy(r["name"], r["lighting"], r["watering"])
        cat = "cacti" if heuristic_cat == "cacti" else (r["category"] or heuristic_cat)
        out.append({
            "name": r["name"],
            "scientificName": sci,
            "family": fam,
            "category": cat,
            "lighting": r["lighting"],
            "soil": r["soil"],
            "fertilizing": r["fertilizing"],
            "watering": r["watering"],
        })
    return out


def write_csv(rows: list[dict]) -> None:
    ASSETS_CSV.parent.mkdir(parents=True, exist_ok=True)
    with ASSETS_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["name", "scientificName", "family", "category",
                    "lighting", "soil", "fertilizing", "watering"])
        for r in rows:
            w.writerow([
                r["name"], r["scientificName"], r["family"], r["category"],
                r["lighting"], r["soil"], r["fertilizing"], r["watering"],
            ])


def main() -> int:
    rows = build_rows()
    write_csv(rows)
    n_with_sci = sum(1 for r in rows if r["scientificName"])
    n_with_fam = sum(1 for r in rows if r["family"])
    print(f"OK  plants.csv written: {len(rows)} TRUSTED rows")
    print(f"    scientificName populated: {n_with_sci}/{len(rows)} ({100*n_with_sci/len(rows):.1f}%)")
    print(f"    family populated:         {n_with_fam}/{len(rows)} ({100*n_with_fam/len(rows):.1f}%)")
    print(f"    care-text written for ALL rows (trust gate)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
