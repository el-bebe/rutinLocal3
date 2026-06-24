package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

/**
 * Servicio de Autenticación para RutinLocal conectado a Firebase.
 */
class AuthService(
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
     * Registro manual con correo y contraseña.
     */
    suspend fun registrarManual(email: String, password: String, nombre: String, rol: AppRole): Result<UserEntity> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("No se pudo crear el usuario en Firebase Auth.")
            
            // Crear el perfil del usuario en Firestore
            val userEntity = UserEntity(
                id = 0, // Id local de Room temporal
                name = nombre,
                role = rol,
                points = 0,
                email = email
            )
            
            guardarUsuarioEnFirestore(firebaseUser.uid, userEntity)
            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Login manual con correo y contraseña.
     */
    suspend fun loginManual(email: String, password: String): Result<UserEntity> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("No se pudo iniciar sesión.")
            
            // Leer el perfil de Firestore para determinar su rol y datos
            val userEntity = obtenerUsuarioDeFirestore(firebaseUser.uid)
            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ingresar con Google (utilizando un token de Google obtenido en el flujo de UI).
     */
    suspend fun ingresarConGoogle(idToken: String): Result<UserEntity> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("No se pudo autenticar con Google.")
            
            // Buscar si ya existe el documento del usuario en la colección 'Usuarios'
            return try {
                val userEntity = obtenerUsuarioDeFirestore(firebaseUser.uid)
                Result.success(userEntity)
            } catch (notFound: Exception) {
                // Si no existe, lo creamos como Vecino por defecto
                val nuevoUsuario = UserEntity(
                    id = 0,
                    name = firebaseUser.displayName ?: "Vecino de Barrio",
                    role = AppRole.VECINO,
                    points = 20, // Puntos de bienvenida
                    email = firebaseUser.email ?: ""
                )
                guardarUsuarioEnFirestore(firebaseUser.uid, nuevoUsuario)
                Result.success(nuevoUsuario)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Guarda el documento del usuario en la colección 'Usuarios' por su UID.
     */
    suspend fun guardarUsuarioEnFirestore(uid: String, user: UserEntity) {
        val userMap = hashMapOf(
            "uid" to uid,
            "name" to user.name,
            "email" to user.email,
            "rol" to user.role.name.lowercase(), // "vecino", "comercio", "admin"
            "puntos_acumulados" to user.points,
            "currentLat" to user.currentLat,
            "currentLng" to user.currentLng,
            "avatar" to user.avatar,
            "city" to user.city
        )
        firestore.collection("Usuarios").document(uid).set(userMap).await()
    }

    /**
     * Obtiene el documento del usuario en la colección 'Usuarios' por su UID.
     */
    suspend fun obtenerUsuarioDeFirestore(uid: String): UserEntity {
        val doc = firestore.collection("Usuarios").document(uid).get().await()
        if (!doc.exists()) {
            throw Exception("El perfil del usuario no existe en la colección 'Usuarios'.")
        }
        
        val name = doc.getString("name") ?: "Vecino"
        val email = doc.getString("email") ?: ""
        val rolStr = doc.getString("rol")?.uppercase() ?: "VECINO"
        
        // Mapear rol de Firestore ("vecino", "comercio", "admin") a AppRole
        val role = try {
            AppRole.valueOf(rolStr)
        } catch (e: IllegalArgumentException) {
            AppRole.VECINO
        }
        
        val puntos = doc.getLong("puntos_acumulados")?.toInt() ?: 0
        val currentLat = doc.getDouble("currentLat") ?: -12.046374
        val currentLng = doc.getDouble("currentLng") ?: -77.042793
        val avatar = doc.getString("avatar") ?: "👤"
        val city = doc.getString("city") ?: "Lima"
        
        return UserEntity(
            id = uid.hashCode(), // Id local único para uso de Room
            name = name,
            role = role,
            points = puntos,
            currentLat = currentLat,
            currentLng = currentLng,
            avatar = avatar,
            city = city,
            email = email
        )
    }

    /**
     * Cerrar sesión en Firebase.
     */
    fun cerrarSesion() {
        auth.signOut()
    }
}
