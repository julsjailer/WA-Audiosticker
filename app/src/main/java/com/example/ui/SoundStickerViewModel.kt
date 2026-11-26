package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SoundStickerEntity
import com.example.data.SoundStickerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SoundStickerViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = SoundStickerRepository(application)

  val stickers: StateFlow<List<SoundStickerEntity>> =
    repository.allStickers.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun saveSticker(
    id: String,
    title: String,
    durationSec: Double,
    createdAt: Long,
    visualBase64: String,
    audioBase64: String,
    onSaved: () -> Unit = {},
  ) {
    viewModelScope.launch {
      repository.saveSticker(id, title, durationSec, createdAt, visualBase64, audioBase64)
      onSaved()
    }
  }

  fun renameSticker(id: String, newTitle: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.renameSticker(id, newTitle)
      onDone()
    }
  }

  fun deleteSticker(id: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.deleteSticker(id)
      onDone()
    }
  }

  suspend fun buildStickersPayloadJson(list: List<SoundStickerEntity>): String {
    val arr = JSONArray()
    for (item in list) {
      val visualB64 = repository.readVisualBase64(item)
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("title", item.title)
          put("duration", item.durationSec)
          put("createdAt", item.createdAt)
          put("visualBase64", visualB64)
        }
      )
    }
    return arr.toString()
  }

  suspend fun getAudioBase64(id: String): String = repository.readAudioBase64(id)

  suspend fun getStickerById(id: String): SoundStickerEntity? = repository.getStickerById(id)
}
