package com.example.scandepense.receiptprocessing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Membre 4 — Julie — Traitement des reçus.
 *
 * Entrée  : texte brut issu du module OCR de Junielle (String).
 * Sortie  : ReceiptData validé, prêt pour Ondoh (correction) et Nanfack (SQLite).
 *
 * Aucune dépendance externe : Java / Android SDK standard uniquement.
 */
public final class ReceiptParser {

    private ReceiptParser() { }

    private static final Pattern SPACED_AMOUNT = Pattern.compile(
            "(?<!\\d)(\\d{1,3})[ \\u00A0]+(\\d{2})(?!\\d)");
    private static final Pattern AMOUNT_VALUE = Pattern.compile(
            "(?i)(?<!\\d)(\\d{1,3}(?:[ \\u00A0]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?\\s*(?:€|EUR|Euros?|FCFA|F\\s*CFA|CFA)?");
    private static final Pattern MERCHANT_LABEL = Pattern.compile(
            "(?i)^\\s*(?:nom\\s+du\\s+)?(?:commerce|magasin|enseigne|marchand|vendeur)\\s*[:=]?\\s*(.+)$");
    private static final Pattern LABELLED_AMOUNT_LINE = Pattern.compile(
            "(?i).*\\b(total|montant|(?:a|à)\\s+payer|net\\s+(?:a|à)\\s+payer|somme)\\b.*");
    private static final Pattern EXCLUDED_AMOUNT_LINE = Pattern.compile(
            "(?i).*\\b(sous[- ]?total|tva|ht|article|articles|qte|quantite)\\b.*");
    private static final Pattern NOISE_LINE_START = Pattern.compile(
            "(?i)^\\s*(tva|ht|sous[- ]?total|article|articles|qte|quantite|date|heure|n°|numero|code)\\b.*");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private static final List<String> NOISE_TOKENS = Collections.unmodifiableList(Arrays.asList(
            "TICKET", "TICKET DE CAISSE", "RECU", "REÇU", "FACTURE", "CAISSE", "DATE", "HEURE",
            "TOTAL", "SOUS", "SOUS-TOTAL", "SOUS TOTAL", "TVA", "HT", "TTC", "A PAYER", "NET A PAYER",
            "MONTANT", "SOMME", "MERCI", "ARTICLE", "ARTICLES", "QTE", "QUANTITE", "CODE", "NUMERO",
            "N°", "SDF", "EUR", "EURO", "EUROS", "PAIEMENT", "CB", "VISA", "MASTERCARD", "TEL",
            "TELEPHONE", "ADRESSE", "NIF", "ICE", "BONJOUR"));

    /** API principale : texte OCR -> données structurées et validées. */
    public static ReceiptData parse(String rawText) {
        String text = normalize(rawText);
        ReceiptData data = new ReceiptData();
        data.setRawText(text);
        data.setCurrency(detectCurrency(text));

        DateResult date = extractDate(text);
        data.setExpenseDate(date.iso == null ? "" : date.iso);
        data.setDateShortYear(date.shortYear);

        AmountResult amount = extractAmount(text);
        data.setTotalAmount(Math.max(0d, amount.value));
        data.setAmountEstimated(!amount.labelled && amount.value > 0d);
        data.setAmountSpacedReconstructed(amount.spacedConverted);

        MerchantResult merchant = extractMerchant(text);
        data.setShopName(merchant.value == null ? "" : merchant.value);
        data.setShopNameFallback(merchant.fallback);

        return ReceiptValidator.recompute(data);
    }

    /** Alias explicite demandé dans le cahier des charges. */
    public static ReceiptData parseAndValidate(String rawText) {
        return parse(rawText);
    }

    /* ------------------------------------------------------------------ */
    /* Date                                                                */
    /* ------------------------------------------------------------------ */

    private static DateResult extractDate(String text) {
        Pattern[] patterns = new Pattern[] {
                ReceiptValidator.DATE_DMY,
                ReceiptValidator.DATE_YMD,
                ReceiptValidator.DATE_DMY_SHORT
        };

        for (int i = 0; i < patterns.length; i++) {
            Matcher m = patterns[i].matcher(text);
            while (m.find()) {
                String day;
                String month;
                String year;
                boolean shortYear = false;

                if (i == 0) {                       // dd/MM/yyyy
                    day = m.group(1);
                    month = m.group(2);
                    year = m.group(3);
                } else if (i == 1) {                // yyyy-MM-dd
                    year = m.group(1);
                    month = m.group(2);
                    day = m.group(3);
                } else {                            // dd/MM/yy
                    day = m.group(1);
                    month = m.group(2);
                    int yy = Integer.parseInt(m.group(3));
                    int century = yy <= 30 ? 2000 : 1900;
                    year = String.valueOf(century + yy);
                    shortYear = true;
                }

                String iso = ReceiptValidator.normalizeDate(day, month, year);
                if (iso != null) return new DateResult(iso, shortYear);
            }
        }
        return new DateResult(null, false);
    }

    /* ------------------------------------------------------------------ */
    /* Montant                                                             */
    /* ------------------------------------------------------------------ */

    private static AmountResult extractAmount(String text) {
        String[] lines = text.split("\n", -1);
        AmountResult best = null;

        // 1) Lignes explicitement libellées : TOTAL, MONTANT, À PAYER...
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.FRANCE);
            if (!LABELLED_AMOUNT_LINE.matcher(lower).find()) continue;
            if (EXCLUDED_AMOUNT_LINE.matcher(lower).find()) continue;

            AmountScan scan = findAmounts(ReceiptValidator.stripDatesAndTimes(line), true);
            if (!scan.values.isEmpty()) {
                best = new AmountResult(
                        scan.values.get(scan.values.size() - 1), true, scan.spacedConverted);
            }
        }
        if (best != null) return best;

        // 2) Repli : plus grande valeur plausible, hors lignes techniques.
        double max = -1d;
        boolean spaced = false;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.FRANCE);
            if (NOISE_LINE_START.matcher(lower).find()) continue;
            if (NOISE_TOKENS.contains(line.trim().toUpperCase(Locale.FRANCE))) continue;

            AmountScan scan = findAmounts(ReceiptValidator.stripDatesAndTimes(line), false);
            for (Double value : scan.values) {
                if (value.doubleValue() > max) {
                    max = value.doubleValue();
                    spaced = scan.spacedConverted;
                }
            }
        }
        if (max > 0d) return new AmountResult(max, false, spaced);

        return new AmountResult(-1d, false, false);
    }

    private static AmountScan findAmounts(String cleaned, boolean labelled) {
        AmountScan scan = new AmountScan();

        // « 12 50 » -> « 12.50 » seulement quand il y a exactement 2 chiffres après l'espace.
        Matcher spaced = SPACED_AMOUNT.matcher(cleaned);
        StringBuffer rebuilt = new StringBuffer();
        while (spaced.find()) {
            scan.spacedConverted = true;
            spaced.appendReplacement(rebuilt,
                    Matcher.quoteReplacement(spaced.group(1) + "." + spaced.group(2)));
        }
        spaced.appendTail(rebuilt);

        Matcher m = AMOUNT_VALUE.matcher(rebuilt.toString());
        while (m.find()) {
            try {
                String integerPart = m.group(1).replace(" ", "").replace("\u00A0", "");
                String decimals = m.group(2);
                double value;

                if (decimals == null || decimals.isEmpty()) {
                    value = Double.parseDouble(integerPart);
                } else {
                    value = Double.parseDouble(integerPart + "." + decimals);
                }

                if (!ReceiptValidator.isPlausibleAmount(value)) continue;

                String matched = m.group().toLowerCase(Locale.FRANCE);
                boolean hasCurrency = matched.contains("€") || matched.contains("eur")
                        || matched.contains("fcfa") || matched.contains("cfa");
                if (!labelled && decimals == null && !hasCurrency
                        && value >= 1900d && value <= 2100d) {
                    continue; // probablement une année, pas un prix
                }

                scan.values.add(Double.valueOf(value));
            } catch (NumberFormatException e) {
                // Valeur OCR invalide : candidat ignoré.
            }
        }
        return scan;
    }

    /* ------------------------------------------------------------------ */
    /* Nom du magasin                                                      */
    /* ------------------------------------------------------------------ */

    private static MerchantResult extractMerchant(String text) {
        String[] lines = text.split("\n", -1);

        // 1) Libellé explicite : MAGASIN: xxx, COMMERCE xxx, ENSEIGNE = xxx...
        for (String line : lines) {
            Matcher m = MERCHANT_LABEL.matcher(line);
            if (!m.find()) continue;
            String candidate = cleanMerchant(m.group(1));
            if (isValidMerchant(candidate)) {
                return new MerchantResult(candidate, false);
            }
        }

        // 2) Première ligne lisible non technique.
        for (String line : lines) {
            String candidate = cleanMerchant(line);
            if (!isValidMerchant(candidate)) continue;

            String upper = candidate.toUpperCase(Locale.FRANCE);
            if (NOISE_TOKENS.contains(upper)) continue;
            if (startsWithNoise(upper)) continue;
            if (digitRatio(candidate) > 0.25d) continue;

            return new MerchantResult(candidate, false);
        }

        // 3) Repli : première ligne avec au moins 3 lettres.
        for (String line : lines) {
            int letters = 0;
            Matcher m = LETTER.matcher(line);
            while (m.find()) letters++;
            if (letters >= 3) {
                String candidate = cleanMerchant(line);
                if (!candidate.isEmpty()) return new MerchantResult(candidate, true);
            }
        }

        return new MerchantResult(null, false);
    }

    private static boolean isValidMerchant(String candidate) {
        if (candidate == null || candidate.length() < 3) return false;
        Matcher m = LETTER.matcher(candidate);
        if (!m.find()) return false;
        return digitRatio(candidate) <= 0.4d;
    }

    private static String cleanMerchant(String raw) {
        String s = collapse(raw);
        s = ReceiptValidator.stripDatesAndTimes(s);
        s = AMOUNT_VALUE.matcher(s).replaceAll(" ");
        s = collapse(s);
        s = s.replaceAll("^[:\\-–—|\\s]+|[:\\-–—|\\s]+$", "");
        return s;
    }

    private static boolean startsWithNoise(String upper) {
        for (String token : NOISE_TOKENS) {
            if (upper.equals(token)) return true;
            if (upper.startsWith(token + " ") || upper.startsWith(token + ":")) return true;
        }
        return false;
    }

    private static double digitRatio(String s) {
        if (s == null || s.isEmpty()) return 1d;
        int digits = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) digits++;
        }
        return (double) digits / (double) s.length();
    }

    /* ------------------------------------------------------------------ */
    /* Utilitaires et types internes                                       */
    /* ------------------------------------------------------------------ */

    /** Devise affichée dans la maquette : FCFA ; conservée pour les reçus en euros si besoin. */
    private static String detectCurrency(String text) {
        if (Pattern.compile("(?i)\\b(fcfa|f\\s*cfa|cfa)\\b").matcher(text).find()) return "FCFA";
        if (text.contains("€") || Pattern.compile("(?i)\\b(?:eur|euros?)\\b").matcher(text).find()) return "EUR";
        return "FCFA";
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\t', ' ');
    }

    private static String collapse(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private static final class DateResult {
        final String iso;
        final boolean shortYear;
        DateResult(String iso, boolean shortYear) {
            this.iso = iso;
            this.shortYear = shortYear;
        }
    }

    private static final class AmountResult {
        final double value;
        final boolean labelled;
        final boolean spacedConverted;
        AmountResult(double value, boolean labelled, boolean spacedConverted) {
            this.value = value;
            this.labelled = labelled;
            this.spacedConverted = spacedConverted;
        }
    }

    private static final class AmountScan {
        final List<Double> values = new ArrayList<Double>();
        boolean spacedConverted = false;
    }

    private static final class MerchantResult {
        final String value;
        final boolean fallback;
        MerchantResult(String value, boolean fallback) {
            this.value = value;
            this.fallback = fallback;
        }
    }
}
