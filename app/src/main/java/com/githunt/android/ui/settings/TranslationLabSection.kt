package com.githunt.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.githunt.android.GitHuntApp
import com.githunt.android.data.settings.TranslationLanguages
import com.githunt.android.data.translate.LabEngine
import com.githunt.android.data.translate.LabRun
import kotlinx.coroutines.launch

/**
 * Translates the same lines with the website, with Google called straight from the phone, and
 * with ML Kit on the device, then shows each answer and how long it took. The target is the
 * language picked on this screen.
 */
@Composable
fun TranslationLabSection(target: String) {
    val context = LocalContext.current
    val lab = remember(context) { (context.applicationContext as? GitHuntApp)?.container?.translationLab }
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf(SampleText) }
    var running by remember { mutableStateOf(false) }
    var runs by remember { mutableStateOf<List<LabRun>>(emptyList()) }

    SettingsSection(
        title = "Compare translation engines",
        description = "Translates the same lines three ways and shows how long each took. " +
            "Target language: ${TranslationLanguages.label(target)}.",
    ) {
        SettingsField(
            label = "Text to translate (one line each)",
            value = input,
            onValueChange = { input = it },
            singleLine = false,
            minLines = 4,
            maxLength = 2000,
            placeholder = "Paste repo descriptions or README sentences",
        )
        if (target == "off") {
            SettingsNote("Pick a language above first. Off means nothing is translated.")
        }
        SettingsActions {
            SettingsButton(
                text = if (running) "Running…" else "Run all three",
                loading = running,
                enabled = lab != null && !running && target != "off" && input.isNotBlank(),
                onClick = {
                    val texts = input.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MaxLines)
                    lab?.let { engines ->
                        scope.launch {
                            running = true
                            runs = emptyList()
                            try {
                                val done = ArrayList<LabRun>()
                                for (engine in LabEngine.values()) {
                                    done.add(engines.run(engine, texts, target))
                                    runs = done.toList()
                                }
                            } finally {
                                running = false
                            }
                        }
                    }
                },
            )
        }
        runs.forEach { run ->
            SettingsNote("${run.engine.label}: ${run.ms} ms. ${run.note}")
            SettingsBody(run.lines.joinToString("\n"))
        }
    }
}

private const val MaxLines = 20

// Mixed languages on purpose, so something gets translated whichever target is picked.
private const val SampleText =
    "Una biblioteca ligera para analizar y validar esquemas JSON\n" +
        "Schnelles, minimalistisches Web-Framework zum Bauen von APIs\n" +
        "A blazing fast terminal emulator with GPU rendering and ligatures\n" +
        "暗号化に対応したオフラインファーストのノートアプリ"
