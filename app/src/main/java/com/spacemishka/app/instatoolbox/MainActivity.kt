package com.spacemishka.app.instatoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spacemishka.app.instatoolbox.ui.theme.InstaToolboxTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Movie
import com.spacemishka.app.instatoolbox.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val permissionsToRequest = remember {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.RECORD_AUDIO
                    )
                } else {
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.RECORD_AUDIO
                    )
                }
            }
            
            fun checkPermission(ctx: android.content.Context): Boolean {
                val audioGranted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    audioGranted
                } else {
                    androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    audioGranted
                }
            }

            var hasPermission by remember { mutableStateOf(checkPermission(context)) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsMap ->
                val imagesGranted = permissionsMap[android.Manifest.permission.READ_MEDIA_IMAGES] ?: false
                val videoGranted = permissionsMap[android.Manifest.permission.READ_MEDIA_VIDEO] ?: false
                val extGranted = permissionsMap[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
                val audioGranted = permissionsMap[android.Manifest.permission.RECORD_AUDIO] ?: false
                
                hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    imagesGranted && videoGranted && audioGranted
                } else {
                    extGranted && audioGranted
                }
            }

            LaunchedEffect(Unit) {
                if (!checkPermission(context)) {
                    permissionLauncher.launch(permissionsToRequest)
                }
            }
            InstaToolboxTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ObsidianBlack
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        hasPermission = hasPermission,
                        permissionLauncher = permissionLauncher,
                        permissionsToRequest = permissionsToRequest
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun NoCropImageScreenPreview() {
    InstaToolboxTheme {
        NoCropImageScreen(hasPermission = true, onBack = {})
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    permissionsToRequest: Array<String>
) {
    var selectedScreen by remember { mutableStateOf<String?>(null) }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "InstaToolbox requires storage and audio permissions to process media and generate subtitles.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { permissionLauncher.launch(permissionsToRequest) },
                colors = ButtonDefaults.buttonColors(containerColor = CreatorSunsetPink)
            ) {
                Text("Grant Permissions", color = ComposeColor.White)
            }
        }
    } else if (selectedScreen == "NoCrop") {
        NoCropImageScreen(
            modifier = modifier.background(ObsidianBlack),
            hasPermission = hasPermission,
            onBack = { selectedScreen = null }
        )
    } else if (selectedScreen == "Swipeable") {
        SwipeablePhotoGeneratorScreen(
            modifier = modifier.background(ObsidianBlack),
            hasPermission = hasPermission,
            onBack = { selectedScreen = null }
        )
    } else if (selectedScreen == "VideoSubtitle") {
        VideoSubtitleScreen(
            modifier = modifier.background(ObsidianBlack),
            hasPermission = hasPermission,
            onBack = { selectedScreen = null }
        )
    } else if (selectedScreen == "NineGrid") {
        NineGridProfileSlicerScreen(
            modifier = modifier.background(ObsidianBlack),
            hasPermission = hasPermission,
            onBack = { selectedScreen = null }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sunset Gradient Title
            val sunsetBrush = Brush.linearGradient(
                colors = listOf(CreatorSunsetPink, CreatorSunsetOrange, CreatorSunsetYellow)
            )
            Text(
                text = "InstaToolbox",
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = sunsetBrush,
                    fontWeight = FontWeight.Black
                ),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Let's craft your next viral post",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
                fontWeight = FontWeight.Medium
            )

            // Tool selections
            CreatorToolCard(
                title = "No-Crop Post Maker",
                subtitle = "Fit full landscape/portrait photos into square grids with elegant background frames.",
                icon = Icons.Default.AspectRatio,
                gradientColors = listOf(CreatorSunsetPink, CreatorSunsetPurple),
                onClick = { selectedScreen = "NoCrop" }
            )

            CreatorToolCard(
                title = "Swipeable Carousel Slicer",
                subtitle = "Split landscape panoramas into seamless square segments for multi-image feed carousels.",
                icon = Icons.Default.PhotoLibrary,
                gradientColors = listOf(CreatorSunsetPurple, CreatorSunsetOrange),
                onClick = { selectedScreen = "Swipeable" }
            )

            CreatorToolCard(
                title = "AI Video Subtitler",
                subtitle = "Transcribe speech automatically using local AI and generate stylish synced subtitles.",
                icon = Icons.Default.Subtitles,
                gradientColors = listOf(CreatorSunsetOrange, CreatorSunsetYellow),
                onClick = { selectedScreen = "VideoSubtitle" }
            )

            CreatorToolCard(
                title = "9-Grid Profile Slicer",
                subtitle = "Divide photos into a grid of 9 segments for layout profile displays.",
                icon = Icons.Default.GridOn,
                gradientColors = listOf(CreatorSunsetOrange, CreatorSunsetPink),
                onClick = { selectedScreen = "NineGrid" },
                enabled = true
            )

            CreatorToolCard(
                title = "Reels Video Splitter",
                subtitle = "Cut long videos into exact 15/30 second clips optimized for stories and reels.",
                icon = Icons.Default.Movie,
                gradientColors = listOf(TextMuted, TextMuted),
                onClick = {},
                enabled = false
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Designed for content creators. Version 1.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun CreatorToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<ComposeColor>,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1.0f else 0.4f
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassySurface
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) CardBorder else CardBorder.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(gradientColors),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ComposeColor.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary.copy(alpha = alpha),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary.copy(alpha = alpha)
                )
            }
            
            if (enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
