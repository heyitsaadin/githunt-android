package com.githunt.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.ui.components.AnimatedWordmark
import com.githunt.android.ui.components.GhLoadingIndicator
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.Wordmark

/**
 * Shown while the app checks whether the person is still signed in.
 * With [animate] the wordmark types itself out and [onAnimationFinished] is called when it is done;
 * without it this is the static wordmark with the morphing loader underneath.
 */
@Composable
fun LoadingScreen(animate: Boolean = false, onAnimationFinished: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (animate) {
            AnimatedWordmark(fontSize = 40.sp, onFinished = onAnimationFinished)
        } else {
            Wordmark(size = 40.sp)
            // Offset instead of stacked in a column, so the wordmark never moves between the
            // typed and the static version.
            GhLoadingIndicator(
                modifier = Modifier.offset(y = 76.dp),
                diameter = 36.dp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The server did not answer. The person is still signed in; they just need to try again. */
@Composable
fun UnavailableScreen(message: String, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(size = 32.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = "Try again", onClick = onRetry)
        }
    }
}
