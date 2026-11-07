package com.githunt.android.data.engagement

import com.githunt.android.data.feed.FeedItem
import com.githunt.android.data.feed.RepoItem
import java.time.Instant
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The web home feed's ranking (lib/feed-ranking.js rankRepo), minus the follow bonuses:
 * fresh, media-rich repos first, with a random jitter so the order never feels like a leaderboard,
 * plus a bonus or penalty from the person's taste profile.
 *
 * Applied to each page as it arrives and never to what is already on screen, so cards never
 * jump around under a finger.
 */
object FeedRanking {

    private const val INTEREST_WEIGHT = 0.08
    private const val INTEREST_CAP = 3.0
    private const val DAY_MS = 86_400_000.0

    /** Sums the profile's weights for the repo's language, topics and language:topic pairs. */
    fun interestScore(item: RepoItem, scores: Map<String, Double>): Double {
        if (scores.isEmpty()) return 0.0
        val language = item.language?.lowercase()
        val domains = item.topics.filter { it.isNotBlank() }.map { it.lowercase() }
        var total = 0.0
        if (language != null) {
            total += scores["language:$language"] ?: 0.0
            for (domain in domains) total += scores["pair:$language:$domain"] ?: 0.0
        }
        for (domain in domains) total += scores["domain:$domain"] ?: 0.0
        return total.coerceIn(-INTEREST_CAP, INTEREST_CAP)
    }

    fun score(item: RepoItem, scores: Map<String, Double>, jitter: Double): Double {
        val media = min(item.readmeImages.size, 5) / 5.0
        val snippet = if (item.readmeSnippet.isNullOrBlank()) 0.0 else 0.15
        val stars = log10(item.stars + 1.0) / 6.0
        return recency(item.updatedAt) * 0.45 +
            media * 0.3 +
            snippet +
            stars * 0.05 +
            jitter * 0.35 +
            interestScore(item, scores) * INTEREST_WEIGHT
    }

    /** Best first. Anything that is not a repo keeps the slot it already had. */
    fun rank(items: List<FeedItem>, scores: Map<String, Double>): List<FeedItem> {
        val slots = items.indices.filter { items[it] is RepoItem }
        if (slots.size < 2) return items
        val sorted = slots
            .map { items[it] as RepoItem }
            .map { repo -> repo to score(repo, scores, Random.nextDouble()) }
            .sortedByDescending { pair -> pair.second }
            .map { pair -> pair.first }
        val out = items.toMutableList()
        slots.forEachIndexed { index, slot -> out[slot] = sorted[index] }
        return out
    }

    /** 1.0 for "just updated", halving roughly every 3.5 days (exp(-days / 5)). */
    private fun recency(updatedAt: String?): Double {
        val now = System.currentTimeMillis()
        val stamp = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
        val ageDays = max(0.0, (now - stamp) / DAY_MS)
        return exp(-ageDays / 5.0)
    }
}
