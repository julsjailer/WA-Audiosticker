package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_stickers")
data class SoundStickerEntity(
  @PrimaryKey val id: String,
  val title: String,
  val durationSec: Double,
  val createdAt: Long,
  val visualFilePath: String,
  val audioFilePath: String,
)
