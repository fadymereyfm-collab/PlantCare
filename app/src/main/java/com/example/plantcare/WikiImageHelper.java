package com.example.plantcare;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to fetch real plant images from Wikipedia/Wikimedia Commons.
 * Uses the Wikipedia REST API to find thumbnail images for plant articles.
 */
public class WikiImageHelper {

    private static final String TAG = "WikiImageHelper";

    /**
     * Map of German plant names to Wikipedia search terms (scientific or English names)
     * for better image search results.
     */
    private static final Map<String, String> SEARCH_OVERRIDES = new HashMap<>();

    static {
        // Indoor plants
        SEARCH_OVERRIDES.put("Aloe Vera", "Aloe vera");
        SEARCH_OVERRIDES.put("Einblatt", "Spathiphyllum wallisii");
        SEARCH_OVERRIDES.put("Gummibaum", "Ficus elastica");
        SEARCH_OVERRIDES.put("Glücksfeder", "Zamioculcas zamiifolia");
        SEARCH_OVERRIDES.put("Ficus Benjamini", "Ficus benjamina");
        SEARCH_OVERRIDES.put("Orchidee", "Orchidaceae");
        SEARCH_OVERRIDES.put("Kaktus", "Cactus");
        SEARCH_OVERRIDES.put("Sansevieria", "Sansevieria trifasciata");
        SEARCH_OVERRIDES.put("Bogenhanf", "Sansevieria trifasciata");
        SEARCH_OVERRIDES.put("Efeutute", "Epipremnum aureum");
        SEARCH_OVERRIDES.put("Zyperngras", "Cyperus alternifolius");
        SEARCH_OVERRIDES.put("Philodendron", "Philodendron");
        SEARCH_OVERRIDES.put("Calathea", "Calathea");
        SEARCH_OVERRIDES.put("Fensterblatt", "Monstera deliciosa");
        SEARCH_OVERRIDES.put("Palme", "Arecaceae");
        SEARCH_OVERRIDES.put("Drachenbaum", "Dracaena (plant)");
        SEARCH_OVERRIDES.put("Grünlilie", "Chlorophytum comosum");
        SEARCH_OVERRIDES.put("Schusterpalme", "Aspidistra elatior");
        SEARCH_OVERRIDES.put("Farne", "Fern");
        SEARCH_OVERRIDES.put("Zamioculcas", "Zamioculcas");
        SEARCH_OVERRIDES.put("Clivie", "Clivia miniata");
        SEARCH_OVERRIDES.put("Yucca-Palme", "Yucca");
        SEARCH_OVERRIDES.put("Amaryllis", "Hippeastrum");
        SEARCH_OVERRIDES.put("Weihnachtsstern", "Euphorbia pulcherrima");
        SEARCH_OVERRIDES.put("Begonie", "Begonia");
        SEARCH_OVERRIDES.put("Gloxinie", "Sinningia speciosa");
        SEARCH_OVERRIDES.put("Geranie", "Pelargonium");
        SEARCH_OVERRIDES.put("Lavendel", "Lavandula");
        SEARCH_OVERRIDES.put("Basilikum", "Basil");
        SEARCH_OVERRIDES.put("Rosmarin", "Rosemary");
        SEARCH_OVERRIDES.put("Thymian", "Thymus vulgaris");
        SEARCH_OVERRIDES.put("Chili", "Capsicum annuum");
        SEARCH_OVERRIDES.put("Pfefferminze", "Mentha × piperita");
        SEARCH_OVERRIDES.put("Anthurie", "Anthurium");
        SEARCH_OVERRIDES.put("Hibiskus", "Hibiscus");
        SEARCH_OVERRIDES.put("Friedenslilie", "Spathiphyllum");
        SEARCH_OVERRIDES.put("Tillandsie", "Tillandsia");
        SEARCH_OVERRIDES.put("Passionsblume", "Passiflora");
        SEARCH_OVERRIDES.put("Kamelie", "Camellia japonica");
        SEARCH_OVERRIDES.put("Engelstrompete", "Brugmansia");
        SEARCH_OVERRIDES.put("Zimmerlinde", "Sparmannia africana");
        SEARCH_OVERRIDES.put("Weinrebe", "Vitis vinifera");
        SEARCH_OVERRIDES.put("Papyrus", "Cyperus papyrus");
        SEARCH_OVERRIDES.put("Zimmeraralie", "Fatsia japonica");
        SEARCH_OVERRIDES.put("Zitronenbaum", "Citrus × limon");
        SEARCH_OVERRIDES.put("Olivenbaum", "Olea europaea");
        SEARCH_OVERRIDES.put("Mimosen", "Mimosa pudica");
        SEARCH_OVERRIDES.put("Azalee", "Azalea");
        SEARCH_OVERRIDES.put("Bougainvillea", "Bougainvillea");
        SEARCH_OVERRIDES.put("Fuchsie", "Fuchsia");
        SEARCH_OVERRIDES.put("Hortensie", "Hydrangea");
        SEARCH_OVERRIDES.put("Kalanchoe", "Kalanchoe");
        SEARCH_OVERRIDES.put("Kranzschlinge", "Stephanotis floribunda");
        SEARCH_OVERRIDES.put("Lilien", "Lilium");
        SEARCH_OVERRIDES.put("Margerite", "Leucanthemum");
        SEARCH_OVERRIDES.put("Nelke", "Dianthus");
        SEARCH_OVERRIDES.put("Oleander", "Nerium");
        SEARCH_OVERRIDES.put("Pelargonie", "Pelargonium");
        SEARCH_OVERRIDES.put("Rhipsalis", "Rhipsalis");
        SEARCH_OVERRIDES.put("Schefflera", "Schefflera");
        SEARCH_OVERRIDES.put("Sonnenblume", "Helianthus annuus");
        SEARCH_OVERRIDES.put("Usambaraveilchen", "Saintpaulia");
        SEARCH_OVERRIDES.put("Veilchen", "Viola (plant)");
        SEARCH_OVERRIDES.put("Wachsblume", "Hoya carnosa");

        // Succulents
        SEARCH_OVERRIDES.put("Echeveria", "Echeveria");
        SEARCH_OVERRIDES.put("Haworthia", "Haworthia");
        SEARCH_OVERRIDES.put("Crassula ovata", "Crassula ovata");
        SEARCH_OVERRIDES.put("Sedum", "Sedum");
        SEARCH_OVERRIDES.put("Lithops", "Lithops");
        SEARCH_OVERRIDES.put("Sempervivum", "Sempervivum");
        SEARCH_OVERRIDES.put("Geldbaum", "Crassula ovata");
        SEARCH_OVERRIDES.put("Elefantenfuß", "Beaucarnea recurvata");
        SEARCH_OVERRIDES.put("Agave", "Agave");
        SEARCH_OVERRIDES.put("Weihnachtskaktus", "Schlumbergera");
        SEARCH_OVERRIDES.put("Osterkaktus", "Hatiora gaertneri");
        SEARCH_OVERRIDES.put("Schwiegermutterstuhl", "Echinocactus grusonii");
        SEARCH_OVERRIDES.put("Feigenkaktus", "Opuntia");

        // Herbs
        SEARCH_OVERRIDES.put("Oregano", "Origanum vulgare");
        SEARCH_OVERRIDES.put("Salbei", "Salvia officinalis");
        SEARCH_OVERRIDES.put("Koriander", "Coriandrum sativum");
        SEARCH_OVERRIDES.put("Lorbeer", "Laurus nobilis");
        SEARCH_OVERRIDES.put("Minze", "Mentha");
        SEARCH_OVERRIDES.put("Zitronengras", "Cymbopogon");
        SEARCH_OVERRIDES.put("Ingwer", "Ginger");
        SEARCH_OVERRIDES.put("Kurkuma", "Turmeric");
        SEARCH_OVERRIDES.put("Petersilie", "Petroselinum crispum");
        SEARCH_OVERRIDES.put("Dill", "Anethum graveolens");
        SEARCH_OVERRIDES.put("Schnittlauch", "Allium schoenoprasum");

        // Vegetables
        SEARCH_OVERRIDES.put("Tomate", "Tomato");
        SEARCH_OVERRIDES.put("Paprika", "Bell pepper");
        SEARCH_OVERRIDES.put("Gurke", "Cucumber");
        SEARCH_OVERRIDES.put("Salat", "Lettuce");
        SEARCH_OVERRIDES.put("Kürbis", "Cucurbita");
        SEARCH_OVERRIDES.put("Zucchini", "Zucchini");
        SEARCH_OVERRIDES.put("Aubergine", "Eggplant");
        SEARCH_OVERRIDES.put("Spinat", "Spinach");
        SEARCH_OVERRIDES.put("Karotte", "Carrot");
        SEARCH_OVERRIDES.put("Zwiebel", "Onion");
        SEARCH_OVERRIDES.put("Knoblauch", "Garlic");
        SEARCH_OVERRIDES.put("Mais", "Maize");
        SEARCH_OVERRIDES.put("Kartoffel", "Potato");
        SEARCH_OVERRIDES.put("Erdbeere", "Strawberry");
        SEARCH_OVERRIDES.put("Bohne", "Phaseolus vulgaris");
        SEARCH_OVERRIDES.put("Erbse", "Pisum sativum");
        SEARCH_OVERRIDES.put("Brokkoli", "Broccoli");
        SEARCH_OVERRIDES.put("Blumenkohl", "Cauliflower");
        SEARCH_OVERRIDES.put("Rosenkohl", "Brussels sprout");
        SEARCH_OVERRIDES.put("Radieschen", "Radish");
        SEARCH_OVERRIDES.put("Kohlrabi", "Kohlrabi");
        SEARCH_OVERRIDES.put("Sellerie", "Celery");
        SEARCH_OVERRIDES.put("Fenchel", "Fennel");
        SEARCH_OVERRIDES.put("Rote Bete", "Beetroot");
        SEARCH_OVERRIDES.put("Süßkartoffel", "Sweet potato");
        SEARCH_OVERRIDES.put("Grünkohl", "Kale");
        SEARCH_OVERRIDES.put("Mangold", "Chard");
        SEARCH_OVERRIDES.put("Rucola", "Eruca vesicaria");
        SEARCH_OVERRIDES.put("Chinakohl", "Napa cabbage");
        SEARCH_OVERRIDES.put("Weißkohl", "Cabbage");
        SEARCH_OVERRIDES.put("Rotkohl", "Red cabbage");
        SEARCH_OVERRIDES.put("Artischocke", "Artichoke");
        SEARCH_OVERRIDES.put("Spargel", "Asparagus");

        // Fruits
        SEARCH_OVERRIDES.put("Himbeere", "Raspberry");
        SEARCH_OVERRIDES.put("Heidelbeere", "Blueberry");
        SEARCH_OVERRIDES.put("Johannisbeere", "Ribes rubrum");
        SEARCH_OVERRIDES.put("Brombeere", "Blackberry");
        SEARCH_OVERRIDES.put("Stachelbeere", "Gooseberry");
        SEARCH_OVERRIDES.put("Feigenbaum", "Ficus carica");
        SEARCH_OVERRIDES.put("Granatapfel", "Pomegranate");
        SEARCH_OVERRIDES.put("Orangenbaum", "Citrus sinensis");
        SEARCH_OVERRIDES.put("Limette", "Lime (fruit)");
        SEARCH_OVERRIDES.put("Kiwi", "Actinidia deliciosa");
        SEARCH_OVERRIDES.put("Avocado", "Avocado");
        SEARCH_OVERRIDES.put("Mango", "Mango");
        SEARCH_OVERRIDES.put("Weintraube", "Grape");
        SEARCH_OVERRIDES.put("Melone", "Melon");
        SEARCH_OVERRIDES.put("Apfelbaum", "Malus domestica");
        SEARCH_OVERRIDES.put("Kirschbaum", "Prunus avium");
        SEARCH_OVERRIDES.put("Birnbaum", "Pyrus communis");
        SEARCH_OVERRIDES.put("Pfirsichbaum", "Prunus persica");

        // Flowering plants
        SEARCH_OVERRIDES.put("Rose", "Rose");
        SEARCH_OVERRIDES.put("Dahlie", "Dahlia");
        SEARCH_OVERRIDES.put("Chrysantheme", "Chrysanthemum");
        SEARCH_OVERRIDES.put("Gardenie", "Gardenia");
        SEARCH_OVERRIDES.put("Jasmin", "Jasminum");
        SEARCH_OVERRIDES.put("Clematis", "Clematis");
        SEARCH_OVERRIDES.put("Pfingstrose", "Paeonia");
        SEARCH_OVERRIDES.put("Tulpe", "Tulip");
        SEARCH_OVERRIDES.put("Narzisse", "Narcissus (plant)");
        SEARCH_OVERRIDES.put("Hyazinthe", "Hyacinth (plant)");
        SEARCH_OVERRIDES.put("Krokus", "Crocus");
        SEARCH_OVERRIDES.put("Gladiole", "Gladiolus");
        SEARCH_OVERRIDES.put("Iris", "Iris (plant)");
        SEARCH_OVERRIDES.put("Alpenveilchen", "Cyclamen");
        SEARCH_OVERRIDES.put("Gerbera", "Gerbera");
        SEARCH_OVERRIDES.put("Ringelblume", "Calendula officinalis");
        SEARCH_OVERRIDES.put("Tagetes", "Tagetes");
        SEARCH_OVERRIDES.put("Kornblume", "Centaurea cyanus");
        SEARCH_OVERRIDES.put("Flieder", "Syringa vulgaris");
        SEARCH_OVERRIDES.put("Magnolie", "Magnolia");
        SEARCH_OVERRIDES.put("Rhododendron", "Rhododendron");
        SEARCH_OVERRIDES.put("Stockrose", "Alcea rosea");
        SEARCH_OVERRIDES.put("Mohn", "Papaver");

        // Special indoor plants
        SEARCH_OVERRIDES.put("Monstera deliciosa", "Monstera deliciosa");
        SEARCH_OVERRIDES.put("Monstera adansonii", "Monstera adansonii");
        SEARCH_OVERRIDES.put("Pilea peperomioides", "Pilea peperomioides");
        SEARCH_OVERRIDES.put("Strelitzie", "Strelitzia reginae");
        SEARCH_OVERRIDES.put("Geigenfeige", "Ficus lyrata");
        SEARCH_OVERRIDES.put("Ficus lyrata", "Ficus lyrata");
        SEARCH_OVERRIDES.put("Ficus elastica", "Ficus elastica");
        SEARCH_OVERRIDES.put("Dieffenbachia", "Dieffenbachia");
        SEARCH_OVERRIDES.put("Croton", "Codiaeum variegatum");
        SEARCH_OVERRIDES.put("Kentia-Palme", "Howea forsteriana");
        SEARCH_OVERRIDES.put("Bergpalme", "Chamaedorea elegans");
        SEARCH_OVERRIDES.put("Goldfruchtpalme", "Dypsis lutescens");
        SEARCH_OVERRIDES.put("Kokospalme", "Cocos nucifera");
        SEARCH_OVERRIDES.put("Dattelpalme", "Phoenix dactylifera");
        SEARCH_OVERRIDES.put("Bananenpflanze", "Musa (genus)");
        SEARCH_OVERRIDES.put("Korbmarante", "Calathea");
        SEARCH_OVERRIDES.put("Kolbenfaden", "Aglaonema");
        SEARCH_OVERRIDES.put("Nestfarn", "Asplenium nidus");
        SEARCH_OVERRIDES.put("Schwertfarn", "Nephrolepis exaltata");
        SEARCH_OVERRIDES.put("Frauenhaarfarn", "Adiantum");

        // Carnivorous plants
        SEARCH_OVERRIDES.put("Venusfliegenfalle", "Venus flytrap");
        SEARCH_OVERRIDES.put("Sonnentau", "Drosera");
        SEARCH_OVERRIDES.put("Schlauchpflanze", "Sarracenia");
        SEARCH_OVERRIDES.put("Kannenpflanze", "Nepenthes");

        // Orchids
        SEARCH_OVERRIDES.put("Phalaenopsis", "Phalaenopsis");
        SEARCH_OVERRIDES.put("Dendrobium", "Dendrobium");
        SEARCH_OVERRIDES.put("Cattleya", "Cattleya");
        SEARCH_OVERRIDES.put("Cymbidium", "Cymbidium");
        SEARCH_OVERRIDES.put("Vanda", "Vanda (plant)");

        // Water plants
        SEARCH_OVERRIDES.put("Seerose", "Nymphaea");
        SEARCH_OVERRIDES.put("Lotusblume", "Nelumbo nucifera");
        SEARCH_OVERRIDES.put("Wasserhyazinthe", "Eichhornia crassipes");

        // Trees
        SEARCH_OVERRIDES.put("Bambus", "Bamboo");
        SEARCH_OVERRIDES.put("Pampasgras", "Cortaderia selloana");
        SEARCH_OVERRIDES.put("Buchsbaum", "Buxus");
        SEARCH_OVERRIDES.put("Efeu", "Hedera helix");
        SEARCH_OVERRIDES.put("Walnussbaum", "Juglans regia");
        SEARCH_OVERRIDES.put("Haselnuss", "Corylus avellana");

        // More plants
        SEARCH_OVERRIDES.put("Guzmania", "Guzmania");
        SEARCH_OVERRIDES.put("Vriesea", "Vriesea");
        SEARCH_OVERRIDES.put("Aechmea", "Aechmea");
        SEARCH_OVERRIDES.put("Flamingoblume", "Anthurium");
        SEARCH_OVERRIDES.put("Katzengras", "Cat grass");
        SEARCH_OVERRIDES.put("Peperomia obtusifolia", "Peperomia obtusifolia");
        SEARCH_OVERRIDES.put("Peperomia caperata", "Peperomia caperata");
        SEARCH_OVERRIDES.put("Dracaena marginata", "Dracaena marginata");
        SEARCH_OVERRIDES.put("Dracaena fragrans", "Dracaena fragrans");
        SEARCH_OVERRIDES.put("Purpurtute", "Syngonium podophyllum");
        SEARCH_OVERRIDES.put("Zebrakraut", "Tradescantia zebrina");
        SEARCH_OVERRIDES.put("Wunderstrauch", "Codiaeum variegatum");
        SEARCH_OVERRIDES.put("Spathiphyllum", "Spathiphyllum");
        SEARCH_OVERRIDES.put("Syngonium", "Syngonium");
        SEARCH_OVERRIDES.put("Hoya carnosa", "Hoya carnosa");
        SEARCH_OVERRIDES.put("Begonia maculata", "Begonia maculata");
        SEARCH_OVERRIDES.put("Tradescantia zebrina", "Tradescantia zebrina");
        SEARCH_OVERRIDES.put("Fittonia", "Fittonia");
        SEARCH_OVERRIDES.put("Buntnessel", "Coleus");
        SEARCH_OVERRIDES.put("Glücksbambus", "Dracaena sanderiana");
        SEARCH_OVERRIDES.put("Ananaspflanze", "Ananas comosus");
        SEARCH_OVERRIDES.put("Kaffeebaum", "Coffea arabica");
        SEARCH_OVERRIDES.put("Vanille", "Vanilla planifolia");
        SEARCH_OVERRIDES.put("Bonsai Ficus", "Ficus retusa");
        SEARCH_OVERRIDES.put("Bonsai Ahorn", "Acer palmatum");
        SEARCH_OVERRIDES.put("Cycas revoluta", "Cycas revoluta");
        SEARCH_OVERRIDES.put("Areca-Palme", "Dypsis lutescens");
        SEARCH_OVERRIDES.put("Clusia", "Clusia rosea");
        SEARCH_OVERRIDES.put("Alocasia zebrina", "Alocasia zebrina");
        SEARCH_OVERRIDES.put("Alocasia Polly", "Alocasia amazonica");
        SEARCH_OVERRIDES.put("Maranta leuconeura", "Maranta leuconeura");
        SEARCH_OVERRIDES.put("Fleißiges Lieschen", "Impatiens walleriana");
        SEARCH_OVERRIDES.put("Christrose", "Helleborus niger");
        SEARCH_OVERRIDES.put("Schneeglöckchen", "Galanthus");
        SEARCH_OVERRIDES.put("Maiglöckchen", "Convallaria majalis");
        SEARCH_OVERRIDES.put("Echinacea", "Echinacea");
        SEARCH_OVERRIDES.put("Bärlauch", "Allium ursinum");
        SEARCH_OVERRIDES.put("Kresse", "Lepidium sativum");
        SEARCH_OVERRIDES.put("Holunder", "Sambucus nigra");
        SEARCH_OVERRIDES.put("Sanddorn", "Hippophae rhamnoides");
        SEARCH_OVERRIDES.put("Paradiesvogelblume", "Strelitzia reginae");
        SEARCH_OVERRIDES.put("Plumeria", "Plumeria");
        SEARCH_OVERRIDES.put("Medinilla", "Medinilla magnifica");
        SEARCH_OVERRIDES.put("Colocasia", "Colocasia");
        SEARCH_OVERRIDES.put("Caladium", "Caladium");
        SEARCH_OVERRIDES.put("Adenium", "Adenium obesum");
    }

    /**
     * Umgekehrte Suche: wissenschaftlicher Name (oder englischer Trivialname) → deutscher Katalog‑Name.
     *
     * Hintergrund: der Katalog in `plants.csv` kennt nur deutsche Namen. Wenn PlantNet
     * eine Pflanze erkennt, liefert es i. d. R. nur den wissenschaftlichen Namen zuverlässig.
     * Für das Pflege‑Feld‑Prefill brauchen wir den Katalog‑Eintrag — also nutzen wir die
     * bestehende SEARCH_OVERRIDES‑Map rückwärts.
     *
     * Die Suche ist case‑insensitive und toleriert kleine Abweichungen (z. B. „Aloe vera" vs. „Aloe Vera").
     *
     * @param scientificOrEnglish z. B. "Polygonatum multiflorum" oder "Ficus elastica"
     * @return der deutsche Katalog‑Name, oder null wenn kein Mapping existiert.
     */
    public static String germanNameForScientific(String scientificOrEnglish) {
        if (scientificOrEnglish == null || scientificOrEnglish.trim().isEmpty()) return null;
        String needle = scientificOrEnglish.trim();
        for (Map.Entry<String, String> entry : SEARCH_OVERRIDES.entrySet()) {
            if (needle.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Fetches a thumbnail image URL from Wikipedia for the given plant name.
     * Tries the English Wikipedia REST API first with an override search term,
     * then falls back to searching with the original German name.
     *
     * @param plantName The German plant name
     * @return The thumbnail URL, or null if not found
     */
    public static String fetchImageUrl(String plantName) {
        // Get the search term to use
        String searchTerm = SEARCH_OVERRIDES.getOrDefault(plantName, plantName);

        // Try direct lookup on English Wikipedia
        String url = tryWikipediaRestApi(searchTerm, "en");
        if (url != null) return url;

        // Try German Wikipedia
        url = tryWikipediaRestApi(searchTerm, "de");
        if (url != null) return url;

        // If we used an override, also try with original name
        if (!searchTerm.equals(plantName)) {
            url = tryWikipediaRestApi(plantName, "de");
            if (url != null) return url;

            url = tryWikipediaRestApi(plantName, "en");
            if (url != null) return url;
        }

        // Try search API as last resort
        url = tryWikipediaSearch(searchTerm, "en");
        if (url != null) return url;

        url = tryWikipediaSearch(plantName, "de");
        return url;
    }

    /**
     * Uses the Wikipedia REST API /page/summary endpoint to get the thumbnail.
     */
    private static String tryWikipediaRestApi(String title, String lang) {
        try {
            String encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8");
            String apiUrl = "https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/"
                    + encodedTitle;
            Log.d(TAG, "Requesting REST API: " + apiUrl);

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            // #1 fix: pre-fix the success path returned mid-method
            // (line 395 `return source`), skipping conn.disconnect()
            // and leaving the BufferedReader / underlying socket
            // unclosed. On a catalog browse that fans out to dozens
            // of plants, this exhausted the HttpURLConnection pool
            // and starved later TLS handshakes. Wrap the whole body
            // in try/finally + try-with-resources on the reader.
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "PlantCareApp/1.0 (Android)");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                Log.d(TAG, "REST API response code for '" + title + "' (" + lang + "): " + code);
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("thumbnail")) {
                        JSONObject thumbnail = json.getJSONObject("thumbnail");
                        String source = thumbnail.optString("source", null);
                        if (source != null && !source.isEmpty()) return source;
                    }
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "REST API failed for '" + title + "' (" + lang + "): " + e.getMessage());
        }
        return null;
    }

    /**
     * Uses the Wikipedia Action API search to find articles and then get their thumbnail.
     */
    private static String tryWikipediaSearch(String query, String lang) {
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String apiUrl = "https://" + lang + ".wikipedia.org/w/api.php?"
                    + "action=query&list=search&srsearch=" + encodedQuery
                    + "&format=json&srlimit=3";
            Log.d(TAG, "Requesting Search API: " + apiUrl);

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            // #1 fix: same connection-leak shape as tryWikipediaRestApi.
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "PlantCareApp/1.0 (Android)");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                Log.d(TAG, "Search API response code for '" + query + "' (" + lang + "): " + code);
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }

                    JSONObject json = new JSONObject(sb.toString());
                    org.json.JSONArray results = json.getJSONObject("query")
                            .getJSONArray("search");

                    for (int i = 0; i < results.length(); i++) {
                        String resultTitle = results.getJSONObject(i).getString("title");
                        String imgUrl = tryWikipediaRestApi(resultTitle, lang);
                        if (imgUrl != null) return imgUrl;
                    }
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "Search API failed for '" + query + "' (" + lang + "): " + e.getMessage());
        }
        return null;
    }
}
