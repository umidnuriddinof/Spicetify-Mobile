package com.spicetify.mobile

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ThemeHubApp()
            }
        }
    }
}

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    BUILT_IN("Built-in"),
    IMPORTED("Imported")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeHubApp() {
    val context = LocalContext.current
    val store = remember { ThemeStore(context.applicationContext) }
    var themes by remember { mutableStateOf(store.load()) }
    var selectedId by remember { mutableStateOf(themes.firstOrNull()?.id) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var status by remember { mutableStateOf<String?>(null) }
    val selectedTheme = themes.firstOrNull { it.id == selectedId } ?: themes.firstOrNull()
    val visibleThemes = themes.filter { theme ->
        val matchesQuery = query.isBlank() ||
            theme.name.contains(query, ignoreCase = true) ||
            theme.author.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.BUILT_IN -> theme.isBuiltIn
            LibraryFilter.IMPORTED -> !theme.isBuiltIn
        }
        matchesQuery && matchesFilter
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected file could not be opened.")
                val imported = stream.use(ThemePackage::decode).map(::importedCopy)
                val updated = themes + imported
                require(updated.size <= ThemePackage.MAX_THEMES) {
                    "The library can contain at most ${ThemePackage.MAX_THEMES} themes."
                }
                store.save(updated)
                themes = updated
                selectedId = imported.firstOrNull()?.id
                status = "Imported ${imported.size} ${if (imported.size == 1) "theme" else "themes"}."
            } catch (error: Exception) {
                status = error.message ?: "Could not import the selected theme package."
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("The destination file could not be opened.")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(ThemePackage.encode(themes)) }
                status = "Exported ${themes.size} ${if (themes.size == 1) "theme" else "themes"}."
            } catch (error: Exception) {
                status = error.message ?: "Could not export the theme library."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spicetify Mobile", fontWeight = FontWeight.Bold)
                        Text("Your offline theme hub", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import")
                }
                TextButton(
                    onClick = { exportLauncher.launch("spicetify-themes.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export library")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search themes") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) }
                    )
                }
            }

            Text("${visibleThemes.size} themes", style = MaterialTheme.typography.titleSmall)
            if (visibleThemes.isEmpty()) {
                Text("No themes match your search.")
            } else {
                visibleThemes.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        selected = theme.id == selectedTheme?.id,
                        onClick = { selectedId = theme.id }
                    )
                }
            }

            selectedTheme?.let { ThemePreview(it) }

            status?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { status = null }) {
                    Text("Dismiss")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThemeRow(theme: ThemePalette, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ColorDot(theme.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(theme.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${theme.author} · ${if (theme.isBuiltIn) "Built-in" else "Imported"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ThemePreview(theme: ThemePalette) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(containerColor = colorFromHex(theme.background)),
            border = BorderStroke(1.dp, colorFromHex(theme.surface))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(theme.name, color = colorFromHex(theme.text), style = MaterialTheme.typography.titleLarge)
                Text(
                    theme.description.ifBlank { "A color palette preview." },
                    color = colorFromHex(theme.text)
                )
                Surface(
                    color = colorFromHex(theme.surface),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Now playing", color = colorFromHex(theme.text), fontWeight = FontWeight.Medium)
                            Text("Offline preview", color = colorFromHex(theme.text).copy(alpha = 0.72f))
                        }
                        Surface(
                            color = colorFromHex(theme.accent),
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {}
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Background" to theme.background,
                        "Surface" to theme.surface,
                        "Accent" to theme.accent,
                        "Text" to theme.text
                    ).forEach { (label, hex) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ColorDot(hex)
                            Text(label, color = colorFromHex(theme.text), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(hex: String) {
    Surface(
        modifier = Modifier.size(24.dp),
        color = colorFromHex(hex),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {}
}

private fun colorFromHex(value: String): Color = Color(AndroidColor.parseColor(value))
