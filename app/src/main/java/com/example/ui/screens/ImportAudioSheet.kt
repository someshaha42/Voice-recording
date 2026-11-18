package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.ImportedPlaybackState
import com.example.data.DeviceAudioItem
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPanelElevated
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

enum class ImportTab {
    MEDIA,
    AUDIO
}

@Composable
fun ImportAudioOverlay(
    hasAudioPermission: Boolean,
    audioItems: List<DeviceAudioItem>,
    isLoadingAudio: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRefreshAudioList: () -> Unit,
    onLaunchVideoPicker: () -> Unit,
    onSelectAudioItem: (DeviceAudioItem) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    var selectedTab by remember { mutableStateOf(ImportTab.MEDIA) }
    var searchQuery by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("import_audio_screen"),
        color = StudioObsidian
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioRack)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = CyanSignal
                        )
                        Text(
                            text = stringResource(R.string.import_audio),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("import_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_import),
                            tint = TextSecondary
                        )
                    }
                }

                // Top 2-tab selector: Media | Audio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioRack)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImportSectionTabButton(
                        label = "Media",
                        icon = Icons.Filled.VideoFile,
                        selected = selectedTab == ImportTab.MEDIA,
                        onClick = { selectedTab = ImportTab.MEDIA },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_tab_media")
                    )
                    ImportSectionTabButton(
                        label = "Audio",
                        icon = Icons.Filled.AudioFile,
                        selected = selectedTab == ImportTab.AUDIO,
                        onClick = {
                            selectedTab = ImportTab.AUDIO
                            if (hasAudioPermission && audioItems.isEmpty()) {
                                onRefreshAudioList()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_tab_audio")
                    )
                }

                HorizontalDivider(color = StudioBorder)

                when (selectedTab) {
                    ImportTab.MEDIA -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = StudioPanel,
                                border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(CyanContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Movie,
                                            contentDescription = null,
                                            tint = CyanSignal,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Text(
                                        text = "Extract Audio from Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Select any recent video from Android's system media picker (newest videos appear first, video files only). Your original video remains untouched while VoxForge extracts its audio track into the 48 kHz DSP engine.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Button(
                                        onClick = onLaunchVideoPicker,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyanSignal,
                                            contentColor = StudioRack
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("import_choose_video_button")
                                    ) {
                                        Icon(imageVector = Icons.Filled.VideoFile, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Choose Video File", style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    }

                    ImportTab.AUDIO -> {
                        if (!hasAudioPermission) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = StudioPanel,
                                    border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.audio_permission_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.audio_permission_body),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                        Button(
                                            onClick = onRequestAudioPermission,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = CyanSignal,
                                                contentColor = StudioRack
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("grant_audio_permission_button")
                                        ) {
                                            Text(stringResource(R.string.audio_permission_grant))
                                        }
                                    }
                                }
                            }
                        } else {
                            val filtered = remember(audioItems, searchQuery) {
                                if (searchQuery.isBlank()) {
                                    audioItems
                                } else {
                                    audioItems.filter {
                                        it.displayName.contains(searchQuery.trim(), ignoreCase = true)
                                    }
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        },
                                        placeholder = { Text("Filter audio files...") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onRefreshAudioList,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Refresh audio files",
                                            tint = CyanSignal
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "ALL DEVICE AUDIO (${filtered.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Newest first",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanSignal
                                    )
                                }

                                when {
                                    isLoadingAudio -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = CyanSignal)
                                        }
                                    }
                                    filtered.isEmpty() -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No accessible audio files found on device.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    else -> {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filtered, key = { it.id }) { audio ->
                                                Surface(
                                                    shape = MaterialTheme.shapes.medium,
                                                    color = StudioPanel,
                                                    border = BorderStroke(1.dp, StudioBorder),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(MaterialTheme.shapes.medium)
                                                        .clickable { onSelectAudioItem(audio) }
                                                        .testTag("device_audio_item_${audio.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(StudioRack),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.AudioFile,
                                                                contentDescription = null,
                                                                tint = CyanSignal,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = audio.displayName,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = CyanContainer
                                                                ) {
                                                                    Text(
                                                                        text = audio.formatLabel,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = CyanSignal,
                                                                        modifier = Modifier.padding(
                                                                            horizontal = 6.dp,
                                                                            vertical = 2.dp
                                                                        )
                                                                    )
                                                                }
                                                                Text(
                                                                    text = formatShortDuration(audio.durationMs),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = TextSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSectionTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) CyanContainer else StudioPanel,
        border = BorderStroke(1.dp, if (selected) CyanSignal else StudioBorder),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) CyanSignal else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) CyanSignal else TextSecondary
            )
        }
    }
}

@Composable
fun ImportedAudioStudioCard(
    playbackState: ImportedPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onChangeSource: () -> Unit,
    onClearSource: () -> Unit,
    onSaveAndShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = playbackState.source

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("imported_audio_studio_card"),
        shape = MaterialTheme.shapes.large,
        color = StudioPanel,
        border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (playbackState.isImporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = CyanSignal,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Extracting & resampling audio to 48 kHz...",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (source != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyanContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (source.fromVideo) Icons.Filled.Movie else Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = CyanSignal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${source.formatLabel} • Real-Time 48 kHz DSP",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSignal
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onChangeSource) {
                            Text("Replace", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                        }
                        IconButton(onClick = onClearSource, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear imported audio",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Transport Row: Play/Pause/Resume + Seek Timeline + Current/Total Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (playbackState.isPlaying) CyanSignal else StudioPanelElevated)
                            .testTag("imported_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) {
                                stringResource(R.string.cd_pause_imported)
                            } else {
                                stringResource(R.string.cd_play_imported)
                            },
                            tint = if (playbackState.isPlaying) StudioRack else CyanSignal
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = playbackState.positionMs.toFloat()
                                .coerceIn(0f, playbackState.durationMs.toFloat().coerceAtLeast(1f)),
                            onValueChange = { onSeek(it.toLong()) },
                            valueRange = 0f..playbackState.durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = CyanSignal,
                                activeTrackColor = CyanSignal,
                                inactiveTrackColor = StudioRack
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("imported_seek_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatShortDuration(playbackState.positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSignal
                            )
                            Text(
                                text = formatShortDuration(playbackState.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Text(
                    text = "Effects and Voice Changer update live without resetting playback position.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                if (playbackState.isExporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { playbackState.exportProgress },
                            color = EmeraldSafe,
                            trackColor = StudioRack,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Rendering full processed WAV (${(playbackState.exportProgress * 100).toInt()}%)...",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSafe
                        )
                    }
                }

                // Bottom Save Audio button inside imported editor (hidden until audio imported)
                Button(
                    onClick = onSaveAndShare,
                    enabled = !playbackState.isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldContainer,
                        contentColor = EmeraldSafe
                    ),
                    border = BorderStroke(1.dp, EmeraldSafe),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.IosShare,
                        contentDescription = stringResource(R.string.cd_save_export),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (playbackState.isExporting) "Rendering Processed Audio..." else stringResource(R.string.save_audio),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}
