package com.spacemishka.app.instatoolbox

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
fun NineGridProfileSlicerScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(initialUri) }
    var sliceMode by remember { mutableStateOf("Center Crop") } // "Center Crop", "Fit Padding"
    var padColorBlack by remember { mutableStateOf(true) }
    var paddingPercent by remember { mutableStateOf(0f) }
    val paddingOptions = listOf(0f, 2.5f, 5f, 10f, 15f)
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewSlices by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            previewSlices.forEach { it.recycle() }
            previewSlices = emptyList()
            sourceBitmap?.recycle()
            sourceBitmap = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            previewSlices.forEach { it.recycle() }
            previewSlices = emptyList()
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
                text = "9-Grid Profile Slicer",
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
                                brush = Brush.linearGradient(listOf(CreatorSunsetOrange, CreatorSunsetPink)),
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
                        text = "Import Grid Photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select any landscape, portrait or square image to slice into 9 grid parts",
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

            // Process preview grid slices on background thread
            LaunchedEffect(sourceBitmap, sliceMode, padColorBlack, paddingPercent) {
                val src = sourceBitmap ?: return@LaunchedEffect
                withContext(Dispatchers.Default) {
                    val padColor = if (padColorBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    val previewGrid = generateNineGridSlices(src, sliceMode, padColor, paddingPercent)
                    withContext(Dispatchers.Main) {
                        previewSlices.forEach { it.recycle() }
                        previewSlices = previewGrid
                    }
                }
            }

            // Grid Preview
            if (previewSlices.isNotEmpty()) {
                Text(
                    text = "Grid Preview & Posting Sequence:", 
                    style = MaterialTheme.typography.titleMedium, 
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 3x3 Grid showing sliced images
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = CardDefaults.cardColors(containerColor = GlassySurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (row in 0 until 3) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val index = row * 3 + col
                                    // Slices are 0 to 8:
                                    // Row 1: 0, 1, 2
                                    // Row 2: 3, 4, 5
                                    // Row 3: 6, 7, 8
                                    // Corresponding posting order index (1st to post is Row 3, col 3 = index 8):
                                    // Posting order label: Post 1st is index 8 (9), Post 9th is index 0 (1).
                                    // postingOrder = 9 - index
                                    val postingOrder = 9 - index
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(ComposeColor.Black)
                                    ) {
                                        Image(
                                            bitmap = previewSlices[index].asImageBitmap(),
                                            contentDescription = "Part ${index + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Overlay posting order badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(ComposeColor.Black.copy(alpha = 0.65f))
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#$postingOrder (Upload $postingOrder)",
                                                color = ComposeColor.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
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
                    CircularProgressIndicator(color = CreatorSunsetOrange)
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
                    // Slicing Mode selector
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Grid Slicing Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf("Center Crop", "Fit Padding")
                            modes.forEach { mode ->
                                val selected = sliceMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) CreatorSunsetOrange else ObsidianBlack,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) CreatorSunsetOrange else CardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            sliceMode = mode
                                            previewSlices.forEach { it.recycle() }
                                            previewSlices = emptyList()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode,
                                        color = if (selected) ComposeColor.White else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Padding Color Swatches (Only visible when "Fit Padding" is selected)
                    if (sliceMode == "Fit Padding") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Padding Color",
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
                                            color = if (padColorBlack) CreatorSunsetOrange else CardBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            padColorBlack = true
                                            previewSlices.forEach { it.recycle() }
                                            previewSlices = emptyList()
                                        }
                                )
                                // White Swatch
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(ComposeColor.White, CircleShape)
                                        .border(
                                            width = 3.dp,
                                            color = if (!padColorBlack) CreatorSunsetOrange else CardBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            padColorBlack = false
                                            previewSlices.forEach { it.recycle() }
                                            previewSlices = emptyList()
                                        }
                                )
                            }
                        }

                        // Padding Amount Options
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Padding Amount",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                paddingOptions.forEach { option ->
                                    val selected = paddingPercent == option
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (selected) CreatorSunsetOrange else ObsidianBlack,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) CreatorSunsetOrange else CardBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                paddingPercent = option
                                                previewSlices.forEach { it.recycle() }
                                                previewSlices = emptyList()
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (option % 1f == 0f) "${option.toInt()}%" else "${option}%",
                                            color = if (selected) ComposeColor.White else TextSecondary,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
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
                                        val fullSlices = generateNineGridSlices(it, sliceMode, padColor, paddingPercent)
                                        saved = saveNineGridToGallery(context, fullSlices)
                                        fullSlices.forEach { seg -> seg.recycle() }
                                    }
                                }
                            }
                            if (saved) {
                                Toast.makeText(context, "9 Grid parts saved! Upload #1 first to #9 last.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Save failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            brush = Brush.linearGradient(listOf(CreatorSunsetOrange, CreatorSunsetPink)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save to Gallery", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
            }
            
            // Helpful Hint Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                colors = CardDefaults.cardColors(containerColor = ObsidianBlack)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "💡 Pro Tip: Grid Splicing Order",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Upload the images to Instagram starting with the bottom-right segment (labeled #1) and end with the top-left segment (labeled #9). This reconstructs the puzzle perfectly on your feed page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// Generate the 3x3 slices
fun generateNineGridSlices(src: Bitmap, mode: String, padColor: Int, paddingPercent: Float): List<Bitmap> {
    val originalW = src.width
    val originalH = src.height

    val squareBitmap: Bitmap
    if (mode == "Center Crop") {
        val minSide = minOf(originalW, originalH)
        val xOffset = (originalW - minSide) / 2
        val yOffset = (originalH - minSide) / 2
        squareBitmap = Bitmap.createBitmap(src, xOffset, yOffset, minSide, minSide)
    } else {
        val maxSide = maxOf(originalW, originalH)
        val imageContentPercent = 1f - (paddingPercent / 100f)
        val finalSize = (maxSide / imageContentPercent).toInt()
        
        val maxContentSize = (finalSize * imageContentPercent).toInt()
        val scale = maxContentSize.toFloat() / maxSide
        
        val scaledW = (originalW * scale).toInt()
        val scaledH = (originalH * scale).toInt()

        squareBitmap = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(squareBitmap)
        canvas.drawColor(padColor)
        val left = (finalSize - scaledW) / 2
        val top = (finalSize - scaledH) / 2
        
        val scaledBitmap = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
        if (scaledBitmap != src) {
            scaledBitmap.recycle()
        }
    }

    // Now slice the squareBitmap into a 3x3 grid
    val slices = mutableListOf<Bitmap>()
    val sideSize = squareBitmap.width
    val sliceSize = sideSize / 3

    for (row in 0 until 3) {
        for (col in 0 until 3) {
            val x = (col * sliceSize).coerceAtMost(sideSize - sliceSize)
            val y = (row * sliceSize).coerceAtMost(sideSize - sliceSize)
            val slice = Bitmap.createBitmap(squareBitmap, x, y, sliceSize, sliceSize)
            slices.add(slice)
        }
    }

    if (squareBitmap !== src) {
        squareBitmap.recycle()
    }
    return slices
}

fun saveNineGridToGallery(context: Context, slices: List<Bitmap>): Boolean {
    var allSaved = true
    val timestamp = System.currentTimeMillis()
    // To help the user upload in correct order, we can label the output file sequence
    // matching the posting sequence.
    // Part index 8 is post #1. Part index 7 is post #2.
    // So for index i, postingOrder = 9 - i.
    for ((idx, bitmap) in slices.withIndex()) {
        val postingOrder = 9 - idx
        val filename = "NineGrid_${timestamp}_part_${postingOrder}.jpg"
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
