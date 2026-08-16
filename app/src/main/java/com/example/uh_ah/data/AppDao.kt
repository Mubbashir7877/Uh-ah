package com.example.uh_ah.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM delete_list ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<AppEntity>>

    @Query("SELECT * FROM delete_list WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackageName(packageName: String): AppEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM delete_list WHERE packageName = :packageName)")
    suspend fun exists(packageName: String): Boolean

    @Query("SELECT packageName FROM delete_list WHERE isEnabled = 1")
    suspend fun getEnabledPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AppEntity)

    @Update
    suspend fun update(entity: AppEntity)

    @Delete
    suspend fun delete(entity: AppEntity)

    @Query("UPDATE delete_list SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
