package com.example.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        MerchantEntity::class,
        CampaignEntity::class,
        TransactionEntity::class,
        RouteEntity::class,
        RouteMerchantCrossRef::class,
        UserRouteStatusEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(AppRoleConverter::class)
abstract class RutinDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun merchantDao(): MerchantDao
    abstract fun campaignDao(): CampaignDao
    abstract fun transactionDao(): TransactionDao
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var INSTANCE: RutinDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): RutinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RutinDatabase::class.java,
                    "rutin_local_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        private suspend fun populateDatabase(db: RutinDatabase) {
            val userDao = db.userDao()
            val merchantDao = db.merchantDao()
            val campaignDao = db.campaignDao()
            val routeDao = db.routeDao()

            // Pre-populate Users
            val user1Id = userDao.insertUser(UserEntity(name = "Carlos Vecino (Cliente)", role = AppRole.VECINO, points = 120))
            val user2Id = userDao.insertUser(UserEntity(name = "Café de Marita (Mercader)", role = AppRole.COMERCIO, points = 0))
            val user3Id = userDao.insertUser(UserEntity(name = "Admin Principal", role = AppRole.ADMIN, points = 0))

            // Pre-populate Merchants
            val m1 = MerchantEntity(
                name = "Café de Marita",
                codeHash = "cafe_marita_hash",
                lat = -12.046374,  // Default Lima coords
                lng = -77.042793,
                category = "Hostelería / Café",
                address = "Calle Belén 105, Lima"
            )
            val m2 = MerchantEntity(
                name = "Antojos del Sur",
                codeHash = "antojos_sur_hash",
                lat = -12.046830,  // ~50m apart from cafe_marita inside tolerance
                lng = -77.043120,
                category = "Gastronomía / Postres",
                address = "Pasaje Carrión 44, Lima"
            )
            val m3 = MerchantEntity(
                name = "Artesanías Andinas",
                codeHash = "artesanias_andinas_hash",
                lat = -12.050500,  // ~500m apart, outside default range to trigger limits!
                lng = -77.040100,
                category = "Artesanías / Souvenirs",
                address = "Av. Tacna 380, Lima"
            )

            val m1Id = merchantDao.insertMerchant(m1).toInt()
            val m2Id = merchantDao.insertMerchant(m2).toInt()
            val m3Id = merchantDao.insertMerchant(m3).toInt()

            // Pre-populate Campaigns (Rewards)
            campaignDao.insertCampaign(CampaignEntity(merchantId = m1Id, title = "Café Expreso Gratis", costPoints = 30, category = "Expreso"))
            campaignDao.insertCampaign(CampaignEntity(merchantId = m1Id, title = "Porción de Torta Tres Leches", costPoints = 60, category = "Postre"))
            campaignDao.insertCampaign(CampaignEntity(merchantId = m2Id, title = "15% Descuento Almuerzo", costPoints = 80, category = "Descuento"))
            campaignDao.insertCampaign(CampaignEntity(merchantId = m3Id, title = "Llavero de Llama Artesanal", costPoints = 25, category = "Regalo"))

            // Pre-populate Routes
            val r1Id = routeDao.insertRoute(
                RouteEntity(
                    title = "Ruta Cafetera del Centro",
                    description = "Visita los mejores cafés tradicionales y acumula puntos dobles.",
                    category = "Café"
                )
            ).toInt()

            val r2Id = routeDao.insertRoute(
                RouteEntity(
                    title = "Ruta Gastronomía y Arte",
                    description = "Un viaje de sabores andinos combinados con el más fino arte manual.",
                    category = "Cultura"
                )
            ).toInt()

            // Link Route to Merchants
            routeDao.insertRouteMerchantCrossRef(RouteMerchantCrossRef(r1Id, m1Id))
            routeDao.insertRouteMerchantCrossRef(RouteMerchantCrossRef(r1Id, m2Id))
            routeDao.insertRouteMerchantCrossRef(RouteMerchantCrossRef(r2Id, m2Id))
            routeDao.insertRouteMerchantCrossRef(RouteMerchantCrossRef(r2Id, m3Id))

            // Initialize route completion states for Carlos
            routeDao.insertUserRouteStatus(UserRouteStatusEntity(userId = user1Id.toInt(), routeId = r1Id, completed = false, progressCount = 0))
            routeDao.insertUserRouteStatus(UserRouteStatusEntity(userId = user1Id.toInt(), routeId = r2Id, completed = false, progressCount = 0))
        }
    }
}

class AppRoleConverter {
    @TypeConverter
    fun fromRole(role: AppRole): String = role.name

    @TypeConverter
    fun toRole(value: String): AppRole = AppRole.valueOf(value)
}
