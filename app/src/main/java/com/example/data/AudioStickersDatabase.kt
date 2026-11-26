package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SoundStickerEntity::class], version = 1, exportSchema = false)
abstract class AudioStickersDatabase : RoomDatabase() {
  abstract fun soundStickerDao(): SoundStickerDao

  companion object {
    @Volatile private var INSTANCE: AudioStickersDatabase? = null

    fun getInstance(context: Context): AudioStickersDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                AudioStickersDatabase::class.java,
                "audiostickers_room.db",
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
