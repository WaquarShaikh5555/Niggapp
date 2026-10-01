package com.smilebeat.ui.screens

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smilebeat.camera.CameraController
import com.smilebeat.detection.ToneTriggerController
import com.smilebeat.ui.components.CameraPreview
import com.smilebeat.ui.components.ToneMeter
import com.smilebeat.ui.theme.*
import com.smilebeat.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraController by remember { mutableStateOf<CameraController?>(null) }

    // Handle lifecycle for camera stop when backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                cameraController?.stopCamera()
                viewModel.setCameraActive(false)
                viewModel.onAppBackgrounded()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                previewViewRef?.let { pv ->
                    cameraController?.startCamera(pv)
                    viewModel.setCameraActive(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraController?.release()
        }
    }

    // Initialize camera controller when preview ready
    LaunchedEffect(previewViewRef) {
        previewViewRef?.let { pv ->
            if (cameraController == null) {
                val controller = CameraController(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    faceDetector = viewModel.getFaceDetector(),
                    toneAnalyzer = viewModel.getToneAnalyzer(),
                    onAnalysisResult = { result ->
                        viewModel.onFrameAnalyzed(
                            hasFace = result.hasFace,
                            faceCount = result.faceCount,
                            toneScore = result.toneResult?.toneScore,
                            confidence = result.toneResult?.confidence ?: 0f,
                            isPoorLighting = result.toneResult?.isPoorLighting ?: false
                        )
                    },
                    onError = { err ->
                        viewModel.setError(err)
                    }
                )
                cameraController = controller
                controller.startCamera(pv)
                viewModel.setCameraActive(true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackVoid)
    ) {
        // Camera Preview Background (if enabled)
        if (uiState.settings.previewEnabled) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = this
                        // Start camera if controller exists, else will be started in LaunchedEffect
                        cameraController?.startCamera(this) ?: run {
                            // Create controller now
                            val controller = CameraController(
                                context = ctx,
                                lifecycleOwner = lifecycleOwner,
                                faceDetector = viewModel.getFaceDetector(),
                                toneAnalyzer = viewModel.getToneAnalyzer(),
                                onAnalysisResult = { result ->
                                    viewModel.onFrameAnalyzed(
                                        hasFace = result.hasFace,
                                        faceCount = result.faceCount,
                                        toneScore = result.toneResult?.toneScore,
                                        confidence = result.toneResult?.confidence ?: 0f,
                                        isPoorLighting = result.toneResult?.isPoorLighting ?: false
                                    )
                                },
                                onError = { err -> viewModel.setError(err) }
                            )
                            cameraController = controller
                            controller.startCamera(this)
                            viewModel.setCameraActive(true)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dark overlay gradient for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BlackVoid.copy(alpha = 0.7f),
                                Color.Transparent,
                                BlackVoid.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera active indicator
            if (uiState.isCameraActive) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CameraActiveGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Camera active • On-device only",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row {
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceDark.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = if (uiState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceDark.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Center Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status chips
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Music status big
                AnimatedContent(
                    targetState = uiState.musicStatusText,
                    transitionSpec = {
                        fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                    },
                    label = "musicStatus"
                ) { status ->
                    Text(
                        text = status,
                        color = when (status) {
                            "VIBING" -> NeonRed
                            "COOLDOWN" -> TextSecondary
                            else -> NeonPurple
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (status) {
                                    "VIBING" -> NeonRed.copy(alpha = 0.15f)
                                    else -> SurfaceDark.copy(alpha = 0.6f)
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Face status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        text = when {
                            !uiState.hasFace -> "Searching for face"
                            uiState.isPoorLighting -> "Poor lighting"
                            uiState.faceCount > 1 -> "Multiple faces — analyzing largest"
                            else -> "Face detected"
                        },
                        color = when {
                            !uiState.hasFace -> TextTertiary
                            uiState.isPoorLighting -> Color(0xFFFFA500)
                            else -> NeonCyan
                        }
                    )
                    if (uiState.isAnalyzingLargestFace) {
                        StatusChip(text = "Largest face", color = NeonPurple)
                    }
                }
            }

            // Tone Meter with glow border
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(SurfaceDark.copy(alpha = 0.7f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                NeonPurple.copy(alpha = 0.5f),
                                NeonRed.copy(alpha = 0.3f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ToneMeter(
                    toneScore = uiState.toneScore,
                    triggerState = uiState.triggerState,
                    threshold = uiState.settings.toneThreshold
                )
            }

            // Bottom controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Privacy note
                Text(
                    text = "Your camera stays on your device. SmileBeat analyzes the current camera image locally and does not save or upload your face.",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark.copy(alpha = 0.5f))
                        .padding(12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flip camera
                    FloatingActionButton(
                        onClick = {
                            previewViewRef?.let { pv ->
                                cameraController?.flipCamera(pv)
                                viewModel.flipCamera()
                            }
                        },
                        containerColor = SurfaceVariant,
                        contentColor = TextPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip camera"
                        )
                    }

                    // Cooldown indicator if needed
                    if (uiState.triggerState == ToneTriggerController.TriggerState.COOLDOWN) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    progress = (uiState.cooldownRemaining / (uiState.settings.cooldownSeconds * 1000f)).coerceIn(0f, 1f),
                                    modifier = Modifier.size(20.dp),
                                    color = NeonPurple,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${uiState.cooldownRemaining / 1000 + 1}s",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Volume quick slider? For now mute already
                    FloatingActionButton(
                        onClick = onNavigateToSettings,
                        containerColor = NeonPurple,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings"
                        )
                    }
                }
            }
        }

        // Error snackbar
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss", color = NeonPurple)
                    }
                },
                containerColor = SurfaceVariant,
                contentColor = TextPrimary
            ) {
                Text(text = error, fontSize = 12.sp)
            }
        }

        // Vibing pulse overlay
        if (uiState.triggerState == ToneTriggerController.TriggerState.TRIGGERED) {
            val infinite = rememberInfiniteTransition(label = "vibe")
            val alpha by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 0.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "vibeAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonRed.copy(alpha = alpha),
                                Color.Transparent
                            ),
                            radius = 800f
                        )
                    )
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
