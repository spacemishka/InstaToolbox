package com.spacemishka.app.instatoolbox

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Translate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import com.spacemishka.app.instatoolbox.ui.theme.*

@Composable
fun VideoSubtitleScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean = true,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleText by remember { mutableStateOf(TextFieldValue("")) }
    var fontSize by remember { mutableStateOf(48f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var subtitles by remember { mutableStateOf<List<VideoSubtitler.Subtitle>>(emptyList()) }
    var isGenerating by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var selectedSubtitleIndex by remember { mutableStateOf<Int?>(null) }
    var useAdvancedRecognition by remember { mutableStateOf(false) }
    var downloadStatusText by remember { mutableStateOf("") }
    var isDownloadingModel by remember { mutableStateOf(false) }

    // Live Video Player Position Tracking
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var currentPlaybackPosition by remember { mutableStateOf(0L) }

    // Custom Typography Presets
    val fontStylePresets = listOf("Plain", "Neon Glow", "Retro Sunset", "Bold Impact")
    var selectedFontStylePreset by remember { mutableStateOf("Plain") }

    val fontColors = listOf(ComposeColor.White, ComposeColor.Yellow, AccentCyan, CreatorSunsetPink, ComposeColor.Green)
    var selectedFontColor by remember { mutableStateOf(ComposeColor.White) }

    // Language list configuration
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }
    val languages = remember {
        listOf(
            Pair("English (US)", Locale.US),
            Pair("German (DE)", Locale.GERMANY),
            Pair("Spanish (ES)", Locale("es", "ES")),
            Pair("French (FR)", Locale.FRANCE),
            Pair("Italian (IT)", Locale.ITALY),
            Pair("Chinese (ZH)", Locale.CHINA)
        )
    }
    var selectedLanguage by remember { mutableStateOf(languages[0]) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            videoUri = uri
            subtitleText = TextFieldValue("")
            previewBitmap = null
            exportStatus = null
            subtitles = emptyList()
            currentPlaybackPosition = 0L
            selectedSubtitleIndex = null
        }
    }

    // SAF File Creator Launchers
    val saveVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            exportStatus = "Exporting video..."
            coroutineScope.launch {
                val result = VideoSubtitler.exportVideoWithSubtitlesMux(
                    context = context,
                    inputUri = videoUri!!,
                    subtitles = subtitles,
                    fontStyle = selectedFontStylePreset,
                    fontSize = fontSize,
                    fontColor = selectedFontColor.toArgb(),
                    outputUri = uri
                )
                exportStatus = if (result) "Video exported successfully with embedded subtitles!" else "Export failed"
                isExporting = false
            }
        }
    }

    val saveSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val srtContent = VideoSubtitler.generateSRT(subtitles)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(srtContent.toByteArray(Charsets.UTF_8))
                    }
                    exportStatus = "Subtitles (.srt) saved successfully!"
                } catch (e: Exception) {
                    exportStatus = "Failed to save subtitles: ${e.message}"
                }
            }
        }
    }

    // Polling Coroutine to update Video Playback position
    LaunchedEffect(videoUri, subtitles) {
        if (videoUri != null) {
            while (true) {
                delay(100)
                videoViewRef?.let {
                    if (it.isPlaying) {
                        currentPlaybackPosition = it.currentPosition.toLong()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = GlassySurface)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "AI Video Subtitler",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        if (videoUri == null) {
            // Import placeholder box
            Card(
                onClick = { if (hasPermission) videoPicker.launch("video/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = GlassySurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(CreatorSunsetOrange, CreatorSunsetYellow)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = ComposeColor.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Import Video Clip",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select an MP4/MOV clip to auto-generate timelines and captions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Video preview Card with subtitle overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GlassySurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(ComposeColor.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setOnPreparedListener { it.isLooping = true; start() }
                                videoViewRef = this
                            }
                        },
                        update = { view ->
                            view.setVideoURI(videoUri)
                            videoViewRef = view
                        },
                        onRelease = { view ->
                            view.stopPlayback()
                            if (videoViewRef == view) {
                                videoViewRef = null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Dynamic Subtitle overlay directly on top of video player!
                    val activeSubtitle = subtitles.find { currentPlaybackPosition in it.startTime..it.endTime }
                    activeSubtitle?.let { sub ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                                .background(
                                    color = if (selectedFontStylePreset == "Bold Impact") ComposeColor.Black.copy(alpha = 0.8f) else ComposeColor.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = sub.text,
                                color = when (selectedFontStylePreset) {
                                    "Neon Glow" -> AccentCyan
                                    "Retro Sunset" -> CreatorSunsetYellow
                                    else -> selectedFontColor
                                },
                                fontSize = (fontSize / 2.5f).sp, // Scale down to fit video view frame cleanly
                                fontWeight = if (selectedFontStylePreset == "Plain") FontWeight.Normal else FontWeight.Bold,
                                fontStyle = if (selectedFontStylePreset == "Neon Glow") androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                fontFamily = when (selectedFontStylePreset) {
                                    "Retro Sunset" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                    else -> androidx.compose.ui.text.font.FontFamily.Default
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = if (selectedFontStylePreset == "Neon Glow") {
                                        androidx.compose.ui.graphics.Shadow(
                                            color = AccentCyan,
                                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            blurRadius = 12f
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.Shadow(
                                            color = ComposeColor.Black,
                                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                            blurRadius = 4f
                                        )
                                    }
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Language Selector Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GlassySurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Language Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = AccentCyan
                            )
                            Text(
                                "Transcription Language", 
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box {
                            Button(
                                onClick = { isLanguageDropdownExpanded = true }, 
                                enabled = !isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianBlack),
                                border = BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(selectedLanguage.first, color = TextPrimary)
                            }
                            DropdownMenu(
                                expanded = isLanguageDropdownExpanded,
                                onDismissRequest = { isLanguageDropdownExpanded = false }
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.first) },
                                        onClick = {
                                            selectedLanguage = lang
                                            isLanguageDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = CardBorder)

                    // Recognition Quality Mode Selector Row
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Speech Model Quality",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Fast/Standard Chip
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (useAdvancedRecognition) ObsidianBlack else CreatorSunsetPink,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (useAdvancedRecognition) CardBorder else CreatorSunsetPink,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = !isGenerating) { useAdvancedRecognition = false }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Standard (Basic)",
                                        color = if (useAdvancedRecognition) TextSecondary else ComposeColor.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Faster, lower memory",
                                        color = if (useAdvancedRecognition) TextMuted else ComposeColor.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // AI-Enhanced / Advanced Chip
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (useAdvancedRecognition) CreatorSunsetPink else ObsidianBlack,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (useAdvancedRecognition) CreatorSunsetPink else CardBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = !isGenerating) { useAdvancedRecognition = true }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "AI-Enhanced (GenAI)",
                                        color = if (useAdvancedRecognition) ComposeColor.White else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Higher accuracy & punctuation",
                                        color = if (useAdvancedRecognition) ComposeColor.White.copy(alpha = 0.7f) else TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Manual Model Downloader / Status sync
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Offline Model Sync:",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (downloadStatusText.isEmpty()) "Not started" else downloadStatusText,
                                    fontSize = 11.sp,
                                    color = if (downloadStatusText.contains("Failed", ignoreCase = true) || downloadStatusText.contains("Error", ignoreCase = true)) CreatorSunsetPink else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    isDownloadingModel = true
                                    downloadStatusText = "Initializing sync..."
                                    coroutineScope.launch {
                                        try {
                                            VideoSubtitler.downloadSpeechModel(
                                                context = context,
                                                locale = selectedLanguage.second,
                                                useAdvanced = useAdvancedRecognition
                                            ).collect { progress ->
                                                val str = progress.toString()
                                                when {
                                                    str.startsWith("Downloading") || str.contains("Progress") -> {
                                                        val pct = "\\d+".toRegex().find(str)?.value
                                                        downloadStatusText = if (pct != null) "Downloading: $pct%" else "Downloading..."
                                                    }
                                                    str.startsWith("Downloaded") || str.contains("Completed") || str.contains("Success") -> {
                                                        downloadStatusText = "Model Ready!"
                                                    }
                                                    str.startsWith("DownloadFailed") || str.contains("Failed") -> {
                                                        downloadStatusText = "Failed: Download Error"
                                                    }
                                                    else -> {
                                                        downloadStatusText = "Downloading..."
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            downloadStatusText = "Error: ${e.message}"
                                        } finally {
                                            isDownloadingModel = false
                                        }
                                    }
                                },
                                enabled = !isDownloadingModel && !isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianBlack),
                                border = BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isDownloadingModel) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = ComposeColor.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing...", fontSize = 11.sp, color = TextPrimary)
                                } else {
                                    Text("Sync Model", fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Generator Action
            Button(
                onClick = {
                    isGenerating = true
                    selectedSubtitleIndex = null
                    coroutineScope.launch {
                        try {
                            subtitles = VideoSubtitler.generateSubtitlesFromVideo(
                                context = context,
                                videoPath = videoUri.toString(),
                                locale = selectedLanguage.second,
                                useAdvanced = useAdvancedRecognition
                            )
                            if (subtitles.isNotEmpty()) {
                                subtitleText = TextFieldValue(subtitles.first().text)
                                selectedSubtitleIndex = 0
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoSubtitleScreen", "Transcription failed", e)
                            val isGenAiIssue = useAdvancedRecognition && (
                                e.message?.contains("AICORE", ignoreCase = true) == true ||
                                e.message?.contains("GenAiException", ignoreCase = true) == true ||
                                e.message?.contains("DOWNLOAD_ERROR", ignoreCase = true) == true ||
                                e.message?.contains("internal error", ignoreCase = true) == true
                            )
                            if (isGenAiIssue) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context, 
                                        "AI-Enhanced mode not supported on this device. Falling back to Standard mode.", 
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    useAdvancedRecognition = false
                                }
                                try {
                                    subtitles = VideoSubtitler.generateSubtitlesFromVideo(
                                        context = context,
                                        videoPath = videoUri.toString(),
                                        locale = selectedLanguage.second,
                                        useAdvanced = false
                                    )
                                    if (subtitles.isNotEmpty()) {
                                        subtitleText = TextFieldValue(subtitles.first().text)
                                        selectedSubtitleIndex = 0
                                    }
                                } catch (fallbackEx: Exception) {
                                    exportStatus = "Transcription failed: ${fallbackEx.message}"
                                }
                            } else {
                                exportStatus = "Transcription failed: ${e.message}"
                            }
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(CreatorSunsetPink, CreatorSunsetOrange)),
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Transparent),
                shape = RoundedCornerShape(12.dp),
                enabled = !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ComposeColor.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Transcribing Speech (Local AI)...", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                } else {
                    Text("Auto-Generate Subtitles", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
            }

            // Timestamps timeline Card
            if (subtitles.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassySurface),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Speech Timeline (Click to Edit):", 
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subtitles.forEachIndexed { index, sub ->
                                val isSelected = selectedSubtitleIndex == index
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) CreatorSunsetPink.copy(alpha = 0.2f) else ObsidianBlack,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CreatorSunsetPink else CardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedSubtitleIndex = index
                                            subtitleText = TextFieldValue(sub.text)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                brush = Brush.linearGradient(
                                                    if (isSelected) listOf(CreatorSunsetPink, CreatorSunsetOrange)
                                                    else listOf(CreatorSunsetOrange, CreatorSunsetYellow)
                                                ),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${String.format("%.1f", sub.startTime / 1000f)}s - ${String.format("%.1f", sub.endTime / 1000f)}s",
                                            color = ComposeColor.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = sub.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Text Editor & Style Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GlassySurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Customize Subtitle Style", 
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Edit Field
                    Column {
                        Text("Edit Text", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                        BasicTextField(
                            value = subtitleText,
                            onValueChange = {
                                subtitleText = it
                                selectedSubtitleIndex?.let { index ->
                                    subtitles = subtitles.mapIndexed { idx, item ->
                                        if (idx == index) item.copy(text = it.text) else item
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianBlack, RoundedCornerShape(8.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 15.sp)
                        )
                    }

                    // Start/End Time Range Editor
                    selectedSubtitleIndex?.let { index ->
                        val sub = subtitles.getOrNull(index)
                        if (sub != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Start Time Block
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Start Time", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                subtitles = subtitles.mapIndexed { idx, item ->
                                                    if (idx == index) item.copy(startTime = maxOf(0L, item.startTime - 100L)) else item
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).background(ObsidianBlack, RoundedCornerShape(6.dp)).border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                                        ) {
                                            Text("-", color = TextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1fs", sub.startTime / 1000f),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = {
                                                subtitles = subtitles.mapIndexed { idx, item ->
                                                    if (idx == index) item.copy(startTime = minOf(item.endTime - 100L, item.startTime + 100L)) else item
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).background(ObsidianBlack, RoundedCornerShape(6.dp)).border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                                        ) {
                                            Text("+", color = TextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // End Time Block
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("End Time", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                subtitles = subtitles.mapIndexed { idx, item ->
                                                    if (idx == index) item.copy(endTime = maxOf(item.startTime + 100L, item.endTime - 100L)) else item
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).background(ObsidianBlack, RoundedCornerShape(6.dp)).border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                                        ) {
                                            Text("-", color = TextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1fs", sub.endTime / 1000f),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = {
                                                subtitles = subtitles.mapIndexed { idx, item ->
                                                    if (idx == index) item.copy(endTime = item.endTime + 100L) else item
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).background(ObsidianBlack, RoundedCornerShape(6.dp)).border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                                        ) {
                                            Text("+", color = TextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Font Style Presets Chips
                    Column {
                        Text("Style Preset", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fontStylePresets.forEach { preset ->
                                val selected = selectedFontStylePreset == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) CreatorSunsetPink else ObsidianBlack,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) CreatorSunsetPink else CardBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedFontStylePreset = preset
                                            previewBitmap = null
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (selected) ComposeColor.White else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Font Colors Row Selection
                    Column {
                        Text("Font Color", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            fontColors.forEach { color ->
                                val selected = selectedFontColor == color
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(color, CircleShape)
                                        .border(
                                            width = 3.dp,
                                            color = if (selected) CreatorSunsetPink else CardBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedFontColor = color
                                            previewBitmap = null
                                        }
                                )
                            }
                        }
                    }

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 24f..80f,
                            colors = SliderDefaults.colors(
                                thumbColor = CreatorSunsetPink,
                                activeTrackColor = CreatorSunsetPink,
                                inactiveTrackColor = CardBorder
                            ),
                            modifier = Modifier.width(180.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${fontSize.toInt()}px",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Preview Action
                    Button(
                        onClick = {
                            val bmp = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888)
                            previewBitmap = VideoSubtitler.drawSubtitleOnBitmap(
                                bitmap = bmp,
                                subtitle = subtitleText.text,
                                fontStyle = selectedFontStylePreset,
                                fontSize = fontSize,
                                fontColor = android.graphics.Color.rgb(
                                    (selectedFontColor.red * 255).toInt(),
                                    (selectedFontColor.green * 255).toInt(),
                                    (selectedFontColor.blue * 255).toInt()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianBlack),
                        border = BorderStroke(1.dp, CardBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate Frame Preview Layout", color = TextPrimary)
                    }

                    previewBitmap?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .background(ComposeColor.Black)
                        ) {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Subtitle preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Change Video
                Button(
                    onClick = { videoPicker.launch("video/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianBlack),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Change Video", color = TextPrimary)
                }

                // Export Video (Gradient)
                Button(
                    onClick = {
                        saveVideoLauncher.launch("subtitled_video.mp4")
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .background(
                            brush = Brush.linearGradient(listOf(CreatorSunsetPink, CreatorSunsetOrange)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Transparent),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isExporting && subtitles.isNotEmpty()
                ) {
                    Text("Export Video", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
            }

            // Export SRT Option Button
            if (subtitles.isNotEmpty()) {
                Button(
                    onClick = {
                        saveSrtLauncher.launch("subtitles.srt")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianBlack),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Export Subtitles (.srt)", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }

            exportStatus?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
