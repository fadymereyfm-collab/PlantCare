package com.example.plantcare.data.plantnet

/**
 * Erzeugt sinnvolle Pflege‑Vorgaben (Licht, Boden, Düngung, Bewässerung) aus der
 * botanischen Familie, die PlantNet mitliefert.
 *
 * Motivation:
 * Der lokale Katalog (plants.csv, ~506 Einträge) deckt häufige Zimmerpflanzen ab.
 * Wildpflanzen wie *Polygonatum multiflorum* (Vielblütiges Salomonssiegel, Asparagaceae)
 * stehen dort aber nicht. Vor dieser Datei blieben in dem Fall alle vier Pflege‑Felder
 * leer („—") — genau das zeigt der Bug‑Screenshot.
 *
 * Hier liegen family‑basierte Default‑Texte. Das ist bewusst keine exakte Aussage über
 * die einzelne Art, sondern ein verständlicher Startwert, den der Nutzer jederzeit
 * im Detail‑Dialog überschreiben kann. Besser ein sinnvoller Richtwert aus der
 * botanischen Familie als gar nichts.
 *
 * Abdeckung: die in der Praxis häufigsten ~35 Familien, die PlantNet meldet. Für
 * unbekannte Familien gibt es [GENERIC_FALLBACK] — neutral gehaltene Texte, die
 * den Nutzer zur Feinjustierung einladen statt ihn zu blockieren.
 */
object PlantCareDefaults {

    data class CareTexts(
        val lighting: String,
        val soil: String,
        val fertilizing: String,
        val watering: String,
        /**
         * Empfohlener Gieß‑Rhythmus in Tagen. Wird verwendet, wenn der watering‑Text
         * keinen Zahlenwert enthält (z. B. „Sparsam gießen, Erde durchtrocknen lassen.")
         * — sonst würde [com.example.plantcare.ReminderUtils.parseWateringInterval]
         * 0 zurückgeben und der ganze Bewässerungsplan auf den Hardcoded‑Fallback
         * von 5 Tagen zusammenfallen (Functional Report §1.4).
         */
        val wateringIntervalDays: Int
    )

    /**
     * Liefert Defaults für eine Familie (z. B. "Asparagaceae"). Gibt immer etwas zurück
     * — im schlechtesten Fall [GENERIC_FALLBACK], damit die UI nie mit vier leeren
     * Feldern dasteht.
     *
     * Die Familienbezeichnung wird case‑insensitive gematcht.
     */
    fun forFamily(family: String?): CareTexts {
        if (family.isNullOrBlank()) return GENERIC_FALLBACK
        return BY_FAMILY[family.trim().lowercase()] ?: GENERIC_FALLBACK
    }

    /** Wird benutzt, wenn die Familie unbekannt ist oder nicht in der Tabelle steht. */
    val GENERIC_FALLBACK = CareTexts(
        lighting    = "Heller Standort ohne direkte Mittagssonne empfohlen. Genaue Bedürfnisse bitte an den Standort anpassen.",
        soil        = "Lockere, humose Blumenerde mit guter Drainage als Ausgangsbasis.",
        fertilizing = "Während der Wachstumsphase (Frühling–Sommer) alle 2–4 Wochen mit Flüssigdünger, im Winter pausieren.",
        watering    = "Regelmäßig mäßig gießen, Erde zwischen den Wassergaben leicht antrocknen lassen. Staunässe vermeiden.",
        wateringIntervalDays = 7
    )

    // Keys sind lowercased — Lookup geht auch case‑insensitive.
    private val BY_FAMILY: Map<String, CareTexts> = mapOf(
        // ------------- Sukkulenten / Wüstenpflanzen -------------
        "cactaceae" to CareTexts(
            lighting    = "Vollsonniger Standort, möglichst viele Stunden direkte Sonne.",
            soil        = "Mineralische Kakteenerde mit hohem Sand‑/Bimsanteil. Unbedingt drainagefähig.",
            fertilizing = "Im Sommer alle 4–6 Wochen sehr sparsam mit Kakteendünger. Im Winter keine Düngung.",
            watering    = "Sparsam gießen. Erde zwischen den Wassergaben komplett durchtrocknen lassen. Im Winter fast trocken halten.",
            wateringIntervalDays = 21
        ),
        "crassulaceae" to CareTexts(
            lighting    = "Hell bis vollsonnig. Viel Licht fördert kräftiges, kompaktes Wachstum.",
            soil        = "Kakteen-/Sukkulentenerde mit grobem Mineralanteil.",
            fertilizing = "Im Frühling und Sommer alle 4 Wochen mit Kakteendünger halb dosiert.",
            watering    = "Nur gießen, wenn die Erde komplett trocken ist. Blätter speichern Wasser — lieber zu wenig als zu viel.",
            wateringIntervalDays = 14
        ),
        "asphodelaceae" to CareTexts(
            lighting    = "Hell bis vollsonnig. Verträgt auch direkte Sonne, besonders im Wachstum.",
            soil        = "Sandige, gut drainierende Kakteenerde.",
            fertilizing = "Im Sommer monatlich mit Kakteendünger, im Winter nicht düngen.",
            watering    = "Sparsam gießen. Erde vor der nächsten Gabe durchtrocknen lassen. Keine Staunässe.",
            wateringIntervalDays = 14
        ),
        "euphorbiaceae" to CareTexts(
            lighting    = "Hell bis vollsonnig, je nach Art. Viele Arten mögen direkte Sonne.",
            soil        = "Durchlässige, eher magere Erde mit Sandanteil.",
            fertilizing = "Während der Wachstumsphase alle 4–6 Wochen schwach düngen.",
            watering    = "Mäßig bis sparsam gießen. Zwischen den Gaben antrocknen lassen. Milchsaft ist giftig – Vorsicht beim Umgang.",
            wateringIntervalDays = 14
        ),

        // ------------- Klassische Zimmerpflanzen -------------
        "araceae" to CareTexts(
            lighting    = "Hell bis halbschattig, keine direkte Mittagssonne. Verträgt auch schattigere Ecken.",
            soil        = "Humose, lockere Erde mit Drainageschicht.",
            fertilizing = "Von Frühling bis Herbst alle 2–3 Wochen mit Flüssigdünger für Grünpflanzen.",
            watering    = "Erde gleichmäßig leicht feucht halten. Vor der nächsten Gabe oberste Schicht antrocknen lassen.",
            wateringIntervalDays = 7
        ),
        "asparagaceae" to CareTexts(
            lighting    = "Halbschatten bis heller Standort ohne pralle Sonne.",
            soil        = "Lockere, humose Garten-/Waldbodenmischung mit Drainage.",
            fertilizing = "Während des Austriebs und der Blütezeit alle 4 Wochen schwach düngen.",
            watering    = "Mäßig und regelmäßig gießen. Erde gleichmäßig leicht feucht halten, aber nicht nass.",
            wateringIntervalDays = 10
        ),
        "moraceae" to CareTexts(
            lighting    = "Hell, gerne mit etwas Morgen- oder Abendsonne. Keine pralle Mittagssonne.",
            soil        = "Nahrhafte, humose Blumenerde mit Drainage.",
            fertilizing = "Von Frühling bis Sommer alle 2–3 Wochen mit Grünpflanzendünger.",
            watering    = "Gleichmäßig feucht halten, nie ganz austrocknen und keine Staunässe.",
            wateringIntervalDays = 7
        ),
        "arecaceae" to CareTexts(
            lighting    = "Hell, aber keine direkte Mittagssonne. Viele Arten vertragen auch Halbschatten.",
            soil        = "Palmenerde oder lockere Blumenerde mit Sandanteil.",
            fertilizing = "Von April bis September alle 2 Wochen mit Palmendünger.",
            watering    = "Regelmäßig gießen. Erdballen nicht austrocknen lassen, Wurzeln mögen keine stehende Nässe.",
            wateringIntervalDays = 7
        ),
        "marantaceae" to CareTexts(
            lighting    = "Halbschatten. Keine direkte Sonne — verbrennt die Blätter.",
            soil        = "Humose, lockere Erde mit hoher Feuchtigkeitsspeicherung.",
            fertilizing = "Im Sommerhalbjahr alle 2 Wochen mit halber Dosis Grünpflanzendünger.",
            watering    = "Gleichmäßig feucht halten. Kalkarmes Wasser bevorzugt. Hohe Luftfeuchtigkeit hilft sehr.",
            wateringIntervalDays = 5
        ),
        "bromeliaceae" to CareTexts(
            lighting    = "Hell, indirektes Licht. Direkte Mittagssonne vermeiden.",
            soil        = "Spezielle Bromelienerde oder Orchideensubstrat mit hohem Luftanteil.",
            fertilizing = "Schwach: alle 4 Wochen verdünnter Flüssigdünger, gern ins Blattinnere gespritzt.",
            watering    = "Trichter in der Blattrosette mit kalkarmem Wasser füllen. Substrat nur leicht feucht halten.",
            wateringIntervalDays = 7
        ),
        "orchidaceae" to CareTexts(
            lighting    = "Hell, aber ohne direkte Mittagssonne — Ostfenster ideal.",
            soil        = "Spezielles Orchideensubstrat aus Rindenstücken, kein normaler Blumenerde.",
            fertilizing = "Alle 2–3 Wochen mit Orchideendünger (halbe Dosis). In der Ruhezeit pausieren.",
            watering    = "Tauchmethode: alle 7–14 Tage Topf kurz in Wasser tauchen, gut abtropfen lassen. Keine Staunässe.",
            wateringIntervalDays = 10
        ),

        // ------------- Heilpflanzen / Kräuter -------------
        "lamiaceae" to CareTexts(
            lighting    = "Sonnig bis vollsonnig — je mehr Licht, desto aromatischer.",
            soil        = "Durchlässige, eher magere Kräutererde.",
            fertilizing = "Sparsam düngen. Alle 4–6 Wochen leicht organisch reicht.",
            watering    = "Mäßig gießen, Erde zwischen den Gaben antrocknen lassen. Staunässe unbedingt vermeiden.",
            wateringIntervalDays = 5
        ),
        "apiaceae" to CareTexts(
            lighting    = "Sonnig bis halbschattig.",
            soil        = "Humose, tiefgründige Gartenerde.",
            fertilizing = "Beim Pflanzen Kompost, während der Saison alle 4 Wochen leicht düngen.",
            watering    = "Gleichmäßig feucht halten, besonders an heißen Tagen.",
            wateringIntervalDays = 4
        ),

        // ------------- Korb-/Ranunkel- / Rosengewächse -------------
        "asteraceae" to CareTexts(
            lighting    = "Sonnig, viele Arten brauchen volle Sonne für die Blüte.",
            soil        = "Durchlässige, nährstoffreiche Gartenerde.",
            fertilizing = "Während der Blütezeit alle 2–3 Wochen mit Blühpflanzendünger.",
            watering    = "Regelmäßig und ausreichend gießen, bei Hitze täglich. Nicht über die Blüten gießen.",
            wateringIntervalDays = 4
        ),
        "rosaceae" to CareTexts(
            lighting    = "Sonnig, mindestens 6 Stunden direkte Sonne pro Tag.",
            soil        = "Tiefgründige, nährstoffreiche Lehm‑Humus‑Erde.",
            fertilizing = "Im Frühjahr Kompost oder Hornspäne, während der Saison alle 3–4 Wochen nachdüngen.",
            watering    = "Durchdringend und bodennah gießen. Lieber selten und viel als oft und wenig.",
            wateringIntervalDays = 7
        ),
        "ranunculaceae" to CareTexts(
            lighting    = "Halbschatten bis Sonne, je nach Art.",
            soil        = "Humose, frische Erde mit Drainage.",
            fertilizing = "Im Frühjahr mit Kompost versorgen, während der Blüte alle 3–4 Wochen flüssig düngen.",
            watering    = "Gleichmäßig feucht halten, keine Staunässe.",
            wateringIntervalDays = 5
        ),

        // ------------- Schmetterlings- / Doldenblütler / etc. -------------
        "fabaceae" to CareTexts(
            lighting    = "Sonnig. Volle Sonne fördert Blüte und Fruchtbildung.",
            soil        = "Durchlässige Gartenerde. Oft selbstversorgend mit Stickstoff.",
            fertilizing = "Eher wenig Stickstoff nötig; Kalium und Phosphor fördern die Blüte.",
            watering    = "Mäßig gießen, in Trockenphasen durchdringend wässern.",
            wateringIntervalDays = 7
        ),
        "poaceae" to CareTexts(
            lighting    = "Sonnig bis halbschattig.",
            soil        = "Durchlässige Erde, viele Arten sind anspruchslos.",
            fertilizing = "Alle 4–6 Wochen leicht düngen, bei Rasen stickstoffbetont.",
            watering    = "Regelmäßig gießen, besonders in Trockenphasen.",
            wateringIntervalDays = 5
        ),

        // ------------- Zwiebel- / Liliengewächse -------------
        "liliaceae" to CareTexts(
            lighting    = "Halbschatten bis Sonne, je nach Art.",
            soil        = "Humose, durchlässige Erde.",
            fertilizing = "Im Frühjahr nach dem Austrieb alle 3–4 Wochen schwach düngen, nach der Blüte pausieren.",
            watering    = "Während des Wachstums gleichmäßig feucht halten, in der Ruhezeit trocken.",
            wateringIntervalDays = 7
        ),
        "amaryllidaceae" to CareTexts(
            lighting    = "Hell, viel Licht für kräftige Blüte.",
            soil        = "Humose, durchlässige Erde.",
            fertilizing = "Während des Austriebs alle 2 Wochen mit Blühpflanzendünger.",
            watering    = "Beim Austrieb regelmäßig gießen. Nach der Blüte Wasser reduzieren, dann Ruhephase.",
            wateringIntervalDays = 7
        ),
        "iridaceae" to CareTexts(
            lighting    = "Sonnig.",
            soil        = "Durchlässige, humose Erde.",
            fertilizing = "Im Frühjahr Kompost, während der Wachstumsphase alle 4 Wochen leicht düngen.",
            watering    = "Mäßig gießen, Staunässe vermeiden — Zwiebeln/Rhizome sind fäulnisempfindlich.",
            wateringIntervalDays = 7
        ),

        // ------------- Farne & Moose -------------
        "polypodiaceae" to CareTexts(
            lighting    = "Schatten bis Halbschatten. Keine direkte Sonne.",
            soil        = "Humose, lockere Walderde mit hoher Feuchtigkeitsspeicherung.",
            fertilizing = "Sehr sparsam: alle 4–6 Wochen mit halber Dosis Flüssigdünger.",
            watering    = "Gleichmäßig feucht halten. Farne lieben hohe Luftfeuchtigkeit — gern regelmäßig übersprühen.",
            wateringIntervalDays = 4
        ),
        "dryopteridaceae" to CareTexts(
            lighting    = "Schatten bis Halbschatten.",
            soil        = "Humose, leicht saure Erde.",
            fertilizing = "Sparsam, alle 6–8 Wochen.",
            watering    = "Erde konstant feucht, aber nie durchnässt.",
            wateringIntervalDays = 4
        ),
        "nephrolepidaceae" to CareTexts(
            lighting    = "Halbschatten, indirektes Licht. Direkte Sonne verbrennt die Wedel.",
            soil        = "Humose, lockere Blumenerde.",
            fertilizing = "Von Frühling bis Herbst alle 2–3 Wochen mit halber Dosis düngen.",
            watering    = "Gleichmäßig feucht halten. Regelmäßig besprühen für hohe Luftfeuchte.",
            wateringIntervalDays = 4
        ),

        // ------------- Weitere häufige Familien -------------
        "solanaceae" to CareTexts(
            lighting    = "Sonnig, viel Licht.",
            soil        = "Nahrhafte, humose Erde mit Drainage.",
            fertilizing = "Während der Wachstums- und Fruchtphase alle 2 Wochen mit Tomaten-/Gemüsedünger.",
            watering    = "Regelmäßig und durchdringend gießen, nicht über die Blätter.",
            wateringIntervalDays = 3
        ),
        "brassicaceae" to CareTexts(
            lighting    = "Sonnig bis halbschattig.",
            soil        = "Nahrhaft, humos, leicht kalkhaltig.",
            fertilizing = "Starker Zehrer: im Frühjahr Kompost, während der Saison alle 3 Wochen nachdüngen.",
            watering    = "Gleichmäßig feucht halten, besonders bei Kopfbildung.",
            wateringIntervalDays = 4
        ),
        "cucurbitaceae" to CareTexts(
            lighting    = "Sonnig, warm.",
            soil        = "Humus- und nährstoffreich.",
            fertilizing = "Während der Wachstumsphase alle 1–2 Wochen kräftig düngen.",
            watering    = "Reichlich gießen, besonders bei Frucht­bildung. Bodenfeuchte konstant halten.",
            wateringIntervalDays = 3
        ),
        "caryophyllaceae" to CareTexts(
            lighting    = "Sonnig bis halbschattig.",
            soil        = "Durchlässige, kalkhaltige Gartenerde.",
            fertilizing = "Im Frühjahr kompostieren, während der Blüte alle 3 Wochen flüssig düngen.",
            watering    = "Mäßig gießen, Staunässe vermeiden.",
            wateringIntervalDays = 5
        ),
        "geraniaceae" to CareTexts(
            lighting    = "Sonnig. Viel Licht fördert üppige Blüte.",
            soil        = "Durchlässige, nährstoffreiche Blumenerde.",
            fertilizing = "Während der Blütezeit alle 1–2 Wochen mit Blühpflanzendünger.",
            watering    = "Mäßig gießen, Erde zwischen den Gaben antrocknen lassen.",
            wateringIntervalDays = 5
        ),
        "saxifragaceae" to CareTexts(
            lighting    = "Halbschatten, geschützter Standort.",
            soil        = "Humose, durchlässige Erde.",
            fertilizing = "Alle 4 Wochen schwach düngen.",
            watering    = "Gleichmäßig feucht halten, nicht austrocknen lassen.",
            wateringIntervalDays = 5
        ),
        "piperaceae" to CareTexts(
            lighting    = "Hell, ohne direkte Mittagssonne.",
            soil        = "Lockere, humose Blumenerde mit Drainage.",
            fertilizing = "Von Frühling bis Herbst alle 3–4 Wochen schwach düngen.",
            watering    = "Mäßig gießen, Erde zwischen den Gaben antrocknen lassen. Blätter speichern etwas Wasser.",
            wateringIntervalDays = 7
        ),
        "urticaceae" to CareTexts(
            lighting    = "Hell bis halbschattig.",
            soil        = "Humose, lockere Erde.",
            fertilizing = "Während der Wachstumsphase alle 2 Wochen mit Grünpflanzendünger.",
            watering    = "Gleichmäßig feucht halten, keine Staunässe.",
            wateringIntervalDays = 5
        ),
        "begoniaceae" to CareTexts(
            lighting    = "Hell, ohne direkte Sonne.",
            soil        = "Humose, lockere Blumenerde.",
            fertilizing = "Alle 2 Wochen mit halber Dosis Blühpflanzendünger.",
            watering    = "Erde gleichmäßig leicht feucht halten, Blätter nicht benetzen.",
            wateringIntervalDays = 5
        ),
        "gesneriaceae" to CareTexts(
            lighting    = "Hell, ohne direkte Sonne.",
            soil        = "Spezielle Usambaraveilchenerde oder lockere Humusmischung.",
            fertilizing = "Alle 2 Wochen mit Flüssigdünger für Blühpflanzen, halbe Dosis.",
            watering    = "Von unten gießen, Blätter nicht benetzen. Zimmerwarmes Wasser.",
            wateringIntervalDays = 5
        )
    )
}
