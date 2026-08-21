package org.fossify.musicplayer.update

import org.json.JSONObject

/** A release published as described by `version.json` at the repo root. */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
) {
    fun toJson(): String = JSONObject().apply {
        put("versionCode", versionCode)
        put("versionName", versionName)
        put("apkUrl", apkUrl)
        put("notes", notes)
    }.toString()

    companion object {
        fun fromJson(json: String): AppUpdate? = runCatching {
            val obj = JSONObject(json)
            AppUpdate(
                versionCode = obj.getInt("versionCode"),
                versionName = obj.getString("versionName"),
                apkUrl = obj.getString("apkUrl"),
                notes = obj.optString("notes")
            )
        }.getOrNull()
    }
}
