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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
            .background(scheme.surfaceVariant),
    ) {
        HorizontalDivider(color = scheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
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

@Composable
private fun RowScope.NavTab(
    label: String,
    icon: String,
    active: Boolean,
    onClick: () -> Unit,
    avatarUrl: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.onBackground else scheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (label == "Profile" && avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(if (active) Modifier.border(1.5.dp, tint, CircleShape) else Modifier),
            )
        } else {
            PathIcon(pathData = icon, tint = tint, iconSize = 24.dp)
        }
        Spacer(Modifier.height(2.dp))
        androidx.compose.material3.Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
