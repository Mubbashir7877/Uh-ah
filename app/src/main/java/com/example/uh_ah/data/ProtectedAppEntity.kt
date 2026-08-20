package com.example.uh_ah.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One installed application whose uninstall confirmation should be dismissed. */
@Entity(tableName = "protected_list")
data class ProtectedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean,
    val dateAdded: Long = System.currentTimeMillis()
)
