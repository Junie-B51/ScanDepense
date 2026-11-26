package com.example.scandepense.receiptprocessing;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Règles de validation du module « Traitement des reçus » (Julie).
 *
 * - Normalisation des dates.
 * - Contrôle de plausibilité des montants.
 * - Calcul du score de confiance et des indicateurs d'incertitude.
 */
public final class ReceiptValidator {

    private ReceiptValidator() { }

    public static final Pattern DATE_DMY = Pattern.compile(
            "(?<!\\d)(\\d{2})[/\\-. ](\\d{2})[/\\-. ](\\d{4})(?!\\d)");
    public static final Pattern DATE_YMD = Pattern.compile(
            "(?<!\\d)(\\d{4})[/\\-. ](\\d{2})[/\\-. ](\\d{2})(?!\\d)");
    public static final Pattern DATE_DMY_SHORT = Pattern.compile(
            "(?<!\\d)(\\d{2})[/\\-. ](\\d{2})[/\\-. ](\\d{2})(?!\\d)");
    public static final Pattern TIME = Pattern.compile(
            "(?<!\\d)\\d{1,2}:\\d{2}(?::\\d{2})?(?!\\d)");

    /** Convertit jour/mois/année en ISO yyyy-MM-dd, ou null si la date est invalide. */
    public static String normalizeDate(String day, String month, String year) {
        try {
            int y = Integer.parseInt(year);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            if (y < 2000 || y > currentYear + 1) return null;

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);
            sdf.setLenient(false);
            sdf.parse(day + "/" + month + "/" + year);
            return y + "-" + month + "-" + day;
        } catch (ParseException e) {
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Normalise une date saisie ou extraite : dd/MM/yyyy, yyyy-MM-dd, dd/MM/yy... */
    public static String normalizeDateInput(String input) {
        if (input == null) return null;

        Matcher m = DATE_DMY.matcher(input);
        if (m.find()) return normalizeDate(m.group(1), m.group(2), m.group(3));

        m = DATE_YMD.matcher(input);
        if (m.find()) return normalizeDate(m.group(3), m.group(2), m.group(1));

        m = DATE_DMY_SHORT.matcher(input);
        if (m.find()) {
            int yy = Integer.parseInt(m.group(3));
            int century = yy <= 30 ? 2000 : 1900;
            return normalizeDate(m.group(1), m.group(2), String.valueOf(century + yy));
        }
        return null;
    }

    /** Date ISO -> affichage dd/MM/yyyy pour l'écran de correction. */
    public static String toDisplayDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
            in.setLenient(false);
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);
            return out.format(in.parse(iso));
        } catch (ParseException e) {
            return iso;
        }
    }

    public static boolean isPlausibleAmount(double value) {
        return value > 0d && value < 1000000d;
    }

    public static boolean isFutureDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
            sdf.setLenient(false);
            long parsed = sdf.parse(iso).getTime();
            return parsed > System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        } catch (ParseException e) {
            return false;
        }
    }

    /** Retire les dates et les heures d'une ligne avant d'y chercher un montant. */
    public static String stripDatesAndTimes(String line) {
        String s = DATE_DMY.matcher(line).replaceAll(" ");
        s = DATE_YMD.matcher(s).replaceAll(" ");
        s = DATE_DMY_SHORT.matcher(s).replaceAll(" ");
        s = TIME.matcher(s).replaceAll(" ");
        return s;
    }

    /**
     * Recalcule les remarques, les indicateurs d'incertitude et le score de confiance.
     * Appelé après extraction, et de nouveau après correction manuelle si souhaité.
     */
    public static ReceiptData recompute(ReceiptData data) {
        List<String> issues = new ArrayList<String>();
        int confidence = 100;

        data.setShopNameUncertain(false);
        data.setDateUncertain(false);
        data.setAmountUncertain(false);

        if (data.getRawText() == null || data.getRawText().trim().isEmpty()) {
            issues.add("Aucun texte reconnu par l'OCR.");
            confidence -= 40;
        }

        if (!data.hasShopName()) {
            data.setShopNameUncertain(true);
            issues.add("Nom du magasin absent.");
            confidence -= 20;
        } else if (data.isShopNameFallback()) {
            data.setShopNameUncertain(true);
            issues.add("Nom du magasin estimé à la première ligne lisible.");
            confidence -= 15;
        }

        if (!data.hasDate()) {
            data.setDateUncertain(true);
            issues.add("Date absente ou invalide.");
            confidence -= 20;
        } else {
            if (ReceiptValidator.normalizeDateInput(data.getExpenseDate()) == null) {
                data.setDateUncertain(true);
                issues.add("Format de date invalide.");
                confidence -= 20;
            } else if (data.isDateShortYear()) {
                issues.add("Année sur 2 chiffres interprétée en 20" + data.getExpenseDate().substring(2, 4) + ".");
                confidence -= 10;
            }
            if (ReceiptValidator.isFutureDate(data.getExpenseDate())) {
                data.setDateUncertain(true);
                issues.add("Date située dans le futur : à vérifier.");
                confidence -= 5;
            }
        }

        if (!data.hasAmount()) {
            data.setAmountUncertain(true);
            issues.add("Montant non détecté.");
            confidence -= 30;
        } else {
            if (data.isAmountEstimated()) {
                data.setAmountUncertain(true);
                issues.add("Libellé TOTAL absent : montant estimé par la valeur maximale.");
                confidence -= 20;
            }
            if (data.isAmountSpacedReconstructed()) {
                data.setAmountUncertain(true);
                issues.add("Montant reconstruit depuis une valeur du type « 12 50 ».");
                confidence -= 10;
            }
            if (!ReceiptValidator.isPlausibleAmount(data.getTotalAmount())) {
                data.setAmountUncertain(true);
                issues.add("Montant incohérent (supérieur à 1 000 000).");
                confidence -= 20;
            }
        }

        data.setIssues(issues);
        data.setConfidence(confidence);
        return data;
    }
}
