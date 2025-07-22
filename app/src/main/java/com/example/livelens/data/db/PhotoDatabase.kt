package com.example.livelens.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PhotoEntry::class], version = 2)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var INSTANCE: PhotoDatabase? = null
        /**
         * Returns a singleton instance of the PhotoDatabase.
         * If the instance is null, it creates a new instance using Room's database builder.
         * The database is built with a destructive migration strategy to handle schema changes.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "photo_db"
                )
                    .fallbackToDestructiveMigration() // Add this line
                    .build().also { INSTANCE = it }
            }
        }
    }
}
