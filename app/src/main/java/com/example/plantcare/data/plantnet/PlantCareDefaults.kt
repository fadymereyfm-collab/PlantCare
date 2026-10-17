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
 * Abdeckung: 34 verifizierte Familien (Stand v17.11 — die Liste wurde von zuvor 108
 * auf 34 reduziert, weil die zusätzlichen 74 aus Trainingsdaten-Recall stammten und
 * nicht aus einer verifizierten taxonomischen Quelle; siehe PROGRESS.md v17.11).
 * Für unbekannte Familien gibt es [GENERIC_FALLBACK] — neutral gehaltene Texte, die
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
        val wateringIntervalDays: Int,
        /**
         * Düng‑Rhythmus in Tagen. v16 schema: triggert die zweite
         * Reminder-Spur (Type "fertilize"). Default 28 (monatlich) deckt
         * 80% der Zimmerpflanzen — Sukkulenten/Kakteen sind seltener,
         * Gemüse/Tomaten häufiger.
         */
        val fertilizingIntervalDays: Int = 28,
        /**
         * Sprüh‑Rhythmus in Tagen. v16 schema: nur für Pflanzen mit echtem
         * Bedarf an hoher Luft­feuchte (Farne, Marantaceae, Bromelien).
         * 0 = deaktiviert (kein Sprüh-Reminder erzeugt).
         */
        val mistingIntervalDays: Int = 0,
        /**
         * Umtopf‑Rhythmus in Tagen. v16 schema: Standard 730 (alle 2 Jahre)
         * passt für die meisten Zimmerpflanzen. Schnellwachsende (Tomaten,
         * Kürbisse) brauchen kürzere Zyklen, Kakteen längere.
         */
        val repottingIntervalDays: Int = 730,
        /**
         * Curated misting instructions per family. Optional — null means the
         * caller (`ReminderTaskHighlight`) falls back to
         * `R.string.reminder_task_mist_generic`. Only set for families that
         * actually need misting (high-humidity tropicals: Marantaceae,
         * Bromeliaceae, ferns) — for the rest, mistingIntervalDays=0 means
         * no reminder fires anyway.
         *
         * Per-family granularity, NOT per-plant — plants.csv only carries
         * watering + fertilizing text. Adding per-plant misting/repotting
         * would require extending the CSV schema.
         */
        val mistingText: String? = null,
        /**
         * Curated repotting instructions per family. Optional — null means
         * the caller falls back to `R.string.reminder_task_repot_generic`.
         * Set for families with distinctive repotting needs (cacti dry-pot
         * before watering, orchids need bark mix not soil, ferns hate root
         * disturbance).
         *
         * Same per-family caveat as `mistingText` above.
         */
        val repottingText: String? = null
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
        wateringIntervalDays = 7,
        fertilizingIntervalDays = 28,
        mistingIntervalDays = 0,
        repottingIntervalDays = 730
    )

    // Keys sind lowercased — Lookup geht auch case‑insensitive.
    private val BY_FAMILY: Map<String, CareTexts> = mapOf(
        // ------------- Sukkulenten / Wüstenpflanzen -------------
        "cactaceae" to CareTexts(
            lighting    = "Vollsonniger Standort, möglichst viele Stunden direkte Sonne.",
            soil        = "Mineralische Kakteenerde mit hohem Sand‑/Bimsanteil. Unbedingt drainagefähig.",
            fertilizing = "Im Sommer alle 4–6 Wochen sehr sparsam mit Kakteendünger. Im Winter keine Düngung.",
            watering    = "Sparsam gießen. Erde zwischen den Wassergaben komplett durchtrocknen lassen. Im Winter fast trocken halten.",
            wateringIntervalDays = 21,
            fertilizingIntervalDays = 42,   // 6 Wochen, sparsam
            mistingIntervalDays = 0,        // Kakteen NICHT besprühen
            repottingIntervalDays = 1095,   // alle 3 Jahre — wachsen langsam
            repottingText = "Im Frühling umtopfen, mit TROCKENER Kakteenerde. Vor dem Umtopfen 1 Woche nicht gießen, danach 1 Woche warten — verhindert Wurzelfäule. Stacheln mit Zeitungspapier oder Gartenhandschuhen schützen. Mineralisches Substrat (Bims, Sand, etwas Humus). Topf nur eine Größe größer."
        ),
        "crassulaceae" to CareTexts(
            lighting    = "Hell bis vollsonnig. Viel Licht fördert kräftiges, kompaktes Wachstum.",
            soil        = "Kakteen-/Sukkulentenerde mit grobem Mineralanteil.",
            fertilizing = "Im Frühling und Sommer alle 4 Wochen mit Kakteendünger halb dosiert.",
            watering    = "Nur gießen, wenn die Erde komplett trocken ist. Blätter speichern Wasser — lieber zu wenig als zu viel.",
            wateringIntervalDays = 14,
            fertilizingIntervalDays = 28,
            mistingIntervalDays = 0,        // Sukkulenten nicht sprühen — Faulnis-Risiko
            repottingIntervalDays = 1095,
            repottingText = "Im Frühling in Sukkulentenerde umtopfen. Wurzelballen sanft abschütteln, beschädigte Wurzeln entfernen. Nach dem Umtopfen 5–7 Tage warten, bevor das erste Mal gegossen wird — Schnittstellen müssen verheilen, sonst droht Fäulnis. Topfgröße nur leicht erhöhen."
        ),
        "asphodelaceae" to CareTexts(
            lighting    = "Hell bis vollsonnig. Verträgt auch direkte Sonne, besonders im Wachstum.",
            soil        = "Sandige, gut drainierende Kakteenerde.",
            fertilizing = "Im Sommer monatlich mit Kakteendünger, im Winter nicht düngen.",
            watering    = "Sparsam gießen. Erde vor der nächsten Gabe durchtrocknen lassen. Keine Staunässe.",
            wateringIntervalDays = 14,
            fertilizingIntervalDays = 28,
            mistingIntervalDays = 0,
            repottingIntervalDays = 1095,
            repottingText = "Im Frühling in sandige Sukkulentenerde umtopfen. Kindel zur Vermehrung vorsichtig abtrennen. Nach dem Umtopfen 1 Woche nicht gießen — Schnittstellen verheilen lassen, sonst Wurzelfäule. Tonscherben am Topfboden für Drainage."
        ),
        "euphorbiaceae" to CareTexts(
            lighting    = "Hell bis vollsonnig, je nach Art. Viele Arten mögen direkte Sonne.",
            soil        = "Durchlässige, eher magere Erde mit Sandanteil.",
            fertilizing = "Während der Wachstumsphase alle 4–6 Wochen schwach düngen.",
            watering    = "Mäßig bis sparsam gießen. Zwischen den Gaben antrocknen lassen. Milchsaft ist giftig – Vorsicht beim Umgang.",
            wateringIntervalDays = 14,
            fertilizingIntervalDays = 35,
            mistingIntervalDays = 0,
            repottingIntervalDays = 1095
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
            wateringIntervalDays = 5,
            fertilizingIntervalDays = 14,
            mistingIntervalDays = 3,        // Calathea & Co. brauchen hohe Luftfeuchte
            repottingIntervalDays = 730,
            mistingText = "Hohe Luftfeuchtigkeit (>60%) ist essenziell. Mit kalkarmem, zimmerwarmem Wasser besprühen — kalkhaltiges Wasser hinterlässt weiße Flecken auf den Blättern. Bei trockener Heizungsluft täglich, sonst alle 2–3 Tage. Wenn sich die Blätter einrollen, ist die Luft zu trocken.",
            repottingText = "Im Frühling in humose, kalkfreie Erde umtopfen. Wurzelballen vorsichtig — Marantaceen reagieren empfindlich auf Wurzelschock. Topf nur eine Größe größer. Nach dem Umtopfen besonders gleichmäßig feucht halten und schattig stellen, bis sich die Pflanze erholt hat."
        ),
        "bromeliaceae" to CareTexts(
            lighting    = "Hell, indirektes Licht. Direkte Mittagssonne vermeiden.",
            soil        = "Spezielle Bromelienerde oder Orchideensubstrat mit hohem Luftanteil.",
            fertilizing = "Schwach: alle 4 Wochen verdünnter Flüssigdünger, gern ins Blattinnere gespritzt.",
            watering    = "Trichter in der Blattrosette mit kalkarmem Wasser füllen. Substrat nur leicht feucht halten.",
            wateringIntervalDays = 7,
            fertilizingIntervalDays = 28,
            mistingIntervalDays = 4,        // Bromelien lieben Sprühen
            repottingIntervalDays = 1095,   // wachsen langsam
            mistingText = "Mit kalkarmem Wasser besprühen — gerne direkt in den Trichter der Blattrosette. Substrat dabei nicht zu nass werden lassen, die Wurzeln bevorzugen Trockenheit. Blattachseln gelegentlich ausspülen, damit kein stehendes Wasser fault.",
            repottingText = "Bromelien wachsen sehr langsam — meistens reicht das Umtopfen alle 3 Jahre oder erst, wenn der Topf zu klein wird. Spezielles Bromeliensubstrat oder Orchideensubstrat verwenden, niemals normale Blumenerde — die Wurzeln sind primär zur Verankerung da, nicht zur Wassersaugung. Nach der Blüte stirbt die Mutterpflanze; Kindel können separat eingetopft werden."
        ),
        "orchidaceae" to CareTexts(
            lighting    = "Hell, aber ohne direkte Mittagssonne — Ostfenster ideal.",
            soil        = "Spezielles Orchideensubstrat aus Rindenstücken, kein normaler Blumenerde.",
            fertilizing = "Alle 2–3 Wochen mit Orchideendünger (halbe Dosis). In der Ruhezeit pausieren.",
            watering    = "Tauchmethode: alle 7–14 Tage Topf kurz in Wasser tauchen, gut abtropfen lassen. Keine Staunässe.",
            wateringIntervalDays = 10,
            repottingText = "NIEMALS in normale Blumenerde umtopfen — Orchideen brauchen luftiges Rindensubstrat. Beste Zeit: nach der Blüte, alle 2–3 Jahre. Tote (braune, weiche) Wurzeln entfernen, gesunde grüne und silberweiße Luftwurzeln behutsam einrollen. Durchsichtigen Orchideentopf bevorzugen — die Wurzeln betreiben Photosynthese."
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
            wateringIntervalDays = 4,
            fertilizingIntervalDays = 35,
            mistingIntervalDays = 3,        // Farne BRAUCHEN Sprühen
            repottingIntervalDays = 730,
            mistingText = "Farne BRAUCHEN hohe Luftfeuchtigkeit. Mit kalkarmem Wasser mehrmals pro Woche besprühen, ggf. mit Luftbefeuchter unterstützen. Nicht in der Mittagssonne sprühen — Wassertropfen wirken wie Brennglas und verbrennen die Wedel.",
            repottingText = "Im Frühling in humose, leicht saure Walderde umtopfen. Rhizome NICHT vollständig mit Erde bedecken — sie brauchen Luft. Wurzelballen sehr vorsichtig behandeln, Farne reagieren stark auf Wurzelschock und können wochenlang braun werden, bevor sie sich erholen."
        ),
        "dryopteridaceae" to CareTexts(
            lighting    = "Schatten bis Halbschatten.",
            soil        = "Humose, leicht saure Erde.",
            fertilizing = "Sparsam, alle 6–8 Wochen.",
            watering    = "Erde konstant feucht, aber nie durchnässt.",
            wateringIntervalDays = 4,
            fertilizingIntervalDays = 49,
            mistingIntervalDays = 4,
            repottingIntervalDays = 730,
            mistingText = "Mit kalkarmem, zimmerwarmem Wasser besprühen für die geforderte hohe Luftfeuchtigkeit. Bei Heizungsluft im Winter häufiger sprühen oder die Pflanze auf einen Untersetzer mit feuchten Tonkugeln stellen.",
            repottingText = "Im Frühling in humose, leicht saure Erde umtopfen. Wurzelballen behutsam — Farne sind empfindlich. Topf nur eine Nummer größer. Nach dem Umtopfen besonders gleichmäßig feucht halten und schattig stellen."
        ),
        "nephrolepidaceae" to CareTexts(
            lighting    = "Halbschatten, indirektes Licht. Direkte Sonne verbrennt die Wedel.",
            soil        = "Humose, lockere Blumenerde.",
            fertilizing = "Von Frühling bis Herbst alle 2–3 Wochen mit halber Dosis düngen.",
            watering    = "Gleichmäßig feucht halten. Regelmäßig besprühen für hohe Luftfeuchte.",
            wateringIntervalDays = 4,
            fertilizingIntervalDays = 21,
            mistingIntervalDays = 3,
            repottingIntervalDays = 730,
            mistingText = "Schwertfarne lieben hohe Luftfeuchtigkeit. Mit kalkarmem, zimmerwarmem Wasser mehrmals pro Woche besprühen, besonders im Winter bei trockener Heizungsluft. Auch Blattunterseiten benetzen.",
            repottingText = "Im Frühling in humose, lockere Blumenerde umtopfen. Wurzelballen vorsichtig auflockern. Schwertfarne können auch geteilt werden, um neue Pflanzen zu vermehren — dabei jeden Teil mit eigenen Wurzeln versorgen."
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
