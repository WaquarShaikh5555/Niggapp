package com.smilebeat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smilebeat.data.DetectionMode
import com.smilebeat.ui.theme.*
import com.smilebeat.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val availableTracks = remember { viewModel.getAvailableTracks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlackVoid,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BlackVoid,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Privacy card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Your camera stays on your device. SmileBeat analyzes the current camera image locally and does not save or upload your face.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Detection Mode
            SettingsSection(title = "Detection Mode", icon = Icons.Default.Visibility) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetectionModeOption(
                        title = "Darker visible tone",
                        description = "Triggers when apparent skin-tone brightness is below threshold (visual color analysis only)",
                        selected = settings.detectionMode == DetectionMode.DARKER_VISIBLE_TONE,
                        onClick = { viewModel.updateDetectionMode(DetectionMode.DARKER_VISIBLE_TONE) }
                    )
                    DetectionModeOption(
                        title = "Experimental inverted",
                        description = "Triggers when tone is brighter than threshold (for testing)",
                        selected = settings.detectionMode == DetectionMode.EXPERIMENTAL_INVERTED,
                        onClick = { viewModel.updateDetectionMode(DetectionMode.EXPERIMENTAL_INVERTED) }
                    )
                }
            }

            // Tone Sensitivity
            SettingsSection(title = "Tone Sensitivity / Threshold", icon = Icons.Default.Tune) {
                Column {
                    Text(
                        text = "Threshold describes measurable image brightness (HSV Value + luma), not racial category. Lower = more sensitive to darker-appearing tones.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dark",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.2f", settings.toneThreshold),
                            color = NeonPurple,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "Bright",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.toneThreshold,
                        onValueChange = { viewModel.updateThreshold(it) },
                        valueRange = 0.05f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPurple,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = SurfaceGlow
                        )
                    )
                    Text(
                        text = "Trigger when tone score <= threshold. Requires 5 consecutive frames.",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }

            // Volume
            SettingsSection(title = "Volume", icon = Icons.Default.VolumeUp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(settings.volume * 100).toInt()}%",
                            color = NeonCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Switch(
                            checked = !settings.isMuted,
                            onCheckedChange = { enabled -> viewModel.updateMuted(!enabled) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Slider(
                        value = settings.volume,
                        onValueChange = { viewModel.updateVolume(it) },
                        valueRange = 0f..1f,
                        enabled = !settings.isMuted,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = SurfaceGlow
                        )
                    )
                    Text(
                        text = "Controls app player volume only. Does not modify system volume.",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }

            // Cooldown
            SettingsSection(title = "Cooldown Duration", icon = Icons.Default.Timer) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Duration", color = TextSecondary, fontSize = 14.sp)
                        Text(
                            text = "${settings.cooldownSeconds}s",
                            color = NeonRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Slider(
                        value = settings.cooldownSeconds.toFloat(),
                        onValueChange = { viewModel.updateCooldown(it.toInt()) },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonRed,
                            activeTrackColor = NeonRed,
                            inactiveTrackColor = SurfaceGlow
                        )
                    )
                    Text(
                        text = "Prevents immediate retrigger after music stops. Default 3s.",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }

            // Track Picker
            SettingsSection(title = "Track Picker", icon = Icons.Default.MusicNote) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "All tracks from res/raw. Replace placeholder wavs with your licensed phonk tracks.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    availableTracks.forEach { trackName ->
                        val isSelected = settings.selectedTrack == trackName
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) NeonPurple.copy(alpha = 0.2f) else SurfaceDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) NeonPurple else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.updateTrack(trackName) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isSelected) NeonPurple else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = trackName,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Bundled • On-device",
                                            color = TextTertiary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = NeonPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Camera preview toggle
            SettingsSection(title = "Camera Preview", icon = Icons.Default.Videocam) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Show camera preview", color = TextPrimary, fontSize = 14.sp)
                        Text(
                            text = "Detection remains active even when preview is off",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.previewEnabled,
                        onCheckedChange = { viewModel.updatePreviewEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonPurple,
                            checkedTrackColor = NeonPurple.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SmileBeat v1.0 • 100% on-device • No analytics • No face storage",
                color = TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(NeonPurple.copy(alpha = 0.2f), Color.Transparent)
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetectionModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) NeonPurple.copy(alpha = 0.15f) else SurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) NeonPurple else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = NeonPurple,
                    unselectedColor = TextTertiary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = if (selected) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
