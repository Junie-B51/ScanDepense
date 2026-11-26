package com.scandepenses.app.database;

public class Expense {

    private long id;
    private String shopName;
    private String expenseDate;   // yyyy-MM-dd
    private double totalAmount;
    private String imagePath;
    private String category;

    public Expense() {
        this.id = -1;
    }

    public Expense(String shopName, String expenseDate, double totalAmount,
                   String imagePath, String category) {
        this.id = -1;
        this.shopName = shopName;
        this.expenseDate = expenseDate;
        this.totalAmount = totalAmount;
        this.imagePath = imagePath;
        this.category = category;
    }

    public Expense(long id, String shopName, String expenseDate, double totalAmount,
                   String imagePath, String category) {
        this(shopName, expenseDate, totalAmount, imagePath, category);
        this.id = id;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getExpenseDate() { return expenseDate; }
    public void setExpenseDate(String expenseDate) { this.expenseDate = expenseDate; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Override
    public String toString() {
        return "Expense{id=" + id + ", shop='" + shopName + "', date='" + expenseDate
                + "', total=" + totalAmount + ", category='" + category + "'}";
    }
}