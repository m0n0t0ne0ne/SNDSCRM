package com.music.spotui.ui.screens

import com.music.spotui.ui.theme.AppDestructive
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.tone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.api.UserProfile
import app.myspoti.api.best
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.components.spotifyImageUrl
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import kotlinx.coroutines.launch

/**
 * A Spotify user's profile, as the Web Player shows it: picture, name, followers and
 * following, a Follow button (not on your own profile) and the public playlists.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ProfileScreen(navController: NavController, userId: String, userName: String = "") {
    val uri = "spotify:user:$userId"
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var failed by remember(userId) { mutableStateOf(false) }
    var following by remember(userId) { mutableStateOf<Boolean?>(null) }
    var blocked by remember(userId) { mutableStateOf<Boolean?>(null) }
    var blockAsk by remember(userId) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val me = com.music.spotui.data.api.ProfileCache.id == userId
    val scope = rememberCoroutineScope()
    var attempt by remember(userId) { mutableStateOf(0) }
    LaunchedEffect(userId, attempt) {
        failed = false
        runCatching { MyspotiRuntime.api.users.profile(uri) }
            .onSuccess { profile = it }.onFailure { failed = true }
        if (!me) following = runCatching { MyspotiRuntime.api.users.isFollowing(listOf(uri)).single() }.getOrNull()
        if (!me) blocked = runCatching { MyspotiRuntime.api.users.isBlocked(uri) }.getOrNull()
    }
    val p = profile
    if (blockAsk) {
        val name = p?.name ?: userName.ifEmpty { userId }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { blockAsk = false },
            containerColor = tone(0xFF282828),
            title = { Text(stringResource(R.string.profile_block_title, name), color = AppText) },
            text = { Text(stringResource(R.string.profile_block_confirm, name), color = AppMuted) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    blockAsk = false
                    com.music.spotui.di.Haptics.confirm()
                    scope.launch {
                        runCatching { MyspotiRuntime.api.users.blockUser(uri) }
                            .onSuccess { blocked = true; following = false }
                            .onFailure { android.widget.Toast.makeText(context, R.string.profile_block_failed, android.widget.Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.profile_block), color = AppDestructive) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { blockAsk = false }) { Text(stringResource(R.string.cancel), color = AppText) } },
        )
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace()),
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_navigate_back), tint = AppText,
                    modifier = Modifier.pressable(0.96f) { navController.navigateUp() }.padding(16.dp).size(26.dp),
                )
                Spacer(Modifier.weight(1f))
                // The Web Player's profile menu: Block / Unblock (only once the block state is known).
                val b = blocked
                if (!me && b != null) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Icon(
                            Icons.Default.MoreVert, stringResource(R.string.profile_more_options), tint = AppText,
                            modifier = Modifier.pressable(0.96f) { menu = true }.padding(16.dp).size(26.dp),
                        )
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = tone(0xFF282828)) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(if (b) R.string.profile_unblock else R.string.profile_block), color = AppText) },
                                onClick = {
                                    menu = false
                                    if (b) scope.launch {
                                        blocked = false
                                        runCatching { MyspotiRuntime.api.users.unblockUser(uri) }.onFailure {
                                            blocked = true
                                            android.widget.Toast.makeText(context, R.string.profile_block_failed, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else blockAsk = true
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (me) MyProfilePhoto(p, userName, onChanged = { attempt++ })
                else com.music.spotui.ui.components.ProfileAvatar(
                    url = spotifyImageUrl(p?.images?.best(300)?.url),
                    name = p?.name ?: userName, size = 150.dp,
                )
                Spacer(Modifier.height(14.dp))
                com.music.spotui.ui.components.FitText(p?.name ?: userName, fontSize = 28.sp, minSize = 18.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (p != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        p.followers?.let {
                            Text(pluralStringResource(R.plurals.profile_followers, it, it), color = AppMuted, fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .pressable(0.96f) { navController.navigate(followListRoute(userId, followers = true)) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                        if (p.followers != null && p.following != null) Text("•", color = AppMuted, fontSize = 14.sp)
                        p.following?.let {
                            Text(stringResource(R.string.profile_following_count, it), color = AppMuted, fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .pressable(0.96f) { navController.navigate(followListRoute(userId, followers = false)) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                    }
                }
                // Your own profile leads to your listening stats, like the official app's.
                if (me) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.stats_listening_stats),
                        color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, AppMuted, RoundedCornerShape(50))
                            .pressable(0.96f) { navController.navigate(com.music.spotui.ui.navigation.Routes.Recap.route) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                if (blocked == true) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.profile_blocked), color = AppMuted, fontSize = 14.sp)
                }
                val f = following
                if (!me && f != null && blocked != true) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(if (f) R.string.curation_button_following_state_text else R.string.curation_button_follow_state_text),
                        color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, if (f) AppText else AppMuted, RoundedCornerShape(50))
                            .pressable(0.96f) {
                                com.music.spotui.di.Haptics.confirm()
                                following = !f
                                scope.launch {
                                    runCatching {
                                        if (f) MyspotiRuntime.api.users.unfollow(listOf(uri)) else MyspotiRuntime.api.users.follow(listOf(uri))
                                    }.onFailure { following = f }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
        when {
            p == null && !failed -> item { com.music.spotui.ui.components.SkeletonList(count = 5) }
            p == null -> item {
                com.music.spotui.ui.components.ErrorRetry(stringResource(R.string.page_load_failed)) { attempt++ }
            }
            p.publicPlaylists.isNotEmpty() -> {
                item {
                    Text(stringResource(R.string.profile_public_playlists), color = AppText, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 12.dp))
                }
                items(p.publicPlaylists, key = { it.uri }) { pl ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                            .pressable(0.98f) { navController.navigate(playlistRoute(pl.uri.substringAfterLast(':'), pl.name)) }
                            .padding(16.dp, 8.dp),
                    ) {
                        GlideImage(
                            transition = CrossFade,
                            model = spotifyImageUrl(pl.images.best(160)?.url), contentDescription = null, contentScale = ContentScale.Crop,
                            failure = placeholder(R.drawable.placeholder), loading = placeholder(R.drawable.placeholder),
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            pl.followers?.let {
                                Text(pluralStringResource(R.plurals.profile_followers, it.toInt(), it.toInt()),
                                    color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SoundScream: your own picture, with a menu to change or remove it (the Web Player's "Edit
 * profile"). The photo comes from Android's photo picker, which needs no permission.
 */
@Composable
private fun MyProfilePhoto(p: UserProfile?, userName: String, onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val hasPhoto = !p?.images.isNullOrEmpty()
    fun toast(res: Int) = android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_SHORT).show()
    fun apply(image: ByteArray?) {
        busy = true
        scope.launch {
            runCatching { MyspotiRuntime.api.users.setProfileImage(image) }
                .onSuccess {
                    onChanged()
                    // The picture in the top bar follows.
                    runCatching { MyspotiRuntime.api.users.me() }.getOrNull()?.let {
                        com.music.spotui.data.api.ProfileCache.pictureChanged(it.images.best(160)?.url)
                    }
                }
                .onFailure {
                    app.myspoti.host.android.Logbook.log("profile picture not changed: ${it.message?.take(120)}")
                    toast(R.string.profile_photo_failed)
                }
            busy = false
        }
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.music.spotui.ui.components.ProfilePhoto.prepare(context, picked) }
                    .getOrDefault(com.music.spotui.ui.components.ProfilePhoto.Result.Unreadable)
            }
            busy = false
            when (prepared) {
                is com.music.spotui.ui.components.ProfilePhoto.Result.Ready -> apply(prepared.jpeg)
                com.music.spotui.ui.components.ProfilePhoto.Result.TooSmall -> toast(R.string.profile_photo_too_small)
                com.music.spotui.ui.components.ProfilePhoto.Result.Unreadable -> toast(R.string.profile_photo_failed)
            }
        }
    }
    Box(contentAlignment = Alignment.Center) {
        com.music.spotui.ui.components.ProfileAvatar(
            url = spotifyImageUrl(p?.images?.best(300)?.url),
            name = p?.name ?: userName, size = 150.dp,
            modifier = Modifier.pressable(0.97f) { if (!busy) menu = true },
        )
        if (busy) Box(Modifier.size(150.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppText, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
        // A small pencil on the picture: it can be tapped.
        if (!busy) Box(
            Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(tone(0xFF282828)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Edit, stringResource(R.string.profile_photo_change), tint = AppText, modifier = Modifier.size(18.dp))
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = tone(0xFF282828)) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_photo_change), color = AppText) },
                onClick = {
                    menu = false
                    picker.launch(androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            if (hasPhoto) androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_photo_remove), color = AppDestructive) },
                onClick = { menu = false; apply(null) },
            )
        }
    }
}
