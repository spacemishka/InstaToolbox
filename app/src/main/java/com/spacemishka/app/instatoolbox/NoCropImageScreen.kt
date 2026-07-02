package com.spacemishka.app.instatoolbox

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.spacemishka.app.instatoolbox.ui.theme.*

@Composable
fun NoCropImageScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var paddingPercent by remember { mutableStateOf(10f) }
    var frameColorBlack by remember { mutableStateOf(true) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val paddingOptions = listOf(0f, 2.5f, 5f, 10f, 15f, 20f)
    val outputSizePresets = listOf("Original", "Instagram (1080px)", "Full HD (1920px)", "4K UHD (3840px)")
    var outputSizePreset by remember { mutableStateOf("Original") }

    DisposableEffect(Unit) {
        onDispose {
            processedBitmap?.recycle()
            processedBitmap = null
            sourceBitmap?.recycle()
            sourceBitmap = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            processedBitmap?.recycle()
            processedBitmap = null
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
                text = "No-Crop Post Maker",
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
                                brush = Brush.linearGradient(listOf(CreatorSunsetPink, CreatorSunsetOrange)),
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
                        text = "Import Photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select an image to adjust aspect ratios",
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

            // Process padded preview bitmap from sourceBitmap on background thread
            LaunchedEffect(sourceBitmap, paddingPercent, frameColorBlack) {
                val src = sourceBitmap ?: return@LaunchedEffect
                withContext(Dispatchers.Default) {
                    val padColor = if (frameColorBlack) Color.BLACK else Color.WHITE
                    val padded = addPaddingToSquare(
                        src,
                        paddingPercent / 100f,
                        padColor
                    )
                    withContext(Dispatchers.Main) {
                        processedBitmap?.recycle()
                        processedBitmap = padded
                    }
                }
            }

            // Preview Layout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = GlassySurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    processedBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .background(if (frameColorBlack) ComposeColor.Black else ComposeColor.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Padded image preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } ?: Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CreatorSunsetPink)
                    }
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
                    // Frame Color Swatches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Frame Background",
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
                                        color = if (frameColorBlack) CreatorSunsetPink else CardBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        frameColorBlack = true
                                        processedBitmap = null
                                    }
                            )
                            // White Swatch
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ComposeColor.White, CircleShape)
                                    .border(
                                        width = 3.dp,
                                        color = if (!frameColorBlack) CreatorSunsetPink else CardBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        frameColorBlack = false
                                        processedBitmap = null
                                    }
                            )
                        }
                    }

                    // Padding Option Chips
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
                                            color = if (selected) CreatorSunsetPink else ObsidianBlack,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) CreatorSunsetPink else CardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            paddingPercent = option
                                            processedBitmap = null
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

                    // Output Size Presets
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Output Size (Square)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            outputSizePresets.forEach { preset ->
                                val selected = outputSizePreset == preset
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (selected) CreatorSunsetPink else ObsidianBlack,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) CreatorSunsetPink else CardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            outputSizePreset = preset
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
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
                                val targetSize = when (outputSizePreset) {
                                    "Instagram (1080px)" -> 1080
                                    "Full HD (1920px)" -> 1920
                                    "4K UHD (3840px)" -> 3840
                                    else -> null
                                }
                                val loader = context.imageLoader
                                val request = ImageRequest.Builder(context)
                                    .data(imageUri)
                                    .size(coil.size.Size.ORIGINAL)
                                    .allowHardware(false)
                                    .build()
                                val result = loader.execute(request)
                                if (result is SuccessResult) {
                                    val fullSrc = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                    fullSrc?.let {
                                        val padColor = if (frameColorBlack) Color.BLACK else Color.WHITE
                                        val fullPadded = addPaddingToSquare(
                                            it,
                                            paddingPercent / 100f,
                                            padColor,
                                            targetSize
                                        )
                                        saved = saveBitmapToGallery(context, fullPadded)
                                        fullPadded.recycle()
                                    }
                                }
                            }
                            Toast.makeText(context, if (saved) "Photo saved to Gallery!" else "Save failed.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            brush = Brush.linearGradient(listOf(CreatorSunsetPink, CreatorSunsetOrange)),
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

fun addPaddingToSquare(
    src: Bitmap,
    paddingPercent: Float,
    frameColor: Int,
    targetSize: Int? = null
): Bitmap {
    val w = src.width
    val h = src.height
    val longer = maxOf(w, h)
    val imageContentPercent = 1 - paddingPercent
    
    // Safely limit max canvas size to prevent OutOfMemory
    val rawFinalSize = (longer / imageContentPercent).toInt()
    val finalSize = targetSize ?: if (rawFinalSize > 8192) 8192 else rawFinalSize
    
    val maxContentSize = (finalSize * imageContentPercent).toInt()
    val scale = maxContentSize.toFloat() / longer
    
    val scaledW = (w * scale).toInt()
    val scaledH = (h * scale).toInt()
    
    val result = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(frameColor)
    val left = (finalSize - scaledW) / 2
    val top = (finalSize - scaledH) / 2
    
    val scaledBitmap = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
    canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
    if (scaledBitmap != src) {
        scaledBitmap.recycle()
    }
    return result
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val filename = "NoCrop_${System.currentTimeMillis()}.jpg"
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
    return fos?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        true
    } ?: false
}