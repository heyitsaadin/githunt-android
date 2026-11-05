package com.githunt.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.ui.components.GhTextField
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.Wordmark

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val signUp = form.mode == AuthMode.SignUp
    val busy = form.busy || form.googleBusy

    fun submit() {
        focusManager.clearFocus()
        viewModel.submit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Wordmark(size = 36.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Find repos before they trend.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))

        ModeSwitch(selected = form.mode, onSelect = viewModel::setMode, enabled = !busy)
        Spacer(Modifier.height(20.dp))

        GhTextField(
            value = form.username,
            onValueChange = viewModel::onUsernameChange,
            label = "Username",
            enabled = !busy,
        )
        if (signUp) {
            Spacer(Modifier.height(12.dp))
            GhTextField(
                value = form.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                keyboardType = KeyboardType.Email,
                enabled = !busy,
            )
        }
        Spacer(Modifier.height(12.dp))
        GhTextField(
            value = form.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
            onDone = { submit() },
            enabled = !busy,
        )

        form.error?.let { message ->
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
            )
        }

        if (signUp && form.error == null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "We'll email you a link to confirm your address.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = if (signUp) "Create account" else "Log in",
            onClick = { submit() },
            enabled = !form.googleBusy,
            loading = form.busy,
        )

        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = scheme.outline)
            Text(
                text = "OR",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = scheme.outline)
        }
        Spacer(Modifier.height(22.dp))

        OutlineButton(
            text = "Continue with Google",
            onClick = {
                focusManager.clearFocus()
                viewModel.signInWithGoogle(context)
            },
            enabled = !form.busy,
            loading = form.googleBusy,
        )
    }
}

@Composable
private fun ModeSwitch(
    selected: AuthMode,
    onSelect: (AuthMode) -> Unit,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(4.dp),
    ) {
        listOf(AuthMode.LogIn to "Log in", AuthMode.SignUp to "Sign up").forEach { (mode, label) ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.surfaceVariant else Color.Transparent)
                    .clickable(enabled = enabled && !active) { onSelect(mode) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) scheme.onBackground else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
