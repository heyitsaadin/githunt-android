package com.githunt.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** The three places the signed-in app lives, same as the website's dock: Feed, Discover, Profile. */
enum class AppTab { Home, Discover, Profile }

@Composable
fun AppBottomNav(
    selected: AppTab,
    avatarUrl: String?,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.background),
    ) {
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.5f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavTab(
                    label = "Home",
                    icon = NavIcons.Home,
                    active = selected == AppTab.Home,
                    onClick = { onSelect(AppTab.Home) },
                )
                NavTab(
                    label = "Discover",
                    icon = NavIcons.Discover,
                    active = selected == AppTab.Discover,
                    onClick = { onSelect(AppTab.Discover) },
                )
                NavTab(
                    label = "Profile",
                    icon = NavIcons.Person,
                    active = selected == AppTab.Profile,
                    avatarUrl = avatarUrl,
                    onClick = { onSelect(AppTab.Profile) },
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: String,
    active: Boolean,
    onClick: () -> Unit,
    avatarUrl: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.onBackground else scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(44.dp)
            .clip(CircleShape)
            .background(if (active) scheme.onBackground.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(if (active) Modifier.border(2.dp, tint, CircleShape) else Modifier),
            )
        } else {
            PathIcon(pathData = icon, tint = tint, iconSize = 24.dp)
        }
    }
}
