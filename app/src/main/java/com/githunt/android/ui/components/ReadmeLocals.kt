package com.githunt.android.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** Loads a repo's README markdown: (owner, repo) -> markdown or an error. */
typealias ReadmeLoader = suspend (owner: String, repo: String) -> Result<String>

/**
 * Provided once at the top of the app (MainActivity). RepoCard reads it so every list that
 * shows cards can expand a README inline without passing a loader through each screen.
 * Null means no loader, and the button falls back to opening GitHub.
 */
val LocalReadmeLoader = staticCompositionLocalOf<ReadmeLoader?> { null }
