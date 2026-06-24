package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.*

class RutinRepository(private val db: RutinDatabase) {

    private val userDao = db.userDao()
    private val merchantDao = db.merchantDao()
    private val campaignDao = db.campaignDao()
    private val transactionDao = db.transactionDao()
    private val routeDao = db.routeDao()

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    val allMerchants: Flow<List<MerchantEntity>> = merchantDao.getAllMerchants()
    val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allRoutes: Flow<List<RouteEntity>> = routeDao.getAllRoutes()

    fun getUserById(id: Int): Flow<UserEntity?> = userDao.getUserById(id)
    fun getTransactionsForUser(userId: Int): Flow<List<TransactionEntity>> = transactionDao.getTransactionsForUser(userId)
    fun getTransactionsForMerchant(merchantId: Int): Flow<List<TransactionEntity>> = transactionDao.getTransactionsForMerchant(merchantId)
    fun getCampaignsByMerchant(merchantId: Int): Flow<List<CampaignEntity>> = campaignDao.getCampaignsByMerchant(merchantId)
    fun getUserRouteStatuses(userId: Int): Flow<List<UserRouteStatusEntity>> = routeDao.getUserRouteStatuses(userId)
    fun getMerchantsForRoute(routeId: Int): Flow<List<MerchantEntity>> = merchantDao.getMerchantsForRoute(routeId)

    // User operations
    suspend fun insertUser(user: UserEntity): Long = userDao.insertUser(user)
    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)

    // Merchant operations
    suspend fun insertMerchant(merchant: MerchantEntity): Long = merchantDao.insertMerchant(merchant)

    // Campaign operations
    suspend fun insertCampaign(campaign: CampaignEntity): Long = campaignDao.insertCampaign(campaign)

    // Route operations
    suspend fun insertRoute(route: RouteEntity): Long = routeDao.insertRoute(route)
    suspend fun insertRouteMerchantCrossRef(routeId: Int, merchantId: Int) {
        routeDao.insertRouteMerchantCrossRef(RouteMerchantCrossRef(routeId, merchantId))
    }

    // Helper: Haversine distance in meters
    fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    // Scan result payload
    data class ScanQrResult(
        val success: Boolean,
        val message: String,
        val pointsEarned: Int = 0,
        val merchantName: String = ""
    )

    fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    // BUSINESS RULE 1: GPS AND HASH VALIDATION FOR SCANS
    suspend fun processQrStampScan(userId: Int, qrHash: String, userLat: Double, userLng: Double): ScanQrResult {
        // 1. Get user
        val user = userDao.getUserByIdSync(userId) ?: return ScanQrResult(false, "Usuario no encontrado.")

        // 2. Get Merchant by QR code hash
        val merchant = merchantDao.getMerchantByHash(qrHash) ?: return ScanQrResult(false, "Código QR de comercio no válido.")

        // 3. Compute Distance
        val distance = calculateDistanceInMeters(userLat, userLng, merchant.lat, merchant.lng)
        val tolerance = 50.0 // 50 meters tolerance

        if (distance > tolerance) {
            return ScanQrResult(
                success = false,
                message = "Estás demasiado lejos de ${merchant.name} para certificar tu consumo. Distancia: ${distance.toInt()}m (Tolerancia: ${tolerance.toInt()}m).",
                merchantName = merchant.name
            )
        }

        // 4. Calculate Dynamic Points
        val allStampsVal = transactionDao.getTransactionsForUser(userId).firstOrNull() ?: emptyList()
        val allStamps = allStampsVal.filter {
            it.type == "STAMP" && it.merchantId == merchant.id
        }

        val now = System.currentTimeMillis()
        val stampsToday = allStamps.filter { isSameDay(it.timestamp, now) }
        val hasHistoricScans = allStamps.isNotEmpty()

        var pointsGained = 0
        var note = ""
        var isDailyLimitAlert = false

        if (!hasHistoricScans) {
            // Primer escaneo histórico en un comercio nuevo: otorga 10 puntos.
            pointsGained = 10
            note = "¡Comercio nuevo registrado! Primer escaneo histórico en ${merchant.name}: sumas +10 puntos."
        } else {
            // Let's check if the historic first scan was TODAY, or has scanned in past days
            val hasScannedInPastDays = allStamps.any { !isSameDay(it.timestamp, now) }
            
            if (!hasScannedInPastDays) {
                // The first scan historically at this merchant was TODAY.
                // We are still on the same FIRST day as the historical scan.
                // Escaneos subsiguientes en el mismo día: 2° escaneo = 3 puntos; 3° y 4° escaneo = 1 punto cada uno.
                // A partir del 5° escaneo en el mismo día, otorga 0 puntos y muestra una alerta en la app.
                val todayCount = stampsToday.size + 1 // +1 for the current scan being performed
                val scanOrdinal = when (todayCount) {
                    2 -> "2°"
                    3 -> "3°"
                    4 -> "4°"
                    else -> "${todayCount}°"
                }
                
                when (todayCount) {
                    2 -> {
                        pointsGained = 3
                        note = "Sello subsiguiente hoy ($scanOrdinal escaneo) en ${merchant.name}: sumas +3 puntos."
                    }
                    3 -> {
                        pointsGained = 1
                        note = "Sello subsiguiente hoy ($scanOrdinal escaneo) en ${merchant.name}: sumas +1 punto."
                    }
                    4 -> {
                        pointsGained = 1
                        note = "Sello subsiguiente hoy ($scanOrdinal escaneo) en ${merchant.name}: sumas +1 punto."
                    }
                    else -> {
                        pointsGained = 0
                        note = "⚠️ ALERTA: Has alcanzado el límite diario (5° o más escaneos hoy en el mismo local). Se otorga 0 puntos de fidelidad en ${merchant.name} hoy."
                        isDailyLimitAlert = true
                    }
                }
            } else {
                // Reinicio diario (any subsequent day):
                // Al día siguiente, el primer escaneo en ese mismo comercio otorga 3 puntos, y los dos siguientes 1 punto. Luego, vuelve a 0.
                val todayCount = stampsToday.size + 1 // +1 for current scan
                val scanOrdinal = when (todayCount) {
                    1 -> "1°"
                    2 -> "2°"
                    3 -> "3°"
                    else -> "${todayCount}°"
                }

                when (todayCount) {
                    1 -> {
                        pointsGained = 3
                        note = "Reinicio diario ($scanOrdinal escaneo hoy) en ${merchant.name}: sumas +3 puntos."
                    }
                    2 -> {
                        pointsGained = 1
                        note = "Reinicio diario ($scanOrdinal escaneo hoy) en ${merchant.name}: sumas +1 punto."
                    }
                    3 -> {
                        pointsGained = 1
                        note = "Reinicio diario ($scanOrdinal escaneo hoy) en ${merchant.name}: sumas +1 punto."
                    }
                    else -> {
                        pointsGained = 0
                        note = "⚠️ ALERTA: Superaste el límite diario de reinicio (4° o más escaneos hoy en el mismo local). Recibes 0 puntos en ${merchant.name} hoy."
                        isDailyLimitAlert = true
                    }
                }
            }
        }

        // Update points
        val updatedPoints = user.points + pointsGained
        userDao.updatePoints(userId, updatedPoints)

        // 5. Add stamps/points transaction
        val transaction = TransactionEntity(
            userId = userId,
            merchantId = merchant.id,
            pointsChange = pointsGained,
            type = "STAMP",
            timestamp = now,
            verifiedGps = true
        )
        transactionDao.insertTransaction(transaction)

        // 6. Update routes progress!
        updateRouteStatusAfterStamp(userId, merchant.id)

        val fullMsg = if (isDailyLimitAlert) {
            note
        } else {
            "Sello verificado con éxito. $note"
        }

        return ScanQrResult(
            success = true,
            message = fullMsg,
            pointsEarned = pointsGained,
            merchantName = merchant.name
        )
    }

    // BUSINESS RULE 2: COUPON CANJE / REDEMPTION DE LA CAMPAÑA
    data class RedeemResult(
        val success: Boolean,
        val message: String,
        val pointsCharged: Int = 0
    )

    suspend fun processCouponRedemption(userId: Int, campaignId: Int): RedeemResult {
        // 1. Fetch details
        val user = userDao.getUserByIdSync(userId) ?: return RedeemResult(false, "Socio / Vecino no encontrado.")
        val campaigns = allCampaigns.firstOrNull() ?: emptyList()
        val campaign = campaigns.find { it.id == campaignId } ?: return RedeemResult(false, "Campaña de recompensa no identificada.")

        // 2. Check points balance
        if (user.points < campaign.costPoints) {
            return RedeemResult(
                success = false,
                message = "Puntos insuficientes del cliente. Balance: ${user.points} puntos. Costo recompensa: ${campaign.costPoints} puntos."
            )
        }

        // 3. Deduct points
        val updatedPoints = user.points - campaign.costPoints
        userDao.updatePoints(userId, updatedPoints)

        // 4. Record CLAIM transaction
        val transaction = TransactionEntity(
            userId = userId,
            merchantId = campaign.merchantId,
            pointsChange = -campaign.costPoints,
            type = "CLAIM",
            verifiedGps = true
        )
        transactionDao.insertTransaction(transaction)

        return RedeemResult(
            success = true,
            message = "Canje procesado exitosamente. Se descontaron ${campaign.costPoints} puntos por '${campaign.title}'. Marcar como Entregado.",
            pointsCharged = campaign.costPoints
        )
    }

    // Helper: Update routes containing the scanned merchant
    private suspend fun updateRouteStatusAfterStamp(userId: Int, merchantId: Int) {
        val routes = allRoutes.firstOrNull() ?: return
        val userRouteStatuses = routeDao.getUserRouteStatuses(userId).firstOrNull() ?: emptyList()

        for (route in routes) {
            val routeMerchants = merchantDao.getMerchantsForRoute(route.id).firstOrNull() ?: emptyList()
            if (routeMerchants.any { it.id == merchantId }) {
                // Find status
                val record = userRouteStatuses.find { it.routeId == route.id }
                val currentProgress = (record?.progressCount ?: 0) + 1
                val isCompleted = currentProgress >= routeMerchants.size

                val newRecord = UserRouteStatusEntity(
                    userId = userId,
                    routeId = route.id,
                    completed = isCompleted,
                    progressCount = if (isCompleted) routeMerchants.size else currentProgress
                )
                routeDao.insertUserRouteStatus(newRecord)

                // If completed now and wasn't completed before, award extra Route Completion +50 points!
                if (isCompleted && !(record?.completed ?: false)) {
                    val user = userDao.getUserByIdSync(userId)
                    if (user != null) {
                        userDao.updatePoints(userId, user.points + 50)
                        
                        // Log a bonus points transaction
                        transactionDao.insertTransaction(
                            TransactionEntity(
                                userId = userId,
                                merchantId = merchantId, // linked to the triggering merchant
                                pointsChange = 50,
                                type = "BONUS_ROUTE"
                            )
                        )
                    }
                }
            }
        }
    }
}
