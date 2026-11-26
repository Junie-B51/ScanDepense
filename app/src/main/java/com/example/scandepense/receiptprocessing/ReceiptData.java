package com.example.scandepense.receiptprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Données d'un reçu après traitement par Julie (Membre 4).
 *
 * Contrat avec Nanfack (base SQLite, table expenses) :
 *   getShopName()   -> shop_name
 *   getExpenseDate()-> expense_date  (format ISO yyyy-MM-dd)
 *   getTotalAmount()-> total_amount
 *
 * Contrat avec Ondoh (écran de correction) :
 *   getters + setters, indicateurs d'incertitude et needsCorrection().
 */
public class ReceiptData {

    private String shopName = "";
    private String expenseDate = "";   // ISO yyyy-MM-dd
    private double totalAmount = 0d;
    private String currency = "FCFA";
    private String rawText = "";
    private List<String> issues = new ArrayList<String>();
    private int confidence = 100;

    // Indicateurs positionnés par le parser, exploités par le validator et l'écran de correction.
    private boolean shopNameUncertain;
    private boolean dateUncertain;
    private boolean amountUncertain;
    private boolean shopNameFallback;
    private boolean dateShortYear;
    private boolean amountEstimated;
    private boolean amountSpacedReconstructed;

    public ReceiptData() { }

    public ReceiptData(String shopName, String expenseDate, double totalAmount, String rawText) {
        setShopName(shopName);
        setExpenseDate(expenseDate);
        setTotalAmount(totalAmount);
        this.rawText = rawText == null ? "" : rawText;
    }

    /* ---------- Champs métier ---------- */

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) {
        this.shopName = shopName == null ? "" : shopName.trim();
    }

    public String getExpenseDate() { return expenseDate; }
    public void setExpenseDate(String expenseDate) {
        String normalized = ReceiptValidator.normalizeDateInput(expenseDate);
        this.expenseDate = normalized == null
                ? (expenseDate == null ? "" : expenseDate.trim())
                : normalized;
    }

    public String getExpenseDateDisplay() { return ReceiptValidator.toDisplayDate(expenseDate); }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) {
        this.currency = currency == null || currency.trim().isEmpty() ? "FCFA" : currency.trim();
    }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText == null ? "" : rawText; }

    /* ---------- Validation / interface ---------- */

    public List<String> getIssues() {
        return issues == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(issues);
    }
    public void setIssues(List<String> issues) {
        this.issues = issues == null ? new ArrayList<String>() : new ArrayList<String>(issues);
    }
    public String getIssuesText() {
        if (issues == null || issues.isEmpty()) return "Aucune anomalie détectée.";
        StringBuilder sb = new StringBuilder();
        for (String issue : issues) {
            if (sb.length() > 0) sb.append(" ; ");
            sb.append(issue);
        }
        return sb.toString();
    }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) {
        this.confidence = Math.max(0, Math.min(100, confidence));
    }

    public boolean isShopNameUncertain() { return shopNameUncertain; }
    public void setShopNameUncertain(boolean v) { this.shopNameUncertain = v; }

    public boolean isDateUncertain() { return dateUncertain; }
    public void setDateUncertain(boolean v) { this.dateUncertain = v; }

    public boolean isAmountUncertain() { return amountUncertain; }
    public void setAmountUncertain(boolean v) { this.amountUncertain = v; }

    public boolean isShopNameFallback() { return shopNameFallback; }
    public void setShopNameFallback(boolean v) { this.shopNameFallback = v; }

    public boolean isDateShortYear() { return dateShortYear; }
    public void setDateShortYear(boolean v) { this.dateShortYear = v; }

    public boolean isAmountEstimated() { return amountEstimated; }
    public void setAmountEstimated(boolean v) { this.amountEstimated = v; }

    public boolean isAmountSpacedReconstructed() { return amountSpacedReconstructed; }
    public void setAmountSpacedReconstructed(boolean v) { this.amountSpacedReconstructed = v; }

    public boolean hasShopName() { return shopName != null && !shopName.trim().isEmpty(); }
    public boolean hasDate() { return expenseDate != null && !expenseDate.trim().isEmpty(); }
    public boolean hasAmount() { return totalAmount > 0d; }
    public boolean isComplete() { return hasShopName() && hasDate() && hasAmount(); }

    public boolean needsCorrection() {
        return !isComplete()
                || confidence < 80
                || shopNameUncertain
                || dateUncertain
                || amountUncertain;
    }

    /** Appelé par Ondoh après correction manuelle dans l'écran de vérification. */
    public void markAllVerified() {
        shopNameUncertain = false;
        dateUncertain = false;
        amountUncertain = false;
        shopNameFallback = false;
        dateShortYear = false;
        amountEstimated = false;
        amountSpacedReconstructed = false;
        issues = new ArrayList<String>();
        confidence = 100;
    }

    /* ---------- Compatibilité avec les autres modules ---------- */

    /** Alias demandé par Junielle / les documents initiaux. */
    public String getMerchant() { return getShopName(); }
    /** Alias demandé par Junielle / les documents initiaux. */
    public String getDate() { return getExpenseDate(); }
    /** Alias demandé par Nanfack / les documents initiaux. */
    public double getAmount() { return getTotalAmount(); }

    /** Valeurs prêtes pour un INSERT dans la table expenses (hors id). */
    public String[] asInsertionValues() {
        return new String[] { shopName, expenseDate, String.valueOf(totalAmount) };
    }

    @Override
    public String toString() {
        return "ReceiptData{shopName='" + shopName + "', expenseDate=" + expenseDate
                + ", totalAmount=" + totalAmount + " " + currency
                + ", complete=" + isComplete() + ", confidence=" + confidence + "%"
                + (issues == null || issues.isEmpty() ? "" : ", issues=" + getIssuesText()) + "}";
    }
}
