package com.tobi.app.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.tobi.app.core.database.WatchHistoryEntity
import com.tobi.app.core.protocol.Acquisition
import com.tobi.app.core.protocol.EntryCard
import com.tobi.app.core.protocol.Shelf
import com.tobi.app.core.protocol.StreamSource
import com.tobi.app.core.protocol.normalizeCardStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ArtworkImage(
    url: String?,
    headers: Map<String, String> = emptyMap(),
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    cornerRadius: Dp = 10.dp
) {
    val palette = LocalTobiPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(palette.surface1),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(url, headers) {
                val networkHeaders = NetworkHeaders.Builder().apply {
                    headers.forEach { (name, value) -> set(name, value) }
                }.build()
                ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(networkHeaders)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!fallbackTitle.isNullOrBlank()) {
            Text(
                text = fallbackTitle,
                color = palette.textSecondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState(interaction)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    Box(
        modifier = modifier
            .graphicsLayerCompat(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = content
    )
}

private fun Modifier.graphicsLayerCompat(scale: Float): Modifier {
    return this.then(
        androidx.compose.ui.Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
fun BadgePill(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = foreground, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun LiveBadge() {
    val palette = LocalTobiPalette.current
    BadgePill(text = "LIVE", background = palette.accent, foreground = Color.White)
}

@Composable
fun EntryCardView(
    entry: EntryCard,
    headers: Map<String, String> = emptyMap(),
    style: String = "portrait",
    onClick: () -> Unit
) {
    val palette = LocalTobiPalette.current
    val normalized = normalizeCardStyle(style)
    val width = when (normalized) {
        "wide" -> 220.dp
        "square" -> 120.dp
        else -> 112.dp
    }
    val ratio = when (normalized) {
        "wide" -> 16f / 9f
        "square" -> 1f
        else -> 2f / 3f
    }
    val imageUrl = when (normalized) {
        "wide" -> entry.visuals?.banner ?: entry.visuals?.thumb ?: entry.visuals?.cover
        else -> entry.visuals?.cover ?: entry.visuals?.banner
    }
    PressableCard(onClick = onClick, modifier = Modifier.width(width)) {
        Column {
            Box {
                ArtworkImage(
                    url = imageUrl,
                    headers = headers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio),
                    fallbackTitle = entry.name
                )
                if (entry.liveNow) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                        LiveBadge()
                    }
                } else {
                    val badge = entry.badges.firstOrNull { it.isNotBlank() }
                    if (badge != null) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                            BadgePill(
                                text = badge,
                                background = Color.Black.copy(alpha = 0.55f),
                                foreground = palette.textPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.name,
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                entry.releaseYear?.toString(),
                entry.score?.let { String.format("%.1f", it) }
            ).filter { it.isNotBlank() }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" • "),
                    color = palette.textMuted,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    history: WatchHistoryEntity,
    onClick: () -> Unit
) {
    val palette = LocalTobiPalette.current
    val progress = if (history.durationMs > 0) {
        (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remaining = history.durationMs - history.positionMs
    PressableCard(onClick = onClick, modifier = Modifier.width(224.dp)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface1)
            ) {
                ArtworkImage(
                    url = history.banner ?: history.cover,
                    modifier = Modifier.fillMaxSize(),
                    fallbackTitle = history.title,
                    cornerRadius = 10.dp
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    if (!history.episodeLabel.isNullOrBlank()) {
                        Text(
                            text = history.episodeLabel,
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = history.title,
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (remaining > 0) {
                        Text(
                            text = formatRemaining(remaining),
                            color = palette.textMuted,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(palette.accent)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroCarousel(
    entries: List<EntryCard>,
    headersFor: (EntryCard) -> Map<String, String>,
    onPlay: (EntryCard) -> Unit,
    onDetails: (EntryCard) -> Unit
) {
    if (entries.isEmpty()) return
    val palette = LocalTobiPalette.current
    val pagerState = rememberPagerState(pageCount = { entries.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(entries) {
        while (true) {
            delay(6000)
            if (entries.size > 1) {
                val next = (pagerState.currentPage + 1) % entries.size
                pagerState.animateScrollToPage(next)
            }
        }
    }
    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 11f)
        ) { page ->
            val entry = entries[page]
            Box(modifier = Modifier.fillMaxSize()) {
                ArtworkImage(
                    url = entry.visuals?.banner ?: entry.visuals?.cover,
                    headers = headersFor(entry),
                    modifier = Modifier.fillMaxSize(),
                    fallbackTitle = entry.name,
                    cornerRadius = 0.dp
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    palette.canvas.copy(alpha = 0.35f),
                                    palette.canvas
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    val wordmark = entry.visuals?.wordmark
                    if (!wordmark.isNullOrBlank()) {
                        ArtworkImage(
                            url = wordmark,
                            headers = headersFor(entry),
                            modifier = Modifier
                                .height(44.dp)
                                .aspectRatio(3f),
                            cornerRadius = 0.dp
                        )
                    } else {
                        Text(
                            text = entry.name,
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.displayLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MetadataRow(
                        items = listOfNotNull(
                            entry.releaseYear?.toString(),
                            entry.score?.let { String.format("%.1f", it) },
                            entry.badges.firstOrNull()
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TobiButton(text = "Play", icon = Icons.Filled.PlayArrow, onClick = { onPlay(entry) })
                        TobiOutlinedButton(text = "Details", onClick = { onDetails(entry) })
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            entries.forEachIndexed { index, _ ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) palette.accent else palette.surface3)
                )
            }
        }
    }
}

@Composable
fun ContentRail(
    shelf: Shelf,
    headersFor: (EntryCard) -> Map<String, String>,
    onEntryClick: (EntryCard) -> Unit,
    onExpand: ((Shelf) -> Unit)? = null
) {
    if (shelf.entries.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = shelf.heading,
            actionLabel = if (shelf.expandable && onExpand != null) "View all" else null,
            onAction = { onExpand?.invoke(shelf) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(shelf.entries, key = { it.uid }) { entry ->
                EntryCardView(
                    entry = entry,
                    headers = headersFor(entry),
                    style = shelf.cardStyle,
                    onClick = { onEntryClick(entry) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val palette = LocalTobiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = palette.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = palette.accent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun MetadataRow(items: List<String>) {
    val palette = LocalTobiPalette.current
    val visible = items.filter { it.isNotBlank() }
    if (visible.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        visible.forEachIndexed { index, item ->
            Text(
                text = item,
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            if (index < visible.size - 1) {
                Text(
                    text = "  •  ",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun GenreChips(genres: List<String>) {
    if (genres.isEmpty()) return
    val palette = LocalTobiPalette.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(genres) { genre ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface2)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = genre, color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun TobiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val palette = LocalTobiPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.ctaFill,
            contentColor = palette.ctaInk,
            disabledContainerColor = palette.surface3,
            disabledContentColor = palette.textMuted
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TobiOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val palette = LocalTobiPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = palette.textPrimary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TobiIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalTobiPalette.current.textPrimary
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
fun FilterChipView(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalTobiPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.surface3 else palette.surface1)
            .border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    val palette = LocalTobiPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.hairline)
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalTobiPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(palette.surface1),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = palette.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            TobiButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 10.dp) {
    val palette = LocalTobiPalette.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -400f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(animationSpec = tween(1400), repeatMode = RepeatMode.Reverse),
        label = "shimmerOffset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(palette.surface1, palette.surface2, palette.surface1),
        start = Offset(offset, 0f),
        end = Offset(offset + 400f, 0f)
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun SkeletonCard(aspectRatio: Float, modifier: Modifier = Modifier) {
    ShimmerBox(modifier = modifier.aspectRatio(aspectRatio))
}

@Composable
fun TobiSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalTobiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface2,
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.textMuted.copy(alpha = 0.5f))
            )
        },
        content = content
    )
}

@Composable
fun TobiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    val palette = LocalTobiPalette.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = { Text(text = placeholder, color = palette.textMuted) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = palette.textMuted) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear", tint = palette.textMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.surface1,
            unfocusedContainerColor = palette.surface1,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            cursorColor = palette.accent
        )
    )
}

@Composable
fun SourcePickerSheet(
    sources: List<StreamSource>,
    resolvingId: String?,
    onSelect: (StreamSource) -> Unit,
    onResolveDeferred: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalTobiPalette.current
    TobiSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = "Select source", color = palette.textPrimary, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(12.dp))
            if (sources.isEmpty()) {
                Text(text = "No sources available", color = palette.textSecondary, style = MaterialTheme.typography.bodyLarge)
            } else {
                sources.forEach { source ->
                    val label = source.displayLabel ?: source.hostName ?: source.sourceId
                    val deferred = source.isDeferred()
                    val ready = source.isReady()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface1)
                            .clickable {
                                if (deferred) onResolveDeferred(source) else if (ready) onSelect(source)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label, color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            val sub = listOfNotNull(source.hostName, source.qualityTag).filter { it.isNotBlank() }.joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(text = sub, color = palette.textMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                            if (deferred) {
                                Text(text = "Tap to load", color = palette.warning, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (resolvingId == source.sourceId) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = palette.accent,
                                strokeWidth = 2.dp
                            )
                        } else if (ready) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = palette.success, modifier = Modifier.size(20.dp))
                        } else if (deferred) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = palette.warning, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DownloadPickerSheet(
    acquisitions: List<Acquisition>,
    onAcquire: (Acquisition) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalTobiPalette.current
    TobiSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = "Download", color = palette.textPrimary, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(12.dp))
            if (acquisitions.isEmpty()) {
                Text(text = "No download options available", color = palette.textSecondary, style = MaterialTheme.typography.bodyLarge)
            } else {
                acquisitions.forEach { acquisition ->
                    val label = acquisition.displayLabel ?: acquisition.hostName ?: acquisition.acqId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface1)
                            .clickable { onAcquire(acquisition) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label, color = palette.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            val sizeText = acquisition.sizeBytes?.let { formatBytes(it) }
                            val sub = listOfNotNull(acquisition.qualityTag, sizeText).filter { it.isNotBlank() }.joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(text = sub, color = palette.textMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                        Icon(imageVector = Icons.Filled.Download, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null) {
    val palette = LocalTobiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface1)
            .border(1.dp, palette.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = message, color = palette.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text(
                text = "Retry",
                color = palette.accent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onRetry)
            )
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    val palette = LocalTobiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface1)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, color = palette.textMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val palette = LocalTobiPalette.current
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier.size(28.dp),
        color = palette.accent,
        strokeWidth = 3.dp
    )
}

@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val palette = LocalTobiPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(palette.surface3)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.accent)
        )
    }
}

@Composable
fun SectionSpacer() {
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
fun CanvasBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalTobiPalette.current
    Box(modifier = modifier.fillMaxSize().background(palette.canvas), content = content)
}
