package cn.edu.traveljournal

import java.time.LocalDate
import java.util.UUID

data class Journal(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val date: String = LocalDate.now().toString(),
    val place: String = "",
    val body: String = "",
    val photos: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val photoDescription: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val wishId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapSticker: String? = null,
    val mapStickerSize: Int = 32,
    val visited: Boolean = true,
)

data class Destination(
    val id: String = "",
    val name: String = "",
    val region: String = "",
    val tags: List<String> = emptyList(),
    val summary: String = "",
    val days: String = "",
    val season: String = "",
    val sourceUrl: String = "",
    val checkedAt: String = "",
)

data class Wish(
    val id: String = UUID.randomUUID().toString(),
    val destination: Destination = Destination(),
    val query: String = "",
    val reason: String = "",
    val notes: String = "",
    val visited: Boolean = false,
    val journalId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val spiritName: String = "",
    val consent: Boolean = false,
    val searchApiKey: String = "",
) {
    override fun toString(): String = "AiConfig(baseUrl=$baseUrl, apiKey=[redacted], model=$model, spiritName=$spiritName, consent=$consent, searchApiKey=[redacted])"
}

data class SearchHit(val id: String, val reason: String = "")
data class PhotoSuggestion(
    val tags: List<String> = emptyList(),
    val description: String = "",
    val title: String = "",
)
data class Recommendation(val id: String, val reason: String = "", val caution: String = "")
data class RecommendationResult(
    val understood: String = "",
    val items: List<Recommendation> = emptyList(),
)
