package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults.Companion.gson
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileReader

@JsonAdapter(CaptureResult.JsonAdapter::class)
sealed interface CaptureResult {
  val timestampNs: Long
  val compareFile: File?
  val actualFile: File?
  val goldenFile: File?

  data class Recorded(
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long,
  ) : CaptureResult {
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null
  }

  data class Added(
    @SerializedName("compare_file_path")
    override val compareFile: File,
    @SerializedName("actual_file_path")
    override val actualFile: File,
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long,
  ) : CaptureResult

  data class Changed(
    @SerializedName("compare_file_path")
    override val compareFile: File,
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("actual_file_path")
    override val actualFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long
  ) : CaptureResult

  data class Unchanged(
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long
  ) : CaptureResult {
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null
  }

  companion object {
    fun fromJsonFile(filePath: String): CaptureResult {
      return gson.fromJson(FileReader(filePath), CaptureResult::class.java)
    }
  }

  object JsonAdapter : com.google.gson.JsonSerializer<CaptureResult>,
    com.google.gson.JsonDeserializer<CaptureResult> {
    override fun serialize(
      src: CaptureResult,
      typeOfSrc: java.lang.reflect.Type,
      context: com.google.gson.JsonSerializationContext
    ): com.google.gson.JsonElement {
      return when (src) {
        is Recorded -> context.serialize(src, Recorded::class.java)
        is Changed -> context.serialize(src, Changed::class.java)
        is Unchanged -> context.serialize(src, Unchanged::class.java)
        is Added -> context.serialize(src, Added::class.java)
      }.apply {
        this.asJsonObject.addProperty(
          "type", when (src) {
            is Recorded -> "recorded"
            is Changed -> "changed"
            is Unchanged -> "unchanged"
            is Added -> "added"
          }
        )
      }
    }

    override fun deserialize(
      json: com.google.gson.JsonElement,
      typeOfT: java.lang.reflect.Type,
      context: com.google.gson.JsonDeserializationContext
    ): CaptureResult? {
      val type = requireNotNull(json.asJsonObject.get("type")?.asString)
      return when (type) {
        "recorded" -> context.deserialize(json, Recorded::class.java)
        "changed" -> context.deserialize(json, Changed::class.java)
        "unchanged" -> context.deserialize(json, Unchanged::class.java)
        "added" -> context.deserialize(json, Added::class.java)
        else -> throw IllegalArgumentException("Unknown type $type")
      }
    }
  }
}
