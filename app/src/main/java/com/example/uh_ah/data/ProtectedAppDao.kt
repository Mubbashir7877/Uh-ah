package com.example.uh_ah.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedAppDao {
    @Query("SELECT * FROM protected_list ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<ProtectedAppEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM protected_list WHERE packageName = :packageName)")
    suspend fun exists(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProtectedAppEntity)

    @Delete
    suspend fun delete(entity: ProtectedAppEntity)

    @Query("UPDATE protected_list SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
