package com.spacemishka.app.instatoolbox

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun VideoCaptionScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean = true,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf(TextFieldValue("")) }
    var fontSize by remember { mutableStateOf(48f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var captions by remember { mutableStateOf<List<VideoCaptioner.Caption>>(emptyList()) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        videoUri = uri
        caption = TextFieldValue("")
        previewBitmap = null
        exportStatus = null
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
                text = "Video Captioning",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { videoPicker.launch("video/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasPermission
        ) {
            Text("Select Video")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (videoUri != null) {
            // Video preview
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(videoUri)
                        setOnPreparedListener { it.isLooping = true; start() }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                coroutineScope.launch {
                    captions = VideoCaptioner.generateCaptionsFromVideo(context, videoUri.toString())
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Generate Captions")
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (captions.isNotEmpty()) {
                Text("Generated Captions:", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                    captions.forEach { cap ->
                        Text("${cap.text} [${cap.startTime} - ${cap.endTime} ms]", fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = caption,
                onValueChange = { caption = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = fontSize.sp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font Size:")
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 24f..96f,
                    steps = 6,
                    modifier = Modifier.width(200.dp)
                )
                Text(fontSize.toInt().toString())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                // For preview, extract a frame from the video (placeholder: use a blank bitmap)
                val bmp = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888)
                previewBitmap = VideoCaptioner.drawCaptionOnBitmap(
                    bmp,
                    caption.text,
                    fontSize = fontSize,
                    fontFamily = Typeface.DEFAULT_BOLD
                )
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Preview Caption")
            }
            Spacer(modifier = Modifier.height(8.dp))
            previewBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.height(200.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                coroutineScope.launch {
                    val result = VideoCaptioner.exportVideoWithCaptions(
                        inputVideoPath = videoUri.toString(),
                        caption = caption.text,
                        outputVideoPath = context.cacheDir.absolutePath + "/captioned_output.mp4"
                    )
                    exportStatus = if (result) "Export successful!" else "Export failed"
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Video with Caption")
            }
            exportStatus?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it)
            }
        }
    }
}