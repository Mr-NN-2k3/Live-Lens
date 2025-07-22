package com.example.livelens.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_entries")
data class PhotoEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
//    val uri: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val filePath: String? = null
)
