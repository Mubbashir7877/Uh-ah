package com.example.uh_ah.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per app the user has added to the delete list.
 *
 * [packageName] is the primary key: it's what the package-installed broadcast
 * matches against, and it's also the natural duplicate-detection key (two
 * entries can't share a package even if the display name differs).
 */
@Entity(tableName = "delete_list")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconUrl: String?,
    val isEnabled: Boolean,
    val dateAdded: Long = System.currentTimeMillis()
)
