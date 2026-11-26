package com.example.scandepense.receiptprocessing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Logique métier de l'écran « Vérification et correction » (maquette, écran 4).
 *
 * Cette classe ne dépend pas d'Android : elle peut être testée avec javac.
 * L'Activity fournie dans A_DONNER_A_ONDOH s'appuie dessus.
 */
public final class ReceiptCorrectionController {

    private ReceiptCorrectionController() { }

    /** Applique les corrections saisies par l'utilisateur, puis revérifie la fiche. */
    public static void applyManualCorrection(ReceiptData data, String shop,
                                             String date, String amountText) {
        if (data == null) return;

        data.setShopName(shop);
        data.setExpenseDate(date);
        data.setTotalAmount(parseAmount(amountText));

        ReceiptValidator.recompute(data);

        // L'utilisateur a vérifié les champs : on lève les indicateurs d'incertitude.
        if (data.isComplete()) {
            data.markAllVerified();
        }
    }

    /** « 12 500 FCFA » -> 12500 ; « 12,50 € » -> 12.50 ; « 1 234,56 » -> 1234.56. */
    public static double parseAmount(String text) {
        if (text == null) return 0d;
        String s = text.replace("\u00A0", " ").replace("\u202F", " ").trim();
        s = s.replaceAll("(?i)(fcfa|f\\s*cfa|cfa|€|eur|euros?)", "");
        s = s.replace(" ", "").replace(',', '.').trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /** Format affiché dans la maquette : « 12 500 FCFA ». */
    public static String formatAmountForDisplay(ReceiptData data) {
        if (data == null) return "";
        double value = data.getTotalAmount();
        String number;
        if (Math.abs(value - Math.rint(value)) < 0.0001d) {
            number = String.format(Locale.FRANCE, "%,d", (long) Math.rint(value));
        } else {
            number = String.format(Locale.FRANCE, "%,.2f", Double.valueOf(value));
        }
        number = number.replace('\u202f', ' ').replace('\u00a0', ' ');
        return number + " " + data.getCurrency();
    }

    /** Champs à mettre en évidence à l'écran quand l'extraction est douteuse. */
    public static List<String> getFieldsToHighlight(ReceiptData data) {
        List<String> fields = new ArrayList<String>();
        if (data == null) return fields;
        if (data.isShopNameUncertain() || !data.hasShopName()) fields.add("Magasin");
        if (data.isDateUncertain() || !data.hasDate()) fields.add("Date du reçu");
        if (data.isAmountUncertain() || !data.hasAmount()) fields.add("Montant total");
        return fields;
    }

    /** Ligne résumée utile pour le log ou le débogage. */
    public static String getSummaryLine(ReceiptData data) {
        if (data == null) return "";
        return data.getShopName() + " | " + data.getExpenseDateDisplay()
                + " | " + formatAmountForDisplay(data)
                + " | confiance " + data.getConfidence() + " %";
    }
}
