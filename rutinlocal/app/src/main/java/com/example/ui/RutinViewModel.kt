package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RutinViewModel(application: Application) : AndroidViewModel(application) {

    private val db = RutinDatabase.getDatabase(application, viewModelScope)
    private val repository = RutinRepository(db)

    // Firebase Services
    val authService by lazy { AuthService() }
    val scannerService by lazy { ScannerService() }

    // Flow states
    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMerchants: StateFlow<List<MerchantEntity>> = repository.allMerchants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCampaigns: StateFlow<List<CampaignEntity>> = repository.allCampaigns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRoutes: StateFlow<List<RouteEntity>> = repository.allRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active logged-in user profile
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Route progress for active user
    val activeUserRoutes: StateFlow<List<UserRouteStatusEntity>> = _currentUser
        .filterNotNull()
        .flatMapLatest { user -> repository.getUserRouteStatuses(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transactions for active user
    val activeUserTransactions: StateFlow<List<TransactionEntity>> = _currentUser
        .filterNotNull()
        .flatMapLatest { user -> repository.getTransactionsForUser(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active real GPS coordinates
    var userLatitude by mutableStateOf(-12.046374)
    var userLongitude by mutableStateOf(-77.042793)

    // For backwards compatibility and visual binding
    val simulatedLat: Double get() = userLatitude
    val simulatedLng: Double get() = userLongitude

    // Dialog & Feedback States
    var scanResult by mutableStateOf<RutinRepository.ScanQrResult?>(null)
    var redeemResult by mutableStateOf<RutinRepository.RedeemResult?>(null)
    var toastMessage by mutableStateOf<String?>(null)
    
    // Push Notification State
    var activePushNotification by mutableStateOf<String?>(null)

    fun sendLocalSystemNotification(context: android.content.Context, message: String) {
        try {
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "rutin_local_notifications"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "RutinLocal Alertas",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones locales de fidelización del barrio"
                    enableLights(true)
                    lightColor = android.graphics.Color.MAGENTA
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("RutinLocal 🗺️")
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Star Milestone gift-chest popup state
    var activeStarMilestone by mutableStateOf<StarMilestone?>(null)

    fun triggerMilestone(station: Int) {
        val couponName = when (station) {
            1 -> "Cupón Bronce: 15% Desc 🎟️"
            2 -> "Cupón Plata: Bebida Gratis ☕"
            3 -> "Cupón Oro: Postre Completo 🍰"
            else -> "Super Cofre de Barrio Semanal 🎁"
        }
        val couponRewardDesc = when (station) {
            1 -> "Disfruta de un 15% de descuento directo en tus consumos de panadería o almacén."
            2 -> "Reclama un fragante Espresso de Especialidad gratis en Cafecito Marita."
            3 -> "Disfruta de un delicioso queque o postre del día gratis en Almacén Don Lucho."
            else -> "¡El tesoro del gran caminante del barrio! Vale de compra de S/. 50 + Pase Dorado."
        }
        activeStarMilestone = StarMilestone(station, couponName, couponRewardDesc)
    }

    // Coupon scanning logic & state for Merchant checkouts
    var activeCouponScanResult by mutableStateOf<String?>(null)
    var couponScanSuccess by mutableStateOf<Boolean?>(null)

    fun getCostForStation(station: Int): Int {
        return when (station) {
            1 -> 100
            2 -> 180
            3 -> 240
            else -> 280 + (station - 4) * 40
        }
    }

    fun claimMilestoneNow(milestone: StarMilestone) {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                val cost = getCostForStation(milestone.stationNumber)
                if (user.points < cost) {
                    toastMessage = "❌ Puntos insuficientes para canjear '${milestone.couponName}' ($cost pts necesarios)."
                    activeStarMilestone = null
                    return@launch
                }

                // Insert COUPON_ACTIVO representing the physical ticket reward that must be scanned in-store
                db.transactionDao().insertTransaction(
                    TransactionEntity(
                        userId = user.id,
                        merchantId = 1, // Default general merchant
                        pointsChange = milestone.stationNumber, // Stock the station level
                        type = "COUPON_ACTIVO",
                        timestamp = System.currentTimeMillis(),
                        verifiedGps = true
                    )
                )

                // Deduct points and reset progression relative to start of new cycle
                val updatedPoints = user.points - cost
                db.userDao().updatePoints(user.id, updatedPoints)

                // Refresh currentUser
                val freshUser = db.userDao().getUserByIdSync(user.id)
                if (freshUser != null) {
                    _currentUser.value = freshUser
                }

                toastMessage = "🎉 ¡Canje de '${milestone.couponName}' iniciado! Se descontaron $cost puntos, tu caminito se reinició y tu saldo de estrellas es $updatedPoints pts. Presenta tu código QR en caja. 🎟️"
                activeStarMilestone = null
            }
        }
    }

    fun continueInRoute(milestone: StarMilestone) {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                // User has chosen to RISK and keep accumulating their stars to reach better rewards!
                // No coupon is delivered, no points are lost, we just keep active points intact.
                toastMessage = "🐾 ¡Has elegido arriesgar y seguir acumulando! Vas con todo buscando un premio superior en la Red de Coexistencia Semanal. 🚀"
                activeStarMilestone = null
            }
        }
    }

    fun scanCouponQrCode(qrCodeStr: String) {
        val trimmed = qrCodeStr.trim()
        if (!trimmed.startsWith("rutinlocal://coupon/")) {
            activeCouponScanResult = "❌ QR Inválido: Formato desconocido. Debe empezar con rutinlocal://coupon/"
            couponScanSuccess = false
            return
        }
        val txIdStr = trimmed.substringAfter("rutinlocal://coupon/")
        val txId = txIdStr.toIntOrNull()
        if (txId == null) {
            activeCouponScanResult = "❌ Error: Código de cupón no identificable o corrupto."
            couponScanSuccess = false
            return
        }

        viewModelScope.launch {
            val tx = db.transactionDao().getTransactionByIdSync(txId)
            if (tx == null) {
                activeCouponScanResult = "❌ Error: El cupón #$txId no se encuentra registrado en nuestra base de datos."
                couponScanSuccess = false
            } else if (tx.type != "COUPON_ACTIVO") {
                activeCouponScanResult = "❌ Error: El cupón já fue canjeado previamente o está inactivo. Estado: ${tx.type}."
                couponScanSuccess = false
            } else {
                // Redeem it! Modify the transaction to COUPON_CANJEADO
                val updatedTx = tx.copy(type = "COUPON_CANJEADO", timestamp = System.currentTimeMillis())
                db.transactionDao().updateTransaction(updatedTx)

                // Fetch customer details
                val customer = db.userDao().getUserByIdSync(tx.userId)
                val customerName = customer?.name ?: "Vecino del Barrio"

                // Check if this user is a suspicious user trigger!
                val allTxList = db.transactionDao().getAllTransactions().firstOrNull() ?: emptyList()
                val suspiciousMap = getSuspiciousUsers(allTxList)
                val isSuspicious = suspiciousMap.containsKey(tx.userId)

                val couponName = when (tx.pointsChange) {
                    1 -> "Cupón Bronce: 15% Desc 🎟️"
                    2 -> "Cupón Plata: Bebida Gratis ☕"
                    3 -> "Cupón Oro: Postre Completo 🍰"
                    else -> "Super Cofre de Barrio Semanal 🎁"
                }

                if (isSuspicious) {
                    activeCouponScanResult = "⚠️ ALERTA DE COEXISTENCIA: Canje de '$couponName' por $customerName aprobado, pero FACTURADO AL COMERCIO ASOCIADO (Tráfico sospechoso detectado)."
                    couponScanSuccess = true
                } else {
                    activeCouponScanResult = "✅ ¡VALIDADO CON ÉXITO! El cupón #$txId ($couponName) de $customerName ha sido redimido correctamente en el fondo común de la comuna."
                    couponScanSuccess = true
                }
            }
        }
    }

    // Quick init loader
    init {
        // No auto-login on startup so user sees the secure welcome and credential gate first!
    }

    fun selectUser(user: UserEntity) {
        _currentUser.value = user
        userLatitude = user.currentLat
        userLongitude = user.currentLng
    }

    fun updateSimulatedGps(lat: Double, lng: Double) {
        updateUserGps(lat, lng)
    }

    fun updateUserGps(lat: Double, lng: Double) {
        userLatitude = lat
        userLongitude = lng
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                val updated = user.copy(currentLat = lat, currentLng = lng)
                repository.updateUser(updated)
                _currentUser.value = updated
            }
        }
    }

    @Suppress("DEPRECATION")
    fun updateLocationFromGps(context: android.content.Context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            toastMessage = "❌ Permiso de GPS no concedido. Por favor otorga permisos."
            return
        }

        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        if (locationManager == null) {
            toastMessage = "❌ Dispositivo sin soporte de localización."
            return
        }

        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            toastMessage = "❌ Sensor de GPS apagado. Por favor enciéndelo."
            return
        }

        var bestLocation: android.location.Location? = null
        try {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null) {
                    if (bestLocation == null || lastKnown.accuracy < bestLocation.accuracy || lastKnown.time > bestLocation.time) {
                        bestLocation = lastKnown
                    }
                }
            }
        } catch (e: SecurityException) {
            toastMessage = "❌ Error de seguridad al leer GPS."
        }

        if (bestLocation != null) {
            userLatitude = bestLocation.latitude
            userLongitude = bestLocation.longitude
            viewModelScope.launch {
                val user = currentUser.value
                if (user != null) {
                    val updated = user.copy(currentLat = bestLocation.latitude, currentLng = bestLocation.longitude)
                    repository.updateUser(updated)
                    _currentUser.value = updated
                }
            }
            toastMessage = "🛰️ Conectado a satélites. Ubicación real: (${String.format("%.5f", userLatitude)}, ${String.format("%.5f", userLongitude)})"
        } else {
            toastMessage = "🛰️ Buscando señal de GPS real... Por favor espera."
            try {
                val provider = if (isGpsEnabled) android.location.LocationManager.GPS_PROVIDER else android.location.LocationManager.NETWORK_PROVIDER
                locationManager.requestSingleUpdate(provider, object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        userLatitude = location.latitude
                        userLongitude = location.longitude
                        viewModelScope.launch {
                            val user = currentUser.value
                            if (user != null) {
                                val updated = user.copy(currentLat = location.latitude, currentLng = location.longitude)
                                repository.updateUser(updated)
                                _currentUser.value = updated
                            }
                        }
                        toastMessage = "🛰️ GPS Sincronizado: (${String.format("%.5f", userLatitude)}, ${String.format("%.5f", userLongitude)})"
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, android.os.Looper.getMainLooper())
            } catch (e: SecurityException) {
                toastMessage = "❌ Error al conectar al servicio de ubicación."
            } catch (e: Exception) {
                toastMessage = "❌ No se pudo recibir señal del GPS real."
            }
        }
    }

    fun registerNewUser(name: String, email: String, avatar: String, city: String) {
        viewModelScope.launch {
            val newUser = UserEntity(
                name = name,
                email = email,
                role = AppRole.VECINO,
                avatar = avatar,
                city = city,
                points = 0
            )
            val newId = db.userDao().insertUser(newUser).toInt()
            val freshUser = db.userDao().getUserByIdSync(newId)
            if (freshUser != null) {
                _currentUser.value = freshUser
                userLatitude = freshUser.currentLat
                userLongitude = freshUser.currentLng
                toastMessage = "🎉 ¡Vecino registrado con éxito en 'Usuarios'!"
            }
        }
    }

    fun loginWithGoogle(email: String, name: String) {
        viewModelScope.launch {
            val usersList = db.userDao().getAllUsers().firstOrNull() ?: emptyList()
            val existing = usersList.find { it.email.equals(email, ignoreCase = true) }
            if (existing != null) {
                _currentUser.value = existing
                userLatitude = existing.currentLat
                userLongitude = existing.currentLng
                toastMessage = "👋 ¡Ingreso con Google exitoso! Bienvenido ${existing.name}."
            } else {
                val newUser = UserEntity(
                    name = name,
                    email = email,
                    role = AppRole.VECINO,
                    avatar = "🦊",
                    city = "Lima Central",
                    points = 20
                )
                val newId = db.userDao().insertUser(newUser).toInt()
                val freshUser = db.userDao().getUserByIdSync(newId)
                if (freshUser != null) {
                    _currentUser.value = freshUser
                    userLatitude = freshUser.currentLat
                    userLongitude = freshUser.currentLng
                    toastMessage = "🔥 ¡Cuenta Google sincronizada con 'Usuarios'!"
                }
            }
        }
    }

    // Process a scanned QR code with GPS validation
    fun scanQrCode(qrHash: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val actualHash = if (qrHash.trim().startsWith("rutinlocal://comercio/")) {
                val mercIdStr = qrHash.trim().substringAfter("rutinlocal://comercio/")
                val mercId = mercIdStr.toIntOrNull()
                if (mercId != null) {
                    val m = db.merchantDao().getMerchantByIdSync(mercId)
                    m?.codeHash ?: qrHash
                } else {
                    qrHash
                }
            } else {
                qrHash
            }

            val oldPoints = user.points
            val result = repository.processQrStampScan(
                userId = user.id,
                qrHash = actualHash,
                userLat = simulatedLat,
                userLng = simulatedLng
            )
            scanResult = result

            // Trigger points reload on UI by slightly refreshing currentUser
            val freshUser = db.userDao().getUserByIdSync(user.id)
            if (freshUser != null) {
                _currentUser.value = freshUser

                if (result.success) {
                    val newPoints = freshUser.points
                    
                    // Trigger dynamic local notification 2 minutes after successful scan
                    viewModelScope.launch {
                        val mName = if (result.merchantName.isNotBlank()) result.merchantName else "Comercio Local"
                        val ptsGained = if (result.pointsEarned > 0) result.pointsEarned else 10
                        val msg = "¡Buenísimo! Sumaste $ptsGained puntos en $mName. Estás cerca, tenés $newPoints puntos acumulados, ¡seguí avanzando!"
                        
                        // Log schedule to Toast for prompt pilot user feedback
                        toastMessage = "🔔 ¡Escaneo exitoso! Recibirás una notificación dinámica en 2 minutos."
                        
                        delay(120000L) // Wait exactly 2 minutes
                        
                        // Trigger local system notification and in-app notice
                        sendLocalSystemNotification(getApplication(), msg)
                        activePushNotification = msg
                    }

                    val reachedStation = when {
                        oldPoints < 100 && newPoints >= 100 -> 1
                        oldPoints < 180 && newPoints >= 180 -> 2
                        oldPoints < 240 && newPoints >= 240 -> 3
                        oldPoints < 280 && newPoints >= 280 -> 4
                        else -> {
                            var station = 5
                            var triggered = false
                            while (true) {
                                val t = 280 + (station - 4) * 40
                                if (oldPoints < t && newPoints >= t) {
                                    triggered = true
                                    break
                                }
                                if (t > newPoints) break
                                station++
                            }
                            if (triggered) station else null
                        }
                    }
                    if (reachedStation != null) {
                        triggerMilestone(reachedStation)
                    }
                }
            }
        }
    }

    // Process Coupon/Reward claiming
    fun redeemReward(userId: Int, campaignId: Int) {
        viewModelScope.launch {
            val result = repository.processCouponRedemption(userId, campaignId)
            redeemResult = result

            // Refresh user points
            val user = _currentUser.value
            if (user != null && user.id == userId) {
                val freshUser = db.userDao().getUserByIdSync(userId)
                if (freshUser != null) {
                    _currentUser.value = freshUser
                }
            } else if (user != null) {
                // If the logged-in user is a merchant testing client's claim
                val freshUser = db.userDao().getUserByIdSync(_currentUser.value!!.id)
                if (freshUser != null) {
                    _currentUser.value = freshUser
                }
            }
        }
    }

    // Create a new campaign (Merchant action)
    fun createCampaign(merchantId: Int, title: String, pointsCost: Int, category: String) {
        viewModelScope.launch {
            repository.insertCampaign(
                CampaignEntity(
                    merchantId = merchantId,
                    title = title,
                    costPoints = pointsCost,
                    active = true,
                    category = category
                )
            )
            toastMessage = "¡Campaña '$title' creada exitosamente!"
        }
    }

    // Add new merchant partner (Admin Action)
    fun registerMerchant(name: String, codeHash: String, lat: Double, lng: Double, category: String, address: String) {
        viewModelScope.launch {
            repository.insertMerchant(
                MerchantEntity(
                    name = name,
                    codeHash = codeHash,
                    lat = lat,
                    lng = lng,
                    category = category,
                    address = address
                )
            )
            toastMessage = "¡Socio '$name' registrado y QR generado!"
        }
    }

    // Add new custom sequence route (Admin Action)
    fun registerRoute(title: String, description: String, category: String, merchantIds: List<Int>) {
        viewModelScope.launch {
            val routeId = repository.insertRoute(
                RouteEntity(
                    title = title,
                    description = description,
                    category = category
                )
            ).toInt()

            merchantIds.forEach { merchantId ->
                repository.insertRouteMerchantCrossRef(routeId, merchantId)
            }
            toastMessage = "¡Nueva ruta temática '$title' configurada!"
        }
    }

    fun dismissScanResult() {
        scanResult = null
    }

    fun dismissRedeemResult() {
        redeemResult = null
    }

    fun consumeToast() {
        toastMessage = null
    }

    // Clean distance helper for view layers
    fun getDistanceToMerchant(merchant: MerchantEntity): Double {
        return repository.calculateDistanceInMeters(simulatedLat, simulatedLng, merchant.lat, merchant.lng)
    }

    // Dynamic Fraud Detection algorithm (Control de Auditoría)
    fun getSuspiciousUsers(transactions: List<TransactionEntity>): Map<Int, Int> {
        val suspiciousMap = mutableMapOf<Int, Int>() // userId -> merchantId
        
        // Group transactions by userId
        val userTxGroup = transactions.filter { it.type == "STAMP" && it.pointsChange > 0 }.groupBy { it.userId }
        
        userTxGroup.forEach { (userId, txs) ->
            // Let's group by calendar day string: "yyyy-MM-dd"
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            // Group transactions by date
            val dailyGroups = txs.groupBy { sdf.format(java.util.Date(it.timestamp)) }
            
            // Sort dates
            val sortedDates = dailyGroups.keys.sorted()
            if (sortedDates.size >= 5) {
                // Parse dates to day epochs so we can check consecutive runs
                val epochDays = sortedDates.mapNotNull { dateStr ->
                    try {
                        val date = sdf.parse(dateStr)
                        date?.time?.div(24 * 60 * 60 * 1000L)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (epochDays.size >= 5) {
                    // Let's find any consecutive run of length >= 5
                    var currentStreak = 1
                    var longestStreak = 1
                    var streakStartIndex = 0
                    var bestStartIndex = 0
                    
                    for (i in 1 until epochDays.size) {
                        if (epochDays[i] == epochDays[i - 1] + 1) {
                            currentStreak++
                            if (currentStreak > longestStreak) {
                                longestStreak = currentStreak
                                bestStartIndex = streakStartIndex
                            }
                        } else {
                            currentStreak = 1
                            streakStartIndex = i
                        }
                    }
                    
                    if (longestStreak >= 5) {
                        // Let's get the dates in this streak
                        val streakDates = sortedDates.subList(bestStartIndex, bestStartIndex + longestStreak)
                        // Let's get all transactions in this streak
                        val streakTxs = streakDates.flatMap { dailyGroups[it] ?: emptyList() }
                        
                        // Check if all of these transactions belong to a single merchant
                        val uniqueMerchantIds = streakTxs.map { it.merchantId }.distinct()
                        if (uniqueMerchantIds.size == 1) {
                            // User checked in exclusively at this unique merchant!
                            val merchantId = uniqueMerchantIds.first()
                            suspiciousMap[userId] = merchantId
                        }
                    }
                }
            }
        }
        return suspiciousMap
    }

    // Force simulation of consecutive days scanning at a single merchant
    fun simulateConsecutiveFraud(testUserId: Int, targetMerchantId: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            
            // Write 6 consecutive daily scans over past 6 days to room
            for (i in 0..5) {
                val timestamp = now - (i * oneDayMs)
                db.transactionDao().insertTransaction(
                    TransactionEntity(
                        userId = testUserId,
                        merchantId = targetMerchantId,
                        pointsChange = 3,
                        type = "STAMP",
                        timestamp = timestamp,
                        verifiedGps = true
                    )
                )
            }
            toastMessage = "¡Simulación de Autoconsumo exitosa! Se registraron 6 días seguidos de escaneos exclusivos para el usuario en el local."
        }
    }

    // --- INTEGRACIÓN FIREBASE SERVICES ---

    /**
     * Registro manual en Firebase Auth y guardado en la colección 'Usuarios' de Firestore.
     */
    fun registrarManualFirebase(email: String, password: String, nombre: String, rol: AppRole) {
        viewModelScope.launch {
            val result = authService.registrarManual(email, password, nombre, rol)
            result.onSuccess { user ->
                _currentUser.value = user
                userLatitude = user.currentLat
                userLongitude = user.currentLng
                toastMessage = "🎉 ¡Vecino registrado exitosamente en Firebase Auth y Firestore!"
            }.onFailure { exception ->
                toastMessage = "❌ Error al registrar en Firebase: ${exception.message}"
            }
        }
    }

    /**
     * Login manual en Firebase Auth y lectura de datos de la colección 'Usuarios' de Firestore.
     */
    fun loginManualFirebase(email: String, password: String) {
        viewModelScope.launch {
            val result = authService.loginManual(email, password)
            result.onSuccess { user ->
                _currentUser.value = user
                userLatitude = user.currentLat
                userLongitude = user.currentLng
                toastMessage = "👋 ¡Ingreso exitoso! Bienvenido ${user.name}."
            }.onFailure { exception ->
                toastMessage = "❌ Error de ingreso manual en Firebase: ${exception.message}"
            }
        }
    }

    /**
     * Ingreso con Google en Firebase Auth y sincronización con la colección 'Usuarios' de Firestore.
     */
    fun ingresarConGoogleFirebase(idToken: String) {
        viewModelScope.launch {
            val result = authService.ingresarConGoogle(idToken)
            result.onSuccess { user ->
                _currentUser.value = user
                userLatitude = user.currentLat
                userLongitude = user.currentLng
                toastMessage = "👋 ¡Ingreso con Google exitoso! Bienvenido ${user.name}."
            }.onFailure { exception ->
                toastMessage = "❌ Error al ingresar con Google en Firebase: ${exception.message}"
            }
        }
    }

    /**
     * Procesa el escaneo del QR validando GPS actual y aplicando escala decreciente (10, 3, 1, 1, 0).
     */
    fun procesarEscaneoFirebase(idComercio: String) {
        viewModelScope.launch {
            val result = scannerService.procesarEscaneo(idComercio, simulatedLat, simulatedLng)
            result.onSuccess { scanResultData ->
                if (scanResultData.success) {
                    toastMessage = "🎉 Sello verificado en Firebase: ${scanResultData.message}"
                    
                    // Actualizar el perfil del usuario local si coincide con el activo
                    val user = _currentUser.value
                    if (user != null) {
                        val updated = user.copy(points = user.points + scanResultData.pointsEarned)
                        _currentUser.value = updated
                    }
                } else {
                    toastMessage = "❌ Falló el escaneo en Firebase: ${scanResultData.message}"
                }
            }.onFailure { exception ->
                toastMessage = "❌ Error al procesar escaneo en Firebase: ${exception.message}"
            }
        }
    }

    /**
     * Canjear premio descontando los puntos del hito actual, guardando el cupón activo,
     * y reseteando el progreso.
     */
    fun canjearPremioFirebase(hitoPoints: Int) {
        viewModelScope.launch {
            val result = scannerService.canjearPremio(hitoPoints)
            result.onSuccess { success ->
                if (success) {
                    toastMessage = "🎟️ ¡Premio canjeado en Firebase! Cupón activo guardado."
                    
                    // Actualizar estado del usuario local
                    val user = _currentUser.value
                    if (user != null) {
                        val updated = user.copy(points = user.points - hitoPoints)
                        _currentUser.value = updated
                    }
                }
            }.onFailure { exception ->
                toastMessage = "❌ Error al canjear premio en Firebase: ${exception.message}"
            }
        }
    }

    fun logout() {
        authService.cerrarSesion()
        _currentUser.value = null
    }
}

data class StarMilestone(
    val stationNumber: Int,
    val couponName: String,
    val couponRewardDesc: String
)
