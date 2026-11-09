package com.githunt.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** git (quiet) + hunt (loud) + a signal dot. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: TextUnit = 28.sp) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = scheme.onSurfaceVariant, fontWeight = FontWeight.Medium)) { append("git") }
            withStyle(SpanStyle(color = scheme.onBackground, fontWeight = FontWeight.Black)) { append("hunt") }
            withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Black)) { append(".") }
        },
        fontSize = size,
        letterSpacing = (-1).sp,
        modifier = modifier,
    )
}

/**
 * Expressive full-width button: a pill at rest that springs into a squarer shape while it is
 * pressed, and shows the morphing loader while [loading].
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 28.dp, pressed = 14.dp)
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(corner),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.primary.copy(alpha = 0.35f),
            disabledContentColor = scheme.onPrimary.copy(alpha = 0.7f),
        ),
    ) {
        if (loading) {
            GhLoadingIndicator(diameter = 28.dp, color = scheme.onPrimary)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner = animatedPressCorner(interaction, rest = 28.dp, pressed = 14.dp)
    OutlinedButton(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(corner),
        interactionSource = interaction,
        border = BorderStroke(1.dp, scheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = scheme.onBackground,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
    ) {
        if (loading) {
            GhLoadingIndicator(diameter = 28.dp, color = scheme.onBackground)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    var revealed by remember { mutableStateOf(false) }

    val revealToggle: @Composable () -> Unit = {
        Text(
            text = if (revealed) "HIDE" else "SHOW",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { revealed = !revealed }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        shape = RoundedCornerShape(18.dp),
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = if (isPassword) revealToggle else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onBackground,
            unfocusedTextColor = scheme.onBackground,
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
            disabledContainerColor = scheme.surface,
            cursorColor = scheme.primary,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
        ),
    )
}

@Composable
fun StatusChip(text: String, tone: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tone,
        modifier = modifier
            .border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
