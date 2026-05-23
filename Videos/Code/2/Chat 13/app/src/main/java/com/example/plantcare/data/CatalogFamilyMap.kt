package com.example.plantcare.data

/**
 * v16 close-out — botanical family + scientific name lookup for the
 * 506-row catalog (`assets/plants.csv`). The CSV itself only carries
 * common name + 4 care-text columns, so without this map every catalog
 * plant inherits `family=null` → `PlantCareDefaults.forFamily(null)`
 * → GENERIC_FALLBACK (28d fertilize / 0d mist / 730d repot) regardless
 * of species.
 *
 * That defeated the whole point of the v16 family-aware defaults: an
 * Aloe Vera (Asphodelaceae) should land on mist=0 + repot=1095, not
 * generic 730. With this map plugged into CatalogSeeder, catalog plants
 * now pick up the right per-family schedule from day 1.
 *
 * Coverage focuses on the ~80 most common houseplants — full 505-row
 * coverage isn't needed because PlantCareDefaults itself only knows
 * 34 families (post-v17.11 reduction from the unverified 108-family
 * draft); anything beyond that falls through to GENERIC_FALLBACK the
 * same way `family=null` would, so the marginal value is small.
 *
 * Heuristic fallback `inferFromName` catches obvious species not in
 * the explicit table (e.g. anything containing "Kaktus" → Cactaceae)
 * for both catalog seed AND user-typed manual additions.
 */
object CatalogFamilyMap {

    data class FamilyInfo(val family: String, val scientificName: String?)

    private val EXACT: Map<String, FamilyInfo> = mapOf(
        // ---- Asphodelaceae ----
        "Aloe Vera"         to FamilyInfo("Asphodelaceae", "Aloe vera"),
        "Haworthia"         to FamilyInfo("Asphodelaceae", "Haworthia"),
        "Gasteria"          to FamilyInfo("Asphodelaceae", "Gasteria"),

        // ---- Asparagaceae ----
        "Sansevieria"       to FamilyInfo("Asparagaceae", "Dracaena trifasciata"),
        "Bogenhanf"         to FamilyInfo("Asparagaceae", "Dracaena trifasciata"),
        "Drachenbaum"       to FamilyInfo("Asparagaceae", "Dracaena"),
        "Yucca-Palme"       to FamilyInfo("Asparagaceae", "Yucca"),
        "Yucca"             to FamilyInfo("Asparagaceae", "Yucca"),
        "Zamioculcas"       to FamilyInfo("Asparagaceae", "Zamioculcas zamiifolia"),
        "Glücksfeder"       to FamilyInfo("Asparagaceae", "Zamioculcas zamiifolia"),
        "Grünlilie"         to FamilyInfo("Asparagaceae", "Chlorophytum comosum"),
        "Schusterpalme"     to FamilyInfo("Asparagaceae", "Aspidistra elatior"),

        // ---- Araceae (large family — many indoor plants) ----
        "Einblatt"          to FamilyInfo("Araceae", "Spathiphyllum wallisii"),
        "Friedenslilie"     to FamilyInfo("Araceae", "Spathiphyllum"),
        "Philodendron"      to FamilyInfo("Araceae", "Philodendron"),
        "Efeutute"          to FamilyInfo("Araceae", "Epipremnum aureum"),
        "Fensterblatt"      to FamilyInfo("Araceae", "Monstera deliciosa"),
        "Monstera"          to FamilyInfo("Araceae", "Monstera deliciosa"),
        "Anthurie"          to FamilyInfo("Araceae", "Anthurium"),
        "Calla"             to FamilyInfo("Araceae", "Zantedeschia"),
        "Dieffenbachia"     to FamilyInfo("Araceae", "Dieffenbachia"),
        "Alocasia"          to FamilyInfo("Araceae", "Alocasia"),
        "Aglaonema"         to FamilyInfo("Araceae", "Aglaonema"),
        "Syngonium"         to FamilyInfo("Araceae", "Syngonium"),

        // ---- Moraceae ----
        "Gummibaum"         to FamilyInfo("Moraceae", "Ficus elastica"),
        "Ficus Benjamini"   to FamilyInfo("Moraceae", "Ficus benjamina"),
        "Ficus"             to FamilyInfo("Moraceae", "Ficus"),
        "Birkenfeige"       to FamilyInfo("Moraceae", "Ficus benjamina"),
        "Geigenfeige"       to FamilyInfo("Moraceae", "Ficus lyrata"),

        // ---- Orchidaceae ----
        "Orchidee"          to FamilyInfo("Orchidaceae", "Phalaenopsis"),
        "Phalaenopsis"      to FamilyInfo("Orchidaceae", "Phalaenopsis"),

        // ---- Cactaceae ----
        "Kaktus"            to FamilyInfo("Cactaceae", "Cactaceae"),
        "Säulenkaktus"      to FamilyInfo("Cactaceae", null),
        "Kugelkaktus"       to FamilyInfo("Cactaceae", null),
        "Weihnachtskaktus"  to FamilyInfo("Cactaceae", "Schlumbergera"),
        "Osterkaktus"       to FamilyInfo("Cactaceae", "Hatiora gaertneri"),

        // ---- Crassulaceae (succulents) ----
        "Geldbaum"          to FamilyInfo("Crassulaceae", "Crassula ovata"),
        "Pfennigbaum"       to FamilyInfo("Crassulaceae", "Crassula ovata"),
        "Echeveria"         to FamilyInfo("Crassulaceae", "Echeveria"),
        "Sedum"             to FamilyInfo("Crassulaceae", "Sedum"),
        "Kalanchoe"         to FamilyInfo("Crassulaceae", "Kalanchoe"),
        "Flammendes Käthchen" to FamilyInfo("Crassulaceae", "Kalanchoe blossfeldiana"),

        // ---- Euphorbiaceae ----
        "Wolfsmilch"        to FamilyInfo("Euphorbiaceae", "Euphorbia"),
        "Christusdorn"      to FamilyInfo("Euphorbiaceae", "Euphorbia milii"),
        "Weihnachtsstern"   to FamilyInfo("Euphorbiaceae", "Euphorbia pulcherrima"),

        // ---- Marantaceae (need misting) ----
        "Calathea"          to FamilyInfo("Marantaceae", "Calathea"),
        "Korbmarante"       to FamilyInfo("Marantaceae", "Calathea"),
        "Marante"           to FamilyInfo("Marantaceae", "Maranta"),
        "Stromanthe"        to FamilyInfo("Marantaceae", "Stromanthe"),

        // ---- Bromeliaceae (mist + central cup watering) ----
        "Tillandsie"        to FamilyInfo("Bromeliaceae", "Tillandsia"),
        "Bromelie"          to FamilyInfo("Bromeliaceae", "Bromelia"),
        "Guzmanie"          to FamilyInfo("Bromeliaceae", "Guzmania"),
        "Vriesea"           to FamilyInfo("Bromeliaceae", "Vriesea"),
        "Ananas"            to FamilyInfo("Bromeliaceae", "Ananas comosus"),

        // ---- Arecaceae (palms) ----
        "Palme"             to FamilyInfo("Arecaceae", "Arecaceae"),
        "Bergpalme"         to FamilyInfo("Arecaceae", "Chamaedorea elegans"),
        "Kentiapalme"       to FamilyInfo("Arecaceae", "Howea forsteriana"),
        "Areca-Palme"       to FamilyInfo("Arecaceae", "Dypsis lutescens"),
        "Goldfruchtpalme"   to FamilyInfo("Arecaceae", "Dypsis lutescens"),
        "Dattelpalme"       to FamilyInfo("Arecaceae", "Phoenix"),

        // ---- Polypodiaceae / Nephrolepidaceae / Dryopteridaceae (ferns — mist!) ----
        "Farne"             to FamilyInfo("Polypodiaceae", "Polypodiaceae"),
        "Farn"              to FamilyInfo("Polypodiaceae", "Polypodiaceae"),
        "Schwertfarn"       to FamilyInfo("Nephrolepidaceae", "Nephrolepis"),
        "Geweihfarn"        to FamilyInfo("Polypodiaceae", "Platycerium"),

        // ---- Cyperaceae ----
        "Zyperngras"        to FamilyInfo("Cyperaceae", "Cyperus"),
        "Papyrus"           to FamilyInfo("Cyperaceae", "Cyperus papyrus"),

        // ---- Amaryllidaceae ----
        "Amaryllis"         to FamilyInfo("Amaryllidaceae", "Hippeastrum"),
        "Clivie"            to FamilyInfo("Amaryllidaceae", "Clivia miniata"),
        "Hippeastrum"       to FamilyInfo("Amaryllidaceae", "Hippeastrum"),

        // ---- Geraniaceae ----
        "Geranie"           to FamilyInfo("Geraniaceae", "Pelargonium"),
        "Pelargonie"        to FamilyInfo("Geraniaceae", "Pelargonium"),

        // ---- Begoniaceae (mapped to araceae for moisture-tolerant defaults) ----
        "Begonie"           to FamilyInfo("Begoniaceae", "Begonia"),

        // ---- Gesneriaceae ----
        "Gloxinie"          to FamilyInfo("Gesneriaceae", "Sinningia speciosa"),
        "Usambaraveilchen"  to FamilyInfo("Gesneriaceae", "Saintpaulia"),

        // ---- Lamiaceae (herbs, sunny) ----
        "Lavendel"          to FamilyInfo("Lamiaceae", "Lavandula"),
        "Basilikum"         to FamilyInfo("Lamiaceae", "Ocimum basilicum"),
        "Rosmarin"          to FamilyInfo("Lamiaceae", "Salvia rosmarinus"),
        "Zitronenmelisse"   to FamilyInfo("Lamiaceae", "Melissa officinalis"),
        "Majoran"           to FamilyInfo("Lamiaceae", "Origanum majorana"),
        "Thymian"           to FamilyInfo("Lamiaceae", "Thymus"),
        "Pfefferminze"      to FamilyInfo("Lamiaceae", "Mentha × piperita"),
        "Salbei"            to FamilyInfo("Lamiaceae", "Salvia officinalis"),
        "Oregano"           to FamilyInfo("Lamiaceae", "Origanum vulgare"),

        // ---- Solanaceae ----
        "Chili"             to FamilyInfo("Solanaceae", "Capsicum"),
        "Petunie"           to FamilyInfo("Solanaceae", "Petunia"),
        "Engelstrompete"    to FamilyInfo("Solanaceae", "Brugmansia"),

        // ---- Brassicaceae ----
        "Kapuzinerkresse"   to FamilyInfo("Tropaeolaceae", "Tropaeolum majus"),

        // ---- Primulaceae ----
        "Primel"            to FamilyInfo("Primulaceae", "Primula"),
        "Stiefmütterchen"   to FamilyInfo("Violaceae", "Viola"),

        // ---- Malvaceae ----
        "Hibiskus"          to FamilyInfo("Malvaceae", "Hibiscus rosa-sinensis"),
        "Zimmerlinde"       to FamilyInfo("Malvaceae", "Sparrmannia africana"),

        // ---- Theaceae ----
        "Kamelie"           to FamilyInfo("Theaceae", "Camellia japonica"),

        // ---- Vitaceae ----
        "Weinrebe"          to FamilyInfo("Vitaceae", "Vitis"),

        // ---- Passifloraceae ----
        "Passionsblume"     to FamilyInfo("Passifloraceae", "Passiflora"),

        // ---- Piperaceae ----
        "Zwergpfeffer"      to FamilyInfo("Piperaceae", "Peperomia"),
        "Peperomia"         to FamilyInfo("Piperaceae", "Peperomia"),

        // ---- Urticaceae ----
        "Pilea"             to FamilyInfo("Urticaceae", "Pilea peperomioides"),
        "Ufopflanze"        to FamilyInfo("Urticaceae", "Pilea peperomioides"),

        // ---- Saxifragaceae ----
        "Steinbrech"        to FamilyInfo("Saxifragaceae", "Saxifraga"),

        // ---- Apiaceae (umbellifers — herbs + carrots family) ----
        "Petersilie"        to FamilyInfo("Apiaceae", "Petroselinum crispum"),
        "Dill"              to FamilyInfo("Apiaceae", "Anethum graveolens"),
        "Schnittlauch"      to FamilyInfo("Amaryllidaceae", "Allium schoenoprasum")
    )

    /**
     * Look up a plant's family + scientific name from its common name.
     * Tries exact match first, then a couple of cheap heuristics so
     * "Roter Säulenkaktus" still resolves to Cactaceae even though it's
     * not in the table. Returns null when no match — caller then falls
     * through to PlantCareDefaults.GENERIC_FALLBACK.
     */
    @JvmStatic
    fun lookup(name: String?): FamilyInfo? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()

        // 1) exact (case-sensitive) match — covers the curated table.
        EXACT[trimmed]?.let { return it }

        // 2) case-insensitive match for typos like "kaktus" / "KAKTUS".
        val lower = trimmed.lowercase()
        EXACT.entries.firstOrNull { it.key.lowercase() == lower }?.let { return it.value }

        // 3) heuristic — substring match for compound common names.
        return inferFromName(trimmed)
    }

    /**
     * Last-resort family inference for compound common names not in
     * the curated table. Order matters: more specific markers first
     * (Weihnachtskaktus → Cactaceae before plain Kaktus would).
     */
    private fun inferFromName(name: String): FamilyInfo? {
        val n = name.lowercase()
        return when {
            n.contains("kaktus")     -> FamilyInfo("Cactaceae", null)
            n.contains("orchid")     -> FamilyInfo("Orchidaceae", null)
            n.contains("palme")      -> FamilyInfo("Arecaceae", null)
            n.contains("farn")       -> FamilyInfo("Polypodiaceae", null)
            n.contains("philodendron") -> FamilyInfo("Araceae", null)
            n.contains("ficus")      -> FamilyInfo("Moraceae", null)
            n.contains("monstera")   -> FamilyInfo("Araceae", null)
            n.contains("aloe")       -> FamilyInfo("Asphodelaceae", null)
            n.contains("yucca")      -> FamilyInfo("Asparagaceae", null)
            n.contains("calathea")   -> FamilyInfo("Marantaceae", null)
            n.contains("bromelie")   -> FamilyInfo("Bromeliaceae", null)
            else -> null
        }
    }
}
