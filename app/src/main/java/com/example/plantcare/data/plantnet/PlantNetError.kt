package com.example.plantcare.data.plantnet

/**
 * Menschlich klassifizierte Fehlertypen des PlantNet-Flows.
 *
 * Begründung (siehe Core-Structure-Report, Fehler 6):
 * Vorher hat PlantNetService alle Ausnahmen in `return null` versenkt. Dadurch konnte
 * die UI nicht zwischen „kein Treffer im Bild", „Schlüssel ungültig", „Tageslimit
 * erreicht" oder „keine Internet­verbindung" unterscheiden — der Nutzer sah immer
 * dieselbe Meldung. Die Enum‑Klassen hier bewahren die Unterscheidung bis in die UI.
 */
enum class PlantNetError {
    /** HTTP 401/403 – API‑Key fehlt, ist falsch oder wurde gesperrt. */
    INVALID_API_KEY,

    /** HTTP 429 – Rate‑Limit / Tagesquote des kostenlosen Kontingents erreicht. */
    QUOTA_EXCEEDED,

    /** Keine Netzwerkverbindung (UnknownHostException, ConnectException, …). */
    NO_INTERNET,

    /** Verbindung hergestellt, aber Server antwortet nicht rechtzeitig. */
    TIMEOUT,

    /** HTTP 5xx oder unerwarteter HTTP‑Code. */
    SERVER_ERROR,

    /** Alles Übrige — unbekannte Ausnahme. */
    UNKNOWN
}

/**
 * Ergebnis des Identify‑Aufrufs. `Success` trägt das ausgewertete JSON,
 * `Failure` trägt den klassifizierten Fehlertyp plus optional die rohe Nachricht
 * fürs Logging.
 */
sealed class PlantNetOutcome {
    data class Success(val response: PlantNetResponse) : PlantNetOutcome()
    data class Failure(
        val error: PlantNetError,
        val rawMessage: String? = null
    ) : PlantNetOutcome()
}
