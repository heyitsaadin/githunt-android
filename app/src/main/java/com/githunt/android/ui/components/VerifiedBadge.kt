package com.githunt.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GitHub accounts that are well known and trusted. Their repos get a [VerifiedBadge] next to the
 * owner name on the card. Names are GitHub logins, compared case-insensitively.
 *
 * This list is curated by hand: add or remove a login here to change who is verified.
 */
private val VerifiedOwners: Set<String> = setOf(
    // AI labs and ML
    "openai", "anthropics", "google-deepmind", "meta-llama", "huggingface", "pytorch",
    "tensorflow", "langchain-ai", "ollama", "ggerganov", "deepseek-ai", "nvidia",
    // Big tech and infrastructure
    "google", "googleapis", "microsoft", "facebook", "meta", "apple", "aws", "amzn",
    "github", "mozilla", "cloudflare", "vercel", "supabase", "docker", "kubernetes",
    "hashicorp", "redis", "elastic", "mongodb", "jetbrains", "square", "android", "flutter",
    // Languages and runtimes
    "kotlin", "rust-lang", "golang", "python", "nodejs", "denoland", "oven-sh",
    "swiftlang", "dotnet",
    // Frameworks and tooling
    "reactjs", "vuejs", "sveltejs", "angular", "tailwindlabs", "shadcn-ui",
    "vitejs", "webpack", "babel", "eslint", "prettier",
    // Well-known maintainers
    "torvalds", "gaearon", "yyx990803", "sindresorhus", "antfu", "tj", "karpathy",
)

/** True when [owner] (a GitHub login) is on the curated verified list. */
fun isVerifiedOwner(owner: String?): Boolean =
    owner != null && owner.lowercase() in VerifiedOwners

/** Blue scalloped seal with a white check, drawn in code so it needs no image asset. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, badgeSize: Dp = 14.dp) {
    Canvas(
        modifier = modifier
            .size(badgeSize)
            .semantics { contentDescription = "Verified account" },
    ) {
        val w = size.width
        val blue = Color(0xFF1D9BF0)
        val center = Offset(w / 2f, w / 2f)

        // The scalloped edge: eight small circles around a bigger one.
        val petals = 8
        for (i in 0 until petals) {
            val angle = 2.0 * PI * i / petals
            drawCircle(
                color = blue,
                radius = w * 0.20f,
                center = Offset(
                    center.x + (w * 0.30f * cos(angle)).toFloat(),
                    center.y + (w * 0.30f * sin(angle)).toFloat(),
                ),
            )
        }
        drawCircle(color = blue, radius = w * 0.34f, center = center)

        val check = Path().apply {
            moveTo(w * 0.30f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.65f)
            lineTo(w * 0.70f, w * 0.37f)
        }
        drawPath(
            path = check,
            color = Color.White,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
