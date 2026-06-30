package com.spacemishka.app.instatoolbox

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.spacemishka.app.instatoolbox.ui.theme.*

@Composable
fun SwipeablePhotoGeneratorScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    onBack: () -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var segmentCount by remember { mutableStateOf(3) }
    var segments by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var padColorBlack by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val segmentOptions = listOf(2, 3, 4, 5)

    DisposableEffect(Unit) {
        onDispose {
            segments.forEach { it.recycle() }
            segments = emptyList()
            previewBitmap?.recycle()
            previewBitmap = null
            sourceBitmap?.recycle()
            sourceBitmap = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            segments.forEach { it.recycle() }
            segments = emptyList()
            previewBitmap?.recycle()
            previewBitmap = null
            sourceBitmap?.recycle()
            sourceBitmap = null
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
                text = "Swipeable Carousel Slicer",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        if (imageUri == null) {
            // Import placeholder box
            Card(
                onClick = { if (hasPermission) imagePickerLauncher.launch("image/*") },
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
                                brush = Brush.linearGradient(listOf(CreatorSunsetPurple, CreatorSunsetOrange)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = ComposeColor.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Import Panorama Photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select a wide panoramic photo to slice into seamless carousel cards",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Load and downsample original source bitmap for preview on background thread
            LaunchedEffect(imageUri) {
                withContext(Dispatchers.IO) {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(imageUri)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val srcBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        srcBitmap?.let {
                            val maxPreviewSize = 1080
                            val originalW = it.width
                            val originalH = it.height
                            val scale = if (maxOf(originalW, originalH) > maxPreviewSize) {
                                maxPreviewSize.toFloat() / maxOf(originalW, originalH)
                            } else {
                                1f
                            }
                            val previewSrc = if (scale < 1f) {
                                Bitmap.createScaledBitmap(it, (originalW * scale).toInt(), (originalH * scale).toInt(), true)
                            } else {
                                it.copy(it.config ?: Bitmap.Config.ARGB_8888, true)
                            }
                            withContext(Dispatchers.Main) {
                                sourceBitmap?.recycle()
                                sourceBitmap = previewSrc
                            }
                        }
                    }
                }
            }

            // Process preview carousel slices on background thread
            LaunchedEffect(sourceBitmap, segmentCount, padColorBlack) {
                val src = sourceBitmap ?: return@LaunchedEffect
                withContext(Dispatchers.Default) {
                    val padColor = if (padColorBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    val previewSlices = splitImageForCarousel(src, segmentCount, padColor)
                    
                    withContext(Dispatchers.Main) {
                        segments.forEach { it.recycle() }
                        segments = previewSlices
                    }
                }
            }

            // Horizontal Slices Preview
            if (segments.isNotEmpty()) {
                Text(
                    text = "Carousel Slides Preview:", 
                    style = MaterialTheme.typography.titleMedium, 
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(segments.size) { index ->
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = CardDefaults.cardColors(containerColor = GlassySurface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = segments[index].asImageBitmap(),
                                    contentDescription = "Slide ${index + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(
                                            brush = Brush.linearGradient(listOf(CreatorSunsetPurple, CreatorSunsetOrange)),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Slide ${index + 1}",
                                        color = ComposeColor.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CreatorSunsetPurple)
                }
            }

            // Customization Options Card
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
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Padding Color Swatches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fill Background Color",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Black Swatch
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ComposeColor.Black, CircleShape)
                                    .border(
                                        width = 3.dp,
                                        color = if (padColorBlack) CreatorSunsetPurple else CardBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        padColorBlack = true
                                        segments.forEach { it.recycle() }
                                        segments = emptyList()
                                    }
                            )
                            // White Swatch
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ComposeColor.White, CircleShape)
                                    .border(
                                        width = 3.dp,
                                        color = if (!padColorBlack) CreatorSunsetPurple else CardBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        padColorBlack = false
                                        segments.forEach { it.recycle() }
                                        segments = emptyList()
                                    }
                            )
                        }
                    }

                    // Slices Choice Chips
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Slide Count",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            segmentOptions.forEach { count ->
                                val selected = segmentCount == count
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) CreatorSunsetPurple else ObsidianBlack,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) CreatorSunsetPurple else CardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            segmentCount = count
                                            segments.forEach { it.recycle() }
                                            segments = emptyList()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$count Slides",
                                        color = if (selected) ComposeColor.White else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
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
                // Change Photo
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianBlack),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Change Photo", color = TextPrimary)
                }

                // Sunset Gradient Save Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            var saved = false
                            withContext(Dispatchers.IO) {
                                val loader = context.imageLoader
                                val request = ImageRequest.Builder(context)
                                    .data(imageUri)
                                    .allowHardware(false)
                                    .build()
                                val result = loader.execute(request)
                                if (result is SuccessResult) {
                                    val fullSrc = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                    fullSrc?.let {
                                        val padColor = if (padColorBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                        val fullSegments = splitImageForCarousel(it, segmentCount, padColor)
                                        saved = saveSegmentsToGallery(context, fullSegments)
                                        fullSegments.forEach { seg -> seg.recycle() }
                                    }
                                }
                            }
                            Toast.makeText(context, if (saved) "All carousel slides saved!" else "Save failed.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            brush = Brush.linearGradient(listOf(CreatorSunsetPurple, CreatorSunsetOrange)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save to Gallery", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Split image into square segments for Instagram carousel
fun splitImageForCarousel(src: Bitmap, count: Int, padColor: Int): List<Bitmap> {
    val h = src.height
    val totalWidth = count * h
    val w = src.width

    // Create a padded bitmap with width = count * h, height = h
    val paddedBitmap = Bitmap.createBitmap(totalWidth, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(paddedBitmap)
    canvas.drawColor(padColor)
    val left = (totalWidth - w) / 2
    canvas.drawBitmap(src, left.toFloat(), 0f, null)

    // Split into squares
    val segments = mutableListOf<Bitmap>()
    for (i in 0 until count) {
        val seg = Bitmap.createBitmap(paddedBitmap, i * h, 0, h, h)
        segments.add(seg)
    }
    paddedBitmap.recycle()
    return segments
}

fun saveSegmentsToGallery(context: Context, segments: List<Bitmap>): Boolean {
    var allSaved = true
    for ((idx, bitmap) in segments.withIndex()) {
        val filename = "Swipeable_${System.currentTimeMillis()}_${idx + 1}.jpg"
        val fos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InstaToolbox")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val appDir = java.io.File(imagesDir, "InstaToolbox")
            if (!appDir.exists()) appDir.mkdirs()
            val file = java.io.File(appDir, filename)
            val stream = java.io.FileOutputStream(file)
            
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            stream
        }
        val saved = fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            true
        } ?: false
        if (!saved) allSaved = false
    }
    return allSaved
}