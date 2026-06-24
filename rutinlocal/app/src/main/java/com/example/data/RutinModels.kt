package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AppRole {
    VECINO,
    COMERCIO,
    ADMIN
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: AppRole,
    val points: Int = 0,
    val currentLat: Double = -12.046374,  // Default Lima coordinates
    val currentLng: Double = -77.042793,
    val avatar: String = "👤",
    val city: String = "Lima",
    val email: String = ""
)

@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val codeHash: String, // Calculated hash for QR code reader
    val lat: Double,
    val lng: Double,
    val category: String,
    val address: String
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val merchantId: Int,
    val title: String,
    val costPoints: Int,
    val active: Boolean = true,
    val category: String = "Descuento",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val merchantId: Int,
    val pointsChange: Int,
    val type: String, // "STAMP" (puntos sumados) or "CLAIM" (canje de premio)
    val timestamp: Long = System.currentTimeMillis(),
    val verifiedGps: Boolean = true
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String // "Café", "Gastronomía", "Artesanía", "Compras"
)

@Entity(tableName = "route_merchant_cross_ref", primaryKeys = ["routeId", "merchantId"])
data class RouteMerchantCrossRef(
    val routeId: Int,
    val merchantId: Int
)

@Entity(tableName = "user_route_status", primaryKeys = ["userId", "routeId"])
data class UserRouteStatusEntity(
    val userId: Int,
    val routeId: Int,
    val completed: Boolean = false,
    val progressCount: Int = 0
)
