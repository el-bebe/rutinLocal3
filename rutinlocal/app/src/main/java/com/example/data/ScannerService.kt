package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

/**
 * Servicio de Puntos y QR para RutinLocal conectado a Cloud Firestore.
 */
class ScannerService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // Helper para convertir Task de Google Play Services en funciones suspendibles de coroutines
    private suspend fun <T> Task<T>.await(): T {
        if (isComplete) {
            val e = exception
            if (e != null) throw e
            return result
        }
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                val e = task.exception
                if (e != null) {
                    cont.resumeWithException(e)
                } else {
                    cont.resume(task.result)
                }
            }
        }
    }

    /**
     * Resultado de un proceso de escaneo.
     */
    data class ScanResult(
        val success: Boolean,
        val message: String,
        val pointsEarned: Int = 0,
        val merchantName: String = ""
    )

    /**
     * Procesa el escaneo del QR utilizando la ubicación del GPS del usuario.
     */
    suspend fun procesarEscaneo(idComercio: String): Result<ScanResult> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado en Firebase."))
        
        // Obtener la ubicación del usuario registrada en Firestore como fallback
        return try {
            val userDoc = firestore.collection("Usuarios").document(uid).get().await()
            val userLat = userDoc.getDouble("currentLat") ?: -12.046374
            val userLng = userDoc.getDouble("currentLng") ?: -77.042793
            procesarEscaneo(idComercio, userLat, userLng)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Procesa el escaneo del QR validando coordenadas GPS actuales del usuario contra el comercio.
     */
    suspend fun procesarEscaneo(idComercio: String, userLat: Double, userLng: Double): Result<ScanResult> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado en Firebase."))
        val now = System.currentTimeMillis()

        return try {
            // 1. Obtener datos del comercio en Firestore
            val docComercio = firestore.collection("Comercios").document(idComercio).get().await()
            if (!docComercio.exists()) {
                return Result.success(ScanResult(false, "El comercio especificado no existe en la base de datos."))
            }

            val merchantName = docComercio.getString("nombre") ?: docComercio.getString("name") ?: "Comercio Local"
            
            // Determinar coordenadas de manera flexible (GeoPoint o coordenadas individuales)
            val coordenadas = docComercio.getGeoPoint("coordenadas")
            val merchantLat: Double
            val merchantLng: Double
            
            if (coordenadas != null) {
                merchantLat = coordenadas.latitude
                merchantLng = coordenadas.longitude
            } else {
                merchantLat = docComercio.getDouble("lat") ?: docComercio.getDouble("latitud") ?: docComercio.getDouble("currentLat") ?: 0.0
                merchantLng = docComercio.getDouble("lng") ?: docComercio.getDouble("longitud") ?: docComercio.getDouble("currentLng") ?: 0.0
            }

            // 2. Verificar la ubicación actual con tolerancia máxima de 50 metros
            val distance = calculateDistanceInMeters(userLat, userLng, merchantLat, merchantLng)
            val tolerance = 50.0 // 50 metros de tolerancia

            if (distance > tolerance) {
                return Result.success(
                    ScanResult(
                        success = false,
                        message = "Estás demasiado lejos de $merchantName para registrar tu visita. Distancia: ${distance.toInt()}m (Tolerancia máxima: ${tolerance.toInt()}m).",
                        merchantName = merchantName
                    )
                )
            }

            // 3. Consultar la colección 'Historial_Escaneos' para aplicar reglas de negocio
            val escaneosSnapshot = firestore.collection("Historial_Escaneos")
                .whereEqualTo("userId", uid)
                .whereEqualTo("merchantId", idComercio)
                .get()
                .await()

            val allScans = escaneosSnapshot.documents.mapNotNull { doc ->
                doc.getLong("timestamp")
            }

            val stampsToday = allScans.filter { isSameDay(it, now) }
            val hasHistoricScans = allScans.isNotEmpty()

            var pointsGained = 0
            var note = ""

            if (!hasHistoricScans) {
                // Primer escaneo histórico en este comercio: otorga 10 puntos
                pointsGained = 10
                note = "¡Bienvenido a $merchantName! Es tu primera visita histórica registrada, ¡ganas +10 puntos!"
            } else {
                val hasScannedInPastDays = allScans.any { !isSameDay(it, now) }
                
                if (!hasScannedInPastDays) {
                    // El primer escaneo histórico fue hoy. Siguientes escaneos hoy aplican escala decreciente (3, 1, 1, 0)
                    val todayCount = stampsToday.size
                    when (todayCount) {
                        1 -> { // Segundo escaneo hoy
                            pointsGained = 3
                            note = "Tu segundo escaneo hoy en $merchantName te otorga +3 puntos de fidelidad."
                        }
                        2 -> { // Tercer escaneo hoy
                            pointsGained = 1
                            note = "Tu tercer escaneo hoy en $merchantName te otorga +1 punto."
                        }
                        3 -> { // Cuarto escaneo hoy
                            pointsGained = 1
                            note = "Tu cuarto escaneo hoy en $merchantName te otorga +1 punto."
                        }
                        else -> { // Quinto o más hoy
                            pointsGained = 0
                            note = "⚠️ ALERTA: Has alcanzado el límite de escaneos diarios para $merchantName. Sumas 0 puntos."
                        }
                    }
                } else {
                    // Reinicio diario para visitas en días posteriores:
                    // Primer escaneo del día otorga 3 puntos, los dos siguientes 1 punto, luego 0.
                    val todayCount = stampsToday.size
                    when (todayCount) {
                        0 -> { // Primer escaneo de un nuevo día
                            pointsGained = 3
                            note = "¡Nueva visita hoy en $merchantName! Sumas +3 puntos de fidelidad."
                        }
                        1 -> { // Segundo escaneo hoy
                            pointsGained = 1
                            note = "Segundo escaneo hoy en $merchantName: sumas +1 punto."
                        }
                        2 -> { // Tercer escaneo hoy
                            pointsGained = 1
                            note = "Tercer escaneo hoy en $merchantName: sumas +1 punto."
                        }
                        else -> { // Cuarto o más hoy
                            pointsGained = 0
                            note = "⚠️ ALERTA: Has alcanzado el límite diario de reinicio en $merchantName hoy (0 puntos)."
                        }
                    }
                }
            }

            // 4. Actualizar 'puntos_acumulados' del usuario en la colección 'Usuarios'
            val userDocRef = firestore.collection("Usuarios").document(uid)
            val userDoc = userDocRef.get().await()
            val currentPoints = userDoc.getLong("puntos_acumulados")?.toInt() ?: 0
            val updatedPoints = currentPoints + pointsGained
            
            userDocRef.update("puntos_acumulados", updatedPoints).await()

            // 5. Registrar el escaneo en el historial ('Historial_Escaneos')
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val scanRecord = hashMapOf(
                "userId" to uid,
                "merchantId" to idComercio,
                "puntos_ganados" to pointsGained,
                "timestamp" to now,
                "fecha" to dateFormat.format(java.util.Date(now)),
                "verifiedGps" to true
            )
            firestore.collection("Historial_Escaneos").add(scanRecord).await()

            Result.success(
                ScanResult(
                    success = true,
                    message = note,
                    pointsEarned = pointsGained,
                    merchantName = merchantName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * LOGICA DE REINICIO POR CANJEO:
     * canjearPremio() descuente los puntos del hito actual (100, 80, 60 o 40),
     * guarde el cupón digital activo en la colección 'Cupones_Activos' y resetee el camino visual a cero.
     */
    suspend fun canjearPremio(hitoPoints: Int): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado en Firebase."))
        val now = System.currentTimeMillis()

        return try {
            val userDocRef = firestore.collection("Usuarios").document(uid)
            val userDoc = userDocRef.get().await()
            
            if (!userDoc.exists()) {
                return Result.failure(Exception("El usuario no existe en la colección 'Usuarios'."))
            }

            val currentPoints = userDoc.getLong("puntos_acumulados")?.toInt() ?: 0

            if (currentPoints < hitoPoints) {
                return Result.failure(Exception("Puntos insuficientes para canjear este premio ($hitoPoints pts requeridos)."))
            }

            // Descontar puntos del hito actual (100, 80, 60 o 40)
            val updatedPoints = currentPoints - hitoPoints
            userDocRef.update("puntos_acumulados", updatedPoints).await()

            // Guardar el cupón digital activo en la colección 'Cupones_Activos'
            val couponRecord = hashMapOf(
                "userId" to uid,
                "puntos_canjeados" to hitoPoints,
                "estado" to "ACTIVO",
                "timestamp" to now
            )
            firestore.collection("Cupones_Activos").add(couponRecord).await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper: Distancia de Haversine en metros
    private fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
