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
    val mapCityCode: String? = null,
    val mapFillColor: String = "#A9BFAE",
    val mapFillMode: String = "color",
    val provinceCode: String? = null,
    val city: String = "",
    // 封面：手账默认用第一张照片；选了别的照片就记在这里（必须是 photos 里的一张）。
    val coverPhoto: String? = null,
)

/** 封面必须是自己的照片；旧数据没有 coverPhoto 时退回第一张。 */
internal fun journalCover(journal: Journal): String? =
    journal.coverPhoto?.takeIf { it in journal.photos } ?: journal.photos.firstOrNull()

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
) {
    override fun toString(): String = "AiConfig(baseUrl=$baseUrl, apiKey=[redacted], model=$model, spiritName=$spiritName, consent=$consent)"
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
