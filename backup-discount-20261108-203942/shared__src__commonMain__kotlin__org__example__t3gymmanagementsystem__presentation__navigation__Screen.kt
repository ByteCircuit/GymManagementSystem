package org.example.t3gymmanagementsystem.presentation.navigation

enum class Screen(val title: String, val subtitle: String, val icon: String, val phase: Int) {
    Dashboard("Dashboard", "Here\u2019s what\u2019s happening at the gym today.", "grid", 7),
    Members("Members", "Manage member records, status, and membership history.", "users", 2),
    Memberships("Memberships", "Monthly and daily membership pricing, student discount, renewals.", "card", 2),
    Attendance("Attendance", "Monitor daily visits and verify members locally.", "scan", 4),
    Payments("Payments", "Review payments, receipts, and walk-ins.", "receipt", 3),
    Inventory("Products & Inventory", "Track products, stock levels, and inventory movement.", "box", 5),
    PointOfSale("Point of Sale", "Process an offline product sale.", "cart", 5),
    Reports("Reports & Analytics", "Analyze gym performance from local records.", "chart", 6),
    Settings("Settings", "Configure gym details and local data storage.", "settings", 1),
}