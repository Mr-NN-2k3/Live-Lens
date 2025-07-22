package com.example.livelens.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(entry: PhotoEntry)

    @Query("SELECT * FROM photo_entries")
    suspend fun getAll(): List<PhotoEntry>
}
