package com.scandepenses.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.scandepenses.app.database.DatabaseHelper.*;

/**
 * Accès aux données : CRUD, recherche, filtres et calculs pour
 * l'accueil, l'historique et les statistiques.
 *
 * Les dates sont au format yyyy-MM-dd : la comparaison de texte en SQL
 * (BETWEEN, >=, <=) fonctionne alors comme une comparaison de dates.
 */
public class ExpenseRepository {

    private static final String[] ALL_COLUMNS = {
            COL_ID, COL_SHOP_NAME, COL_EXPENSE_DATE,
            COL_TOTAL_AMOUNT, COL_IMAGE_PATH, COL_CATEGORY
    };

    private static final String ORDER_RECENT_FIRST =
            COL_EXPENSE_DATE + " DESC, " + COL_ID + " DESC";

    private final DatabaseHelper helper;

    public ExpenseRepository(Context context) {
        this.helper = DatabaseHelper.getInstance(context);
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    /** Ajoute une dépense. Retourne son id, ou -1 en cas d'échec. */
    public long insert(Expense expense) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long id = db.insert(TABLE_EXPENSES, null, toValues(expense));
        if (id != -1) {
            expense.setId(id);
        }
        return id;
    }

    /** Retourne la dépense correspondant à l'id, ou null si introuvable. */
    public Expense getById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(TABLE_EXPENSES, ALL_COLUMNS, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            c.close();
        }
    }

    /** Toutes les dépenses, les plus récentes en premier. */
    public List<Expense> getAll() {
        return query(null, null, ORDER_RECENT_FIRST, null);
    }

    /** Met à jour une dépense. Retourne le nombre de lignes modifiées (1 si OK). */
    public int update(Expense expense) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.update(TABLE_EXPENSES, toValues(expense), COL_ID + " = ?",
                new String[]{String.valueOf(expense.getId())});
    }

    /** Supprime une dépense. Retourne le nombre de lignes supprimées (1 si OK). */
    public int delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(TABLE_EXPENSES, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    // ------------------------------------------------------------------
    // Historique : recherche et filtres (Tout / Jour / Semaine / Mois)
    // ------------------------------------------------------------------

    /** Recherche par nom de magasin (contient le texte saisi). */
    public List<Expense> searchByShop(String text) {
        return query(COL_SHOP_NAME + " LIKE ?", new String[]{"%" + text + "%"},
                ORDER_RECENT_FIRST, null);
    }

    /** Dépenses entre deux dates incluses (format yyyy-MM-dd). */
    public List<Expense> getBetween(String startDate, String endDate) {
        return query(COL_EXPENSE_DATE + " BETWEEN ? AND ?",
                new String[]{startDate, endDate}, ORDER_RECENT_FIRST, null);
    }

    /** Recherche par magasin ET période, pour combiner barre de recherche et filtre. */
    public List<Expense> searchBetween(String text, String startDate, String endDate) {
        return query(COL_SHOP_NAME + " LIKE ? AND " + COL_EXPENSE_DATE + " BETWEEN ? AND ?",
                new String[]{"%" + text + "%", startDate, endDate},
                ORDER_RECENT_FIRST, null);
    }

    // ------------------------------------------------------------------
    // Accueil : "Dépensé ce mois" et "Dernières dépenses"
    // ------------------------------------------------------------------

    /** Les N dernières dépenses (ex. 3 ou 5 pour l'écran d'accueil). */
    public List<Expense> getLatest(int limit) {
        return query(null, null, ORDER_RECENT_FIRST, String.valueOf(limit));
    }

    /** Somme des montants entre deux dates incluses. 0 s'il n'y a aucune dépense. */
    public double getTotalBetween(String startDate, String endDate) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + COL_TOTAL_AMOUNT + "), 0) FROM "
                        + TABLE_EXPENSES + " WHERE " + COL_EXPENSE_DATE + " BETWEEN ? AND ?",
                new String[]{startDate, endDate});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }

    // ------------------------------------------------------------------
    // Statistiques
    // ------------------------------------------------------------------

    /**
     * Total par jour entre deux dates (pour le graphique en barres).
     * Clé = date yyyy-MM-dd, valeur = total du jour. Les jours sans dépense
     * sont absents : l'écran de statistiques doit les compléter avec 0.
     */
    public Map<String, Double> getDailyTotals(String startDate, String endDate) {
        Map<String, Double> result = new LinkedHashMap<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_EXPENSE_DATE + ", SUM(" + COL_TOTAL_AMOUNT
                        + ") FROM " + TABLE_EXPENSES
                        + " WHERE " + COL_EXPENSE_DATE + " BETWEEN ? AND ?"
                        + " GROUP BY " + COL_EXPENSE_DATE
                        + " ORDER BY " + COL_EXPENSE_DATE + " ASC",
                new String[]{startDate, endDate});
        try {
            while (c.moveToNext()) {
                result.put(c.getString(0), c.getDouble(1));
            }
        } finally {
            c.close();
        }
        return result;
    }

    /** Les plus grosses dépenses d'une période ("Dépenses les plus importantes"). */
    public List<Expense> getTopExpenses(String startDate, String endDate, int limit) {
        return query(COL_EXPENSE_DATE + " BETWEEN ? AND ?",
                new String[]{startDate, endDate},
                COL_TOTAL_AMOUNT + " DESC", String.valueOf(limit));
    }

    // ------------------------------------------------------------------
    // Périodes prêtes à l'emploi (format yyyy-MM-dd, semaine du lundi au dimanche)
    // ------------------------------------------------------------------

    public static String today() {
        return format(Calendar.getInstance());
    }

    /** {début, fin} de la semaine en cours. */
    public static String[] currentWeekRange() {
        Calendar start = Calendar.getInstance();
        start.setFirstDayOfWeek(Calendar.MONDAY);
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        return new String[]{format(start), format(end)};
    }

    /** {début, fin} du mois en cours. */
    public static String[] currentMonthRange() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        Calendar end = (Calendar) start.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        return new String[]{format(start), format(end)};
    }

    private static String format(Calendar cal) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    // ------------------------------------------------------------------
    // Outils internes
    // ------------------------------------------------------------------

    private List<Expense> query(String selection, String[] args, String orderBy, String limit) {
        List<Expense> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(TABLE_EXPENSES, ALL_COLUMNS, selection, args,
                null, null, orderBy, limit);
        try {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        } finally {
            c.close();
        }
        return list;
    }

    private ContentValues toValues(Expense e) {
        ContentValues v = new ContentValues();
        v.put(COL_SHOP_NAME, e.getShopName());
        v.put(COL_EXPENSE_DATE, e.getExpenseDate());
        v.put(COL_TOTAL_AMOUNT, e.getTotalAmount());
        v.put(COL_IMAGE_PATH, e.getImagePath());
        v.put(COL_CATEGORY, e.getCategory());
        return v;
    }

    private Expense fromCursor(Cursor c) {
        return new Expense(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_SHOP_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_EXPENSE_DATE)),
                c.getDouble(c.getColumnIndexOrThrow(COL_TOTAL_AMOUNT)),
                c.getString(c.getColumnIndexOrThrow(COL_IMAGE_PATH)),
                c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)));
    }
}