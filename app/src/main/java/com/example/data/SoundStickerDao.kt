package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundStickerDao {
  @Query("SELECT * FROM sound_stickers ORDER BY createdAt DESC")
  fun getAllStickers(): Flow<List<SoundStickerEntity>>

  @Query("SELECT * FROM sound_stickers WHERE id = :id LIMIT 1")
  suspend fun getStickerById(id: String): SoundStickerEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSticker(sticker: SoundStickerEntity)

  @Update
  suspend fun updateSticker(sticker: SoundStickerEntity)

  @Query("UPDATE sound_stickers SET title = :newTitle WHERE id = :id")
  suspend fun renameSticker(id: String, newTitle: String)

  @Query("DELETE FROM sound_stickers WHERE id = :id")
  suspend fun deleteStickerById(id: String)
}
