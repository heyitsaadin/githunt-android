package com.githunt.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.githunt.android.data.auth.User
import com.githunt.android.ui.auth.HomeState
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.StatusChip
import com.githunt.android.ui.components.Wordmark

/**
 * Placeholder for the signed-in state. Its one real job today: make email
 * confirmation work end to end. The feed and the rest arrive in later layers.
 */
@Composable
fun HomeScreen(
    user: User,
    state: HomeState,
    onRefresh: (manual: Boolean) -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // Coming back from the mail app: check whether the link was opened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefresh(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark()
            if (user.emailVerified) {
                StatusChip(text = "VERIFIED", tone = scheme.primary)
            } else {
                StatusChip(text = "UNVERIFIED", tone = scheme.error)
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            text = "SIGNED IN AS",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onBackground,
        )
        user.email?.let { email ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))
        val panelShape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .background(scheme.surface)
                .border(1.dp, scheme.outline, panelShape)
                .padding(18.dp),
        ) {
            if (user.emailVerified) {
                Text(
                    text = "You're in.",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your account and email are confirmed. The feed is the next layer we build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Confirm your email",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Open the link we sent you, then come back and tap the button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = "I've confirmed it",
                    onClick = { onRefresh(true) },
                    enabled = !state.resending,
                    loading = state.refreshing,
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    text = "Resend email",
                    onClick = onResend,
                    enabled = !state.refreshing,
                    loading = state.resending,
                )
            }
            state.notice?.let { notice ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlineButton(text = "Sign out", onClick = onSignOut)
    }
}
