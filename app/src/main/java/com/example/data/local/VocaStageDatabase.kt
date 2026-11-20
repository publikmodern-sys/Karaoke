package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [KaraokeTrackEntity::class, VocalMixEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VocaStageDatabase : RoomDatabase() {
    abstract fun karaokeTrackDao(): KaraokeTrackDao
    abstract fun vocalMixDao(): VocalMixDao

    companion object {
        @Volatile
        private var INSTANCE: VocaStageDatabase? = null

        fun getInstance(context: Context): VocaStageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VocaStageDatabase::class.java,
                    "vocastage.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
