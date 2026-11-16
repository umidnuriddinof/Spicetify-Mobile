package com.spicetify.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

data class ThemePalette(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val background: String,
    val surface: String,
    val accent: String,
    val text: String
) {
    val isBuiltIn: Boolean
        get() = id.startsWith("builtin-")
}

object ThemePackage {
    private const val FORMAT = "spicetify-mobile-theme-pack"
    private const val VERSION = 1
    private const val MAX_PACKAGE_BYTES = 1_048_576
    const val MAX_THEMES = 100
    private val colorPattern = Regex("^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    fun encode(themes: List<ThemePalette>): String {
        val entries = JSONArray()
        themes.forEach { theme ->
            entries.put(
                JSONObject()
                    .put("id", theme.id)
                    .put("name", theme.name)
                    .put("author", theme.author)
                    .put("description", theme.description)
                    .put("background", theme.background)
                    .put("surface", theme.surface)
                    .put("accent", theme.accent)
                    .put("text", theme.text)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("themes", entries)
            .toString(2)
    }

    fun decode(input: InputStream): List<ThemePalette> {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var totalBytes = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            require(totalBytes <= MAX_PACKAGE_BYTES) { "The package is larger than 1 MB." }
            output.write(buffer, 0, count)
        }
        val root = try {
            JSONObject(output.toByteArray().toString(Charsets.UTF_8))
        } catch (error: JSONException) {
            throw IllegalArgumentException("The selected file is not a valid theme package.", error)
        }

        require(root.optString("format") == FORMAT) { "This file is not a Spicetify Mobile theme package." }
        require(root.optInt("version", -1) == VERSION) { "This theme package version is not supported." }
        val items = root.optJSONArray("themes")
            ?: throw IllegalArgumentException("The package does not contain a themes list.")
        require(items.length() in 1..MAX_THEMES) { "A package must contain between 1 and $MAX_THEMES themes." }

        return (0 until items.length()).map { index ->
            val item = items.optJSONObject(index)
                ?: throw IllegalArgumentException("Theme ${index + 1} must be a JSON object.")
            parseTheme(item, index)
        }
    }

    private fun parseTheme(item: JSONObject, index: Int): ThemePalette {
        fun requiredText(key: String, maxLength: Int): String {
            val value = item.optString(key, "").trim()
            require(value.isNotEmpty() && value.length <= maxLength) {
                "Theme ${index + 1} has an invalid '$key' field."
            }
            return value
        }

        fun requiredColor(key: String): String {
            val value = requiredText(key, 9)
            require(colorPattern.matches(value)) {
                "Theme ${index + 1} has an invalid '$key' color. Use #RRGGBB or #AARRGGBB."
            }
            return value.uppercase()
        }

        val name = requiredText("name", 80)
        val author = item.optString("author", "Unknown").trim().ifEmpty { "Unknown" }.take(80)
        val description = item.optString("description", "").trim().take(240)
        val id = item.optString("id", name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            .take(80)
            .ifEmpty { UUID.randomUUID().toString() }

        return ThemePalette(
            id = id,
            name = name,
            author = author,
            description = description,
            background = requiredColor("background"),
            surface = requiredColor("surface"),
            accent = requiredColor("accent"),
            text = requiredColor("text")
        )
    }
}

class ThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("theme-library", Context.MODE_PRIVATE)

    fun load(): List<ThemePalette> {
        val saved = preferences.getString(KEY_THEMES, null) ?: return builtInThemes
        return try {
            ThemePackage.decode(saved.byteInputStream())
        } catch (_: IllegalArgumentException) {
            builtInThemes
        } catch (_: JSONException) {
            builtInThemes
        }
    }

    fun save(themes: List<ThemePalette>) {
        check(preferences.edit().putString(KEY_THEMES, ThemePackage.encode(themes)).commit()) {
            "Could not save the theme library."
        }
    }

    companion object {
        private const val KEY_THEMES = "themes"

        val builtInThemes = listOf(
            ThemePalette(
                id = "builtin-midnight",
                name = "Midnight",
                author = "Spicetify Mobile",
                description = "A calm, deep-blue palette for late listening.",
                background = "#10131A",
                surface = "#1B202B",
                accent = "#8B7CFF",
                text = "#F4F5FA"
            ),
            ThemePalette(
                id = "builtin-forest",
                name = "Forest",
                author = "Spicetify Mobile",
                description = "Soft greens and warm, readable surfaces.",
                background = "#111A17",
                surface = "#1B2A23",
                accent = "#65D6A3",
                text = "#F1F7F2"
            ),
            ThemePalette(
                id = "builtin-sunset",
                name = "Sunset",
                author = "Spicetify Mobile",
                description = "Warm tones with a bright coral highlight.",
                background = "#201512",
                surface = "#34211C",
                accent = "#FF8A65",
                text = "#FFF4EC"
            )
        )
    }
}

fun importedCopy(theme: ThemePalette): ThemePalette =
    theme.copy(id = "imported-${UUID.randomUUID()}")
