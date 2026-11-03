package com.selawi.cockpit.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** The base panel every dashboard area sits on. */
@Composable
fun CockpitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    // Day: crisp white card on a soft shadow. Night: translucent glass with a hairline edge.
    Column(
        modifier
            .let {
                if (CockpitColors.isDark) it else it.shadow(
                    10.dp, shape,
                    ambientColor = androidx.compose.ui.graphics.Color(0x1A111418),
                    spotColor = androidx.compose.ui.graphics.Color(0x1A111418),
                )
            }
            .clip(shape)
            .background(CockpitColors.Surface)
            .let { if (CockpitColors.isDark) it.border(BorderStroke(1.dp, CockpitColors.Stroke), shape) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Small uppercase label above a card's content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = CockpitColors.TextTertiary,
        modifier = modifier,
    )
}

/** Round icon button; the primary one (play/pause) is near-black by day and near-white at night. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = Space.touch,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (primary) CockpitColors.Inverse else CockpitColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (primary) CockpitColors.OnInverse else CockpitColors.TextPrimary,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/** Wide button with an optional icon. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = Space.touch)
            .clip(RoundedCornerShape(Space.touch / 2))
            .background(if (primary) CockpitColors.Inverse else CockpitColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
    ) {
        val tint = if (primary) CockpitColors.OnInverse else CockpitColors.TextPrimary
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** A full-height scrollable screen with a title, used by secondary screens. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        color = CockpitColors.TextPrimary,
        modifier = modifier.padding(bottom = Space.s),
    )
}
