package com.scandepenses.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Création et mise à jour de la base SQLite de ScanDépenses.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "scandepenses.db";
    public static final int DATABASE_VERSION = 1;

    // Table et colonnes
    public static final String TABLE_EXPENSES = "expenses";
    public static final String COL_ID = "id";
    public static final String COL_SHOP_NAME = "shop_name";
    public static final String COL_EXPENSE_DATE = "expense_date";
    public static final String COL_TOTAL_AMOUNT = "total_amount";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_CATEGORY = "category";

    private static final String CREATE_TABLE_EXPENSES =
            "CREATE TABLE " + TABLE_EXPENSES + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_SHOP_NAME + " TEXT NOT NULL, "
                    + COL_EXPENSE_DATE + " TEXT NOT NULL, "
                    + COL_TOTAL_AMOUNT + " REAL NOT NULL, "
                    + COL_IMAGE_PATH + " TEXT, "
                    + COL_CATEGORY + " TEXT"
                    + ");";

    private static final String CREATE_INDEX_DATE =
            "CREATE INDEX idx_expenses_date ON " + TABLE_EXPENSES
                    + " (" + COL_EXPENSE_DATE + ");";

    private static DatabaseHelper instance;

    /** Une seule instance partagée dans toute l'application. */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_EXPENSES);
        db.execSQL(CREATE_INDEX_DATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 : pas encore de migration. Pour faire évoluer le schéma,
        // augmenter DATABASE_VERSION et utiliser ALTER TABLE ici plutôt que
        // de supprimer la table (sinon les dépenses de l'utilisateur seraient perdues).
    }
}