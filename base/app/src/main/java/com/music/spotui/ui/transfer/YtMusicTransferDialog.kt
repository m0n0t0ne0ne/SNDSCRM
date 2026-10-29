package com.music.spotui.ui.transfer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppShapes
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AppType
import com.music.spotui.ui.theme.tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface RowStatus {
    data object Waiting : RowStatus
    data object Searching : RowStatus
    data class Found(val match: YtMatch) : RowStatus
    data object Missing : RowStatus
}

private enum class Phase { READY, RUNNING, DONE, FAILED }

/**
 * Full-screen tool that copies an album or playlist to YouTube Music without signing in:
 * each song is looked up, the matches are listed as they come, and links open them in
 * YouTube as a playlist the user saves.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun YtMusicTransferDialog(
    name: String,
    coverUri: String,
    songs: List<SongsModel>,
    /** The page is still fetching songs (a long playlist): the button waits for all of them. */
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateListOf<RowStatus>() }
    var snapshot by remember { mutableStateOf<List<SongsModel>>(emptyList()) }
    var phase by remember { mutableStateOf(Phase.READY) }
    var current by remember { mutableIntStateOf(0) }

    fun start() {
        snapshot = songs.filter { it.title.isNotBlank() }
        statuses.clear(); statuses.addAll(List(snapshot.size) { RowStatus.Waiting })
        phase = Phase.RUNNING
        scope.launch {
            var failuresInARow = 0
            for (i in snapshot.indices) {
                current = i
                statuses[i] = RowStatus.Searching
                val s = snapshot[i]
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { YtMusicMatcher.find(TransferSong(s.title, s.singer, s.durationMs)) }
                }
                if (outcome.isFailure) {
                    failuresInARow++
                    statuses[i] = RowStatus.Missing
                    // The network, not the songs: stop and say so instead of marking everything missing.
                    if (failuresInARow >= 3) { phase = Phase.FAILED; return@launch }
                } else {
                    failuresInARow = 0
                    statuses[i] = outcome.getOrNull()?.let { RowStatus.Found(it) } ?: RowStatus.Missing
                }
                // Gentle pacing: one search at a time, as a person searching would.
                delay(150)
            }
            phase = Phase.DONE
        }
    }

    val found = statuses.mapNotNull { (it as? RowStatus.Found)?.match }
    val list = if (phase == Phase.READY) songs else snapshot

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(AppBackground)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Top bar
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    ) { Icon(Icons.Filled.Close, stringResource(R.string.yt_transfer_close), tint = AppText) }
                    Text(stringResource(R.string.yt_transfer_title), color = AppText, style = AppType.ItemTitle.copy(fontSize = 17.sp),
                        modifier = Modifier.padding(start = 6.dp))
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            GlideImage(model = coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(AppShapes.Tile))
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(name, color = AppText, style = AppType.Section, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.yt_transfer_songs, list.size), color = AppMuted, style = AppType.ItemSub)
                            }
                        }
                    }
                    item { StatusBlock(phase, current, list.size, found.size, loading) }
                    item {
                        when (phase) {
                            Phase.READY -> PillButton(
                                if (loading) stringResource(R.string.yt_transfer_loading) else stringResource(R.string.yt_transfer_start),
                                primary = true, enabled = !loading && songs.isNotEmpty(),
                            ) { start() }
                            Phase.FAILED -> PillButton(stringResource(R.string.yt_transfer_retry), primary = true) { start() }
                            Phase.DONE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val links = YtMusicMatcher.playlistLinks(found.map { it.candidate.videoId })
                                links.forEachIndexed { part, url ->
                                    val from = part * YtMusicMatcher.PER_LINK + 1
                                    val to = minOf(found.size, (part + 1) * YtMusicMatcher.PER_LINK)
                                    PillButton(stringResource(R.string.yt_transfer_open_part, from, to), primary = true, icon = Icons.Outlined.OpenInNew) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    }
                                }
                                if (found.isNotEmpty()) PillButton(stringResource(R.string.yt_transfer_copy), primary = false, icon = Icons.Outlined.ContentCopy) {
                                    val text = snapshot.indices.mapNotNull { i ->
                                        (statuses.getOrNull(i) as? RowStatus.Found)?.let { f ->
                                            "${snapshot[i].title} – ${snapshot[i].singer}: ${YtMusicMatcher.songLink(f.match.candidate.videoId)}"
                                        }
                                    }.joinToString("\n")
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText(name, text))
                                    android.widget.Toast.makeText(context, R.string.yt_transfer_copied, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            Phase.RUNNING -> {}
                        }
                    }
                    if (phase != Phase.READY) item {
                        Text(stringResource(R.string.yt_transfer_tip), color = AppMuted, fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
                    }
                    itemsIndexed(list, key = { i, s -> "$i|${s.title}" }) { i, song ->
                        val status = if (phase == Phase.READY) null else statuses.getOrNull(i)
                        SongRow(song, status) { videoId ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YtMusicMatcher.songLink(videoId)))) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(phase: Phase, current: Int, total: Int, found: Int, loading: Boolean) {
    val text = when (phase) {
        Phase.READY -> stringResource(R.string.yt_transfer_intro, YtMusicMatcher.PER_LINK)
        Phase.RUNNING -> stringResource(R.string.yt_transfer_progress, current + 1, total)
        Phase.DONE -> stringResource(R.string.yt_transfer_done, found, total)
        Phase.FAILED -> stringResource(R.string.yt_transfer_offline)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text, color = if (phase == Phase.READY) AppMuted else AppText,
            fontSize = if (phase == Phase.READY) 14.sp else 16.sp, lineHeight = 20.sp,
            fontWeight = if (phase == Phase.READY) FontWeight.Normal else FontWeight.Bold)
        if (phase == Phase.RUNNING && total > 0) {
            Spacer(Modifier.height(12.dp))
            // Progress bar in the app's accent.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(AppShapes.Pill).background(tone(0xFF2A2A2A))) {
                Box(Modifier.fillMaxWidth((current + 1f) / total).height(4.dp).clip(AppShapes.Pill).background(AppAccent))
            }
        }
        if (phase == Phase.READY && loading) Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PillButton(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(AppShapes.Pill)
            .background(if (primary) AppAccent.copy(alpha = if (enabled) 1f else 0.4f) else tone(0xFF2A2A2A))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (primary) Color.Black else AppText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = if (primary) Color.Black else AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SongRow(song: SongsModel, status: RowStatus?, onOpen: (String) -> Unit) {
    val match = (status as? RowStatus.Found)?.match
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (match != null) Modifier.clickable { onOpen(match.candidate.videoId) } else Modifier)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when (status) {
                null, RowStatus.Waiting, RowStatus.Searching -> song.singer
                is RowStatus.Found -> "→ " + status.match.candidate.title +
                    (status.match.candidate.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                RowStatus.Missing -> stringResource(R.string.yt_transfer_not_found)
            }
            Text(sub, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (status) {
                RowStatus.Searching -> CircularProgressIndicator(color = AppAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                is RowStatus.Found -> Box(Modifier.size(20.dp).clip(CircleShape).background(AppAccent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
                RowStatus.Missing -> Icon(Icons.Filled.Close, null, tint = AppMuted, modifier = Modifier.size(18.dp))
                else -> {}
            }
        }
    }
}
