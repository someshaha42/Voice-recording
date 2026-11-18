package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.RecordingStatus
import com.example.data.RecordingItem
import com.example.ui.StudioTab
import com.example.ui.VoxForgeViewModel
import com.example.ui.screens.EffectsScreen
import com.example.ui.screens.RecorderScreen
import com.example.ui.screens.VoiceChangerScreen
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.RecordCoral
import com.example.ui.theme.RecordCoralContainer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoxForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            VoxForgeTheme {
                VoxForgeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxForgeApp(
    viewModel: VoxForgeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
    val hardwareState by viewModel.hardwareState.collectAsStateWithLifecycle()
    val recordingStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val metrics by viewModel.liveMetrics.collectAsStateWithLifecycle()
    val voiceChangerConfig by viewModel.voiceChangerConfig.collectAsStateWithLifecycle()
    val monitoringConfig by viewModel.monitoringConfig.collectAsStateWithLifecycle()
    val effectsConfig by viewModel.effectsConfig.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(micGranted)
    }

    val requestPermissions = {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(granted)
        if (!granted) {
            requestPermissions()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.setAppForeground(true)
                }
                Lifecycle.Event.ON_RESUME -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.setPermissionGranted(granted)
                    viewModel.refreshDevices()
                    viewModel.setAppForeground(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.setAppForeground(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setAppForeground(false)
        }
    }

    val openAppSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    val shareRecording: (RecordingItem) -> Unit = { item ->
        val uri = viewModel.getShareUri(item)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export ${item.fileName}"))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = StudioRack,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    StudioTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { TabIcon(tab = tab, selected = isSelected) },
                            label = { Text(tabLabel(tab)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanSignal,
                                selectedTextColor = CyanSignal,
                                indicatorColor = CyanContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = StudioObsidian,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = StudioRack,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                if (recordingStatus == RecordingStatus.RECORDING) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = RecordCoralContainer,
                                        border = BorderStroke(1.dp, RecordCoral)
                                    ) {
                                        Text(
                                            text = "REC",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RecordCoral,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (monitoringConfig.livePreviewEnabled) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = CyanContainer,
                                        border = BorderStroke(1.dp, CyanSignal)
                                    ) {
                                        Text(
                                            text = "PREVIEW",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanSignal,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = StudioRack,
                            tonalElevation = 0.dp
                        ) {
                            StudioTab.entries.forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { TabIcon(tab = tab, selected = isSelected) },
                                    label = { Text(tabLabel(tab)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanSignal,
                                        selectedTextColor = CyanSignal,
                                        indicatorColor = CyanContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    statusBanner?.let { bannerMsg ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.small,
                            color = StudioPanel,
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = bannerMsg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = viewModel::dismissBanner) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss notification",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    when (selectedTab) {
                        StudioTab.RECORDER -> RecorderScreen(
                            hasMicPermission = hasMicPermission,
                            hardwareState = hardwareState,
                            recordingStatus = recordingStatus,
                            metrics = metrics,
                            voiceChangerConfig = voiceChangerConfig,
                            effectsConfig = effectsConfig,
                            monitoringConfig = monitoringConfig,
                            recordings = recordings,
                            playbackState = playbackState,
                            onRequestMicPermission = requestPermissions,
                            onOpenAppSettings = openAppSettings,
                            onRefreshDevices = viewModel::refreshDevices,
                            onStartRecording = viewModel::startRecording,
                            onPauseRecording = viewModel::pauseRecording,
                            onResumeRecording = viewModel::resumeRecording,
                            onStopRecording = viewModel::stopRecording,
                            onResetClips = viewModel::resetClipCounter,
                            onTogglePlayback = viewModel::togglePlayback,
                            onSeekPlayback = viewModel::seekPlayback,
                            onRenameRecording = viewModel::renameRecording,
                            onDeleteRecording = viewModel::deleteRecording,
                            onShareRecording = shareRecording
                        )

                        StudioTab.VOICE_CHANGER -> VoiceChangerScreen(
                            config = voiceChangerConfig,
                            monitoringConfig = monitoringConfig,
                            hardwareState = hardwareState,
                            metrics = metrics,
                            onEnabledChanged = viewModel::setVoiceChangerEnabled,
                            onSelectPreset = viewModel::selectVoicePreset,
                            onResetPreset = viewModel::resetVoicePresetToDefault,
                            onPitchLevelChanged = viewModel::updateVoicePitchLevel,
                            onToneLevelChanged = viewModel::updateVoiceToneLevel,
                            onClarityChanged = viewModel::updateVoiceClarity,
                            onLivePreviewToggle = viewModel::setLiveVoicePreviewEnabled,
                            onMonitoringDelayChanged = viewModel::updateMonitoringDelaySlider,
                            onResetMonitoringDelay = viewModel::resetMonitoringDelayToDefault,
                            onMonitoringVolumeChanged = viewModel::updateMonitoringVolume,
                            onEchoCancellationToggle = viewModel::setEchoCancellationEnabled
                        )

                        StudioTab.EFFECTS -> EffectsScreen(
                            config = effectsConfig,
                            onNoiseGateChanged = viewModel::updateNoiseGateConfig,
                            onLowPassChanged = viewModel::updateLowPassConfig,
                            onHighPassChanged = viewModel::updateHighPassConfig,
                            onEqualizerChanged = viewModel::updateEqualizerConfig,
                            onReverbChanged = viewModel::updateReverbConfig,
                            onEchoChanged = viewModel::updateEchoConfig,
                            onHallChanged = viewModel::updateHallConfig
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: StudioTab, selected: Boolean) {
    val icon = when (tab) {
        StudioTab.RECORDER -> if (selected) Icons.Filled.Mic else Icons.Outlined.Mic
        StudioTab.VOICE_CHANGER -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
        StudioTab.EFFECTS -> if (selected) Icons.Filled.GraphicEq else Icons.Outlined.GraphicEq
    }
    Icon(imageVector = icon, contentDescription = tabLabel(tab))
}

@Composable
private fun tabLabel(tab: StudioTab): String = when (tab) {
    StudioTab.RECORDER -> stringResource(R.string.tab_recorder)
    StudioTab.VOICE_CHANGER -> stringResource(R.string.tab_voice_changer)
    StudioTab.EFFECTS -> stringResource(R.string.tab_effects)
}
