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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.foundation.layout.Row as Row1

@Composable
fun SwipeablePhotoGeneratorScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    onBack: () -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var segmentCount by remember { mutableStateOf(3) }
    var segments by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var padColorBlack by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        segments = emptyList()
        previewBitmap = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Swipeable Photo Generator",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasPermission
        ) {
            Text("Select Image")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row1(verticalAlignment = Alignment.CenterVertically) {
            Text("Segments:", modifier = Modifier.weight(1f))
            DropdownMenuSegmentCount(segmentCount) { segmentCount = it }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row1(verticalAlignment = Alignment.CenterVertically) {
            Text("Padding Color:", modifier = Modifier.weight(1f))
            Button(
                onClick = { padColorBlack = !padColorBlack },
                modifier = Modifier.weight(2f)
            ) {
                Text(if (padColorBlack) "Black" else "White")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        imageUri?.let { uri ->
            LaunchedEffect(uri, segmentCount, padColorBlack) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val srcBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    srcBitmap?.let {
                        val padColor = if (padColorBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                        val squares = splitImageForCarousel(it, segmentCount, padColor)
                        segments = squares
                        previewBitmap = createCarouselPreview(it, segmentCount, padColor)
                    }
                }
            }
            previewBitmap?.let { bmp ->
                Text("Preview:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    ImageWithSeparators(
                        bitmap = bmp,
                        segmentCount = segmentCount,
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val saved = saveSegmentsToGallery(context, segments)
                        Toast.makeText(context, if (saved) "Segments saved!" else "Save failed.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save All Segments")
                }
            } ?: Text("Processing image...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ImageWithSeparators(bitmap: Bitmap, segmentCount: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Preview with separators",
            modifier = Modifier.matchParentSize()
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val step = width / segmentCount
            for (i in 1 until segmentCount) {
                drawLine(
                    color = Color.Red,
                    start = androidx.compose.ui.geometry.Offset(i * step, 0f),
                    end = androidx.compose.ui.geometry.Offset(i * step, height),
                    strokeWidth = 4f
                )
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
    return segments
}

// Create a preview image with separator lines for carousel
fun createCarouselPreview(src: Bitmap, count: Int, padColor: Int): Bitmap {
    val h = src.height
    val totalWidth = count * h
    val w = src.width

    val previewBitmap = Bitmap.createBitmap(totalWidth, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(previewBitmap)
    canvas.drawColor(padColor)
    val left = (totalWidth - w) / 2
    canvas.drawBitmap(src, left.toFloat(), 0f, null)

    // Draw separator lines
    val lineColor = android.graphics.Color.RED
    val lineWidth = 8
    for (i in 1 until count) {
        canvas.drawRect(
            Rect(i * h - lineWidth / 2, 0, i * h + lineWidth / 2, h),
            android.graphics.Paint().apply {
                color = lineColor
                strokeWidth = lineWidth.toFloat()
            }
        )
    }
    return previewBitmap
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
            val imagesDir = context.getExternalFilesDir("Pictures")
            val file = java.io.File(imagesDir, filename)
            java.io.FileOutputStream(file)
        }
        val saved = fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            true
        } ?: false
        if (!saved) allSaved = false
    }
    return allSaved
}

@Composable
fun DropdownMenuSegmentCount(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text("$selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(2, 3, 6, 9).forEach { count ->
                DropdownMenuItem(
                    text = { Text("$count") },
                    onClick = {
                        onSelect(count)
                        expanded = false
                    }
                )
            }
        }
    }
}