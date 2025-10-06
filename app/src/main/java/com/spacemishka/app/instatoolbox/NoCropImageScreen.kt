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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

@Composable
fun NoCropImageScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var paddingPercent by remember { mutableStateOf(20f) }
    var frameColorBlack by remember { mutableStateOf(true) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var expandedPadding by remember { mutableStateOf(false) }
    
    val paddingOptions = listOf(2.5f, 5f, 10f, 15f, 20f)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        processedBitmap = null
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
                text = "No-Crop Image Posting",
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Padding:", modifier = Modifier.weight(1f))
            Box(modifier = Modifier.weight(2f)) {
                Button(
                    onClick = { expandedPadding = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${if (paddingPercent % 1 == 0f) paddingPercent.toInt() else paddingPercent}%")
                }
                DropdownMenu(
                    expanded = expandedPadding,
                    onDismissRequest = { expandedPadding = false }
                ) {
                    paddingOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${if (option % 1 == 0f) option.toInt() else option}%") },
                            onClick = {
                                paddingPercent = option
                                expandedPadding = false
                                processedBitmap = null
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Frame Color:", modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    frameColorBlack = !frameColorBlack
                    processedBitmap = null
                },
                modifier = Modifier.weight(2f)
            ) {
                Text(if (frameColorBlack) "Black" else "White")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        imageUri?.let { uri ->
            LaunchedEffect(uri, paddingPercent, frameColorBlack) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val srcBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    srcBitmap?.let {
                        processedBitmap = addPaddingToSquare(
                            it,
                            paddingPercent / 100f,
                            if (frameColorBlack) Color.BLACK else Color.WHITE
                        )
                    }
                }
            }
            processedBitmap?.let { bmp ->
                Text("Preview:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (frameColorBlack) ComposeColor.Black else ComposeColor.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Padded image",
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val saved = saveBitmapToGallery(context, bmp)
                        Toast.makeText(context, if (saved) "Image saved!" else "Save failed.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Image")
                }
            } ?: Text("Processing image...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun addPaddingToSquare(
    src: Bitmap,
    paddingPercent: Float,
    frameColor: Int
): Bitmap {
    val w = src.width
    val h = src.height
    val longer = maxOf(w, h)
    // paddingPercent is the TOTAL percentage for both sides combined
    // So 10% padding means 5% on each side
    // The image should occupy (100% - paddingPercent) of the canvas
    val imageContentPercent = 1 - paddingPercent
    val finalSize = (longer / imageContentPercent).toInt()
    
    // Calculate the scaled dimensions to fit in the content area
    val scaledW = (w * imageContentPercent).toInt()
    val scaledH = (h * imageContentPercent).toInt()
    
    val result = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(frameColor)
    val left = (finalSize - scaledW) / 2
    val top = (finalSize - scaledH) / 2
    
    val scaledBitmap = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
    canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
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
        val imagesDir = context.getExternalFilesDir("Pictures")
        val file = java.io.File(imagesDir, filename)
        java.io.FileOutputStream(file)
    }
    return fos?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        true
    } ?: false
}