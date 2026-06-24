package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: Int): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserByIdSync(id: Int): UserEntity?

    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET points = :points WHERE id = :userId")
    suspend fun updatePoints(userId: Int, points: Int)
}

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants WHERE id = :id")
    fun getMerchantById(id: Int): Flow<MerchantEntity?>

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getMerchantByIdSync(id: Int): MerchantEntity?

    @Query("SELECT * FROM merchants ORDER BY name ASC")
    fun getAllMerchants(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE codeHash = :hash LIMIT 1")
    suspend fun getMerchantByHash(hash: String): MerchantEntity?

    @Query("SELECT m.* FROM merchants m INNER JOIN route_merchant_cross_ref r ON m.id = r.merchantId WHERE r.routeId = :routeId")
    fun getMerchantsForRoute(routeId: Int): Flow<List<MerchantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchant(merchant: MerchantEntity): Long
}

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns WHERE merchantId = :merchantId ORDER BY timestamp DESC")
    fun getCampaignsByMerchant(merchantId: Int): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns ORDER BY timestamp DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: CampaignEntity): Long

    @Update
    suspend fun updateCampaign(campaign: CampaignEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTransactionsForUser(userId: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE merchantId = :merchantId ORDER BY timestamp DESC")
    fun getTransactionsForMerchant(merchantId: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionByIdSync(id: Int): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY id ASC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteMerchantCrossRef(crossRef: RouteMerchantCrossRef)

    @Query("SELECT * FROM user_route_status WHERE userId = :userId")
    fun getUserRouteStatuses(userId: Int): Flow<List<UserRouteStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserRouteStatus(status: UserRouteStatusEntity)
}
