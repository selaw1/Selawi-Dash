package com.selawi.cockpit.features.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.ScreenTitle
import com.selawi.cockpit.core.design.Space

@Composable
fun AppsScreen(
    apps: List<AppInfo>,
    pinned: List<AppInfo>,
    onOpen: (AppInfo) -> Unit,
    onTogglePin: (AppInfo) -> Unit,
    onCarHome: () -> Unit,
) {

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("Apps")
                Text(
                    "Tap to open. Press and hold to pin to the dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
            }
            PillButton("BYD home", onClick = onCarHome)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(apps, key = { it.key }) { app ->
                AppTile(
                    app = app,
                    pinned = pinned.any { it.key == app.key },
                    onOpen = { onOpen(app) },
                    onTogglePin = { onTogglePin(app) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    pinned: Boolean = false,
    iconSize: Dp = 64.dp,
    onOpen: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
) {
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CockpitColors.SurfaceHigh)
                .combinedClickable(onClick = onOpen, onLongClick = onTogglePin)
                .padding(vertical = Space.m, horizontal = Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(iconSize)) }
            Text(
                app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.s),
            )
        }
        if (pinned && onTogglePin != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.s)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CockpitColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, contentDescription = "Pinned", tint = CockpitColors.Background, modifier = Modifier.size(16.dp))
            }
        }
    }
}
