package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioConfig
import com.example.audio.AudioHardwareState
import com.example.audio.LiveAudioMetrics
import com.example.audio.MonitoringConfig
import com.example.audio.PlaybackUiState
import com.example.audio.RecordingStatus
import com.example.data.RecordingItem
import com.example.dsp.NoiseGateConfig
import com.example.dsp.VoiceChangerConfig
import com.example.ui.components.HardwareStatusCard
import com.example.ui.components.StudioLevelMeterCard
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RecordCoral
import com.example.ui.theme.RecordCoralContainer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPanelElevated
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecorderScreen(
    hasMicPermission: Boolean,
    hardwareState: AudioHardwareState,
    recordingStatus: RecordingStatus,
    metrics: LiveAudioMetrics,
    voiceChangerConfig: VoiceChangerConfig,
    noiseGateConfig: NoiseGateConfig,
    monitoringConfig: MonitoringConfig,
    recordings: List<RecordingItem>,
    playbackState: PlaybackUiState,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefreshDevices: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onResetClips: () -> Unit,
    onTogglePlayback: (RecordingItem) -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onRenameRecording: (RecordingItem, String) -> Unit,
    onDeleteRecording: (RecordingItem) -> Unit,
    onShareRecording: (RecordingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingItem?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasMicPermission) {
                item {
                    PermissionPromptCard(
                        onGrantClick = onRequestMicPermission,
                        onOpenSettings = onOpenAppSettings
                    )
                }
            }

            item {
                HardwareStatusCard(
                    hardwareState = hardwareState,
                    onRefreshDevices = onRefreshDevices
                )
            }

            item {
                TransportConsoleCard(
                    hasMicPermission = hasMicPermission,
                    recordingStatus = recordingStatus,
                    durationMs = metrics.recordingDurationMs,
                    voiceChangerConfig = voiceChangerConfig,
                    noiseGateConfig = noiseGateConfig,
                    monitoringConfig = monitoringConfig,
                    onStartRecording = onStartRecording,
                    onPauseRecording = onPauseRecording,
                    onResumeRecording = onResumeRecording,
                    onStopRecording = onStopRecording
                )
            }

            item {
                StudioLevelMeterCard(
                    metrics = metrics,
                    onResetClips = onResetClips
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECORDED VOICEOVERS (${recordings.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "48 kHz WAV PCM",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            if (recordings.isEmpty()) {
                item {
                    EmptyRecordingsCard()
                }
            } else {
                items(recordings, key = { it.id }) { item ->
                    val isCurrent = playbackState.activeFilePath == item.filePath
                    RecordingFileCard(
                        item = item,
                        isActive = isCurrent,
                        isPlaying = isCurrent && playbackState.isPlaying,
                        playbackPositionMs = if (isCurrent) playbackState.positionMs else 0L,
                        playbackDurationMs = if (isCurrent && playbackState.durationMs > 0L) {
                            playbackState.durationMs
                        } else {
                            item.durationMs
                        },
                        onTogglePlay = { onTogglePlayback(item) },
                        onSeek = onSeekPlayback,
                        onRenameClick = { renameTarget = item },
                        onShareClick = { onShareRecording(item) },
                        onDeleteClick = { deleteTarget = item }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    renameTarget?.let { target ->
        RenameRecordingDialog(
            item = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameRecording(target, newName)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = StudioPanel,
            title = { Text("Delete Recording?") },
            text = {
                Text(
                    "Remove \"${target.fileName}\" permanently from device storage?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecording(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordCoral)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionPromptCard(
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = RecordCoralContainer,
        border = BorderStroke(1.dp, RecordCoral)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.mic_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.mic_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RecordCoral),
                    modifier = Modifier.testTag("grant_mic_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.mic_permission_grant))
                }
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.mic_permission_settings))
                }
            }
        }
    }
}

@Composable
private fun TransportConsoleCard(
    hasMicPermission: Boolean,
    recordingStatus: RecordingStatus,
    durationMs: Long,
    voiceChangerConfig: VoiceChangerConfig,
    noiseGateConfig: NoiseGateConfig,
    monitoringConfig: MonitoringConfig,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val statusLabel = when (recordingStatus) {
        RecordingStatus.IDLE -> "READY"
        RecordingStatus.RECORDING -> "RECORDING"
        RecordingStatus.PAUSED -> "PAUSED"
        RecordingStatus.INTERRUPTED_PAUSED -> "INTERRUPTED — PAUSED"
    }
    val statusColor = when (recordingStatus) {
        RecordingStatus.IDLE -> TextSecondary
        RecordingStatus.RECORDING -> RecordCoral
        RecordingStatus.PAUSED -> AmberCaution
        RecordingStatus.INTERRUPTED_PAUSED -> AmberCaution
    }
    val statusBg = when (recordingStatus) {
        RecordingStatus.IDLE -> StudioRack
        RecordingStatus.RECORDING -> RecordCoralContainer
        RecordingStatus.PAUSED -> AmberContainer
        RecordingStatus.INTERRUPTED_PAUSED -> AmberContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = StudioPanel,
        border = BorderStroke(
            1.dp,
            if (recordingStatus == RecordingStatus.RECORDING) RecordCoral else StudioBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("recording_status_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                    }
                }

                // Fixed Read-Only Studio Output Specification
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StudioRack,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Text(
                        text = "${AudioConfig.OUTPUT_FORMAT_LABEL} • 48 kHz • 16-bit Mono • ${AudioConfig.OUTPUT_BITRATE_KBPS} kbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            // Tabular Recording Timer
            Text(
                text = formatTimerMs(durationMs),
                style = MaterialTheme.typography.displaySmall,
                color = if (recordingStatus == RecordingStatus.RECORDING) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    TextSecondary
                },
                modifier = Modifier.testTag("recording_timer")
            )

            // Active shared DSP summary pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                val voiceLabel = if (voiceChangerConfig.bypass) {
                    "Voice: Bypass"
                } else {
                    "Voice: ${voiceChangerConfig.preset.displayName}"
                }
                PipelinePill(
                    text = voiceLabel,
                    active = !voiceChangerConfig.bypass,
                    activeColor = CyanSignal
                )
                PipelinePill(
                    text = if (noiseGateConfig.enabled) "Gate: ON" else "Gate: OFF",
                    active = noiseGateConfig.enabled,
                    activeColor = EmeraldSafe
                )
                PipelinePill(
                    text = if (monitoringConfig.livePreviewEnabled) "Preview: ON" else "Preview: OFF",
                    active = monitoringConfig.livePreviewEnabled,
                    activeColor = CyanSignal
                )
            }

            // Transport Controls (ONLY ONE Start Recording button in the entire app)
            if (recordingStatus == RecordingStatus.IDLE) {
                Button(
                    onClick = onStartRecording,
                    enabled = hasMicPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecordCoral,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = stringResource(R.string.cd_start_recording),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Recording",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (recordingStatus == RecordingStatus.RECORDING) {
                        FilledTonalButton(
                            onClick = onPauseRecording,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AmberContainer,
                                contentColor = AmberCaution
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("pause_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.cd_pause_recording)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        Button(
                            onClick = onResumeRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldSafe,
                                contentColor = StudioRack
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("resume_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.cd_resume_recording)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    Button(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecordCoral,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("stop_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.cd_stop_recording)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop & Save", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelinePill(
    text: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) StudioPanelElevated else StudioRack,
        border = BorderStroke(1.dp, if (active) activeColor.copy(alpha = 0.5f) else StudioBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else TextMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyRecordingsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = CyanSignal,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "No Voiceovers Recorded Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure your Noise Gate and Voice Changer tabs, connect headphones for Live Voice Preview, and press Start Recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RecordingFileCard(
    item: RecordingItem,
    isActive: Boolean,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateStr = remember(item.createdAtMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.createdAtMs))
    }
    val sizeKb = (item.sizeBytes / 1024L).coerceAtLeast(1L)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(1.dp, if (isActive) CyanSignal else StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) CyanContainer else StudioRack)
                        .testTag("play_recording_${item.fileName}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.cd_pause_playback)
                        } else {
                            stringResource(R.string.cd_play_recording)
                        },
                        tint = CyanSignal
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatShortDuration(item.durationMs)} • ${sizeKb} KB • $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (item.wasRecovered) {
                        Text(
                            text = "Recovered from interruption",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSafe
                        )
                    }
                }

                IconButton(onClick = onRenameClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_rename_recording),
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onShareClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.cd_share_recording),
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.cd_delete_recording),
                        tint = RecordCoral,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (isActive && playbackDurationMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatShortDuration(playbackPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSignal
                    )
                    Slider(
                        value = playbackPositionMs.toFloat().coerceIn(0f, playbackDurationMs.toFloat()),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..playbackDurationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanSignal,
                            activeTrackColor = CyanSignal,
                            inactiveTrackColor = StudioRack
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatShortDuration(playbackDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameRecordingDialog(
    item: RecordingItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember(item) {
        mutableStateOf(item.fileName.removeSuffix(".wav").removeSuffix(".WAV"))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioPanel,
        title = { Text("Rename Recording") },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                label = { Text("File name (.wav)") }
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput) },
                enabled = nameInput.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatTimerMs(ms: Long): String {
    val totalTenths = (ms / 100L).coerceAtLeast(0L)
    val tenths = totalTenths % 10L
    val totalSec = totalTenths / 10L
    val sec = totalSec % 60L
    val min = (totalSec / 60L) % 60L
    val hr = totalSec / 3600L
    return String.format(Locale.US, "%02d:%02d:%02d.%d", hr, min, sec, tenths)
}

fun formatShortDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val sec = totalSec % 60L
    val min = totalSec / 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
