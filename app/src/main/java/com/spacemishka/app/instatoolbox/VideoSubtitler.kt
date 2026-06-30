package com.spacemishka.app.instatoolbox

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

// Media3 Imports
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Composition
import com.google.common.collect.ImmutableList

/**
 * VideoSubtitler provides functions to decode video audio to PCM, run actual
 * on-device speech-to-text to generate subtitles using ML Kit GenAI Speech Recognition,
 * render subtitles on frames, and export the final video.
 */
object VideoSubtitler {

    data class Subtitle(
        val startTime: Long,
        val endTime: Long,
        val text: String
    )

    fun downloadSpeechModel(
        context: Context,
        locale: java.util.Locale,
        useAdvanced: Boolean
    ): kotlinx.coroutines.flow.Flow<Any> {
        val options = com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions.Builder().apply {
            this.locale = locale
            preferredMode = if (useAdvanced) {
                com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions.Mode.MODE_ADVANCED
            } else {
                com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions.Mode.MODE_BASIC
            }
        }.build()
        val client = com.google.mlkit.genai.speechrecognition.SpeechRecognition.getClient(options)
        @Suppress("UNCHECKED_CAST")
        return client.download() as kotlinx.coroutines.flow.Flow<Any>
    }

    private data class SubtitleSample(
        val timeUs: Long,
        val text: String
    )

    /**
     * Decodes the audio track of a video file to raw 16-bit PCM, downmixes it to mono,
     * and resamples it to 16 kHz to be compatible with ML Kit speech recognition.
     */
    fun decodeVideoAudioToPcm16kMono(context: Context, videoUri: Uri, outputFile: File): Boolean {
        val extractor = android.media.MediaExtractor()
        var codec: android.media.MediaCodec? = null
        var fos: FileOutputStream? = null
        try {
            extractor.setDataSource(context, videoUri, null)
            var audioTrackIndex = -1
            var format: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || format == null) return false
            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(android.media.MediaFormat.KEY_MIME)!!
            format.setInteger(android.media.MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            val decoder = android.media.MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val sourceSampleRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val sourceChannels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            fos = FileOutputStream(outputFile)

            val info = android.media.MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isCodecEOS = false
            val timeoutUs = 10000L

            var remainder = 0.0
            val targetSampleRate = 16000.0
            val ratio = sourceSampleRate.toDouble() / targetSampleRate

            var pcmBuffer = ByteArray(0)

            while (!isCodecEOS) {
                if (!isExtractorEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEOS = true
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(info, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                    val chunk = ByteArray(info.size)
                    outputBuffer.position(info.offset)
                    outputBuffer.get(chunk)
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isCodecEOS = true
                    }

                    if (chunk.isNotEmpty()) {
                        pcmBuffer = pcmBuffer + chunk
                        val bytesPerFrame = sourceChannels * 2
                        val availableFrames = pcmBuffer.size / bytesPerFrame
                        if (availableFrames > 0) {
                            val framesToProcess = availableFrames
                            val bytesToProcess = framesToProcess * bytesPerFrame

                            val workBuffer = pcmBuffer.sliceArray(0 until bytesToProcess)
                            pcmBuffer = pcmBuffer.sliceArray(bytesToProcess until pcmBuffer.size)

                            val srcSamples = ShortArray(framesToProcess)
                            val byteBuf = ByteBuffer.wrap(workBuffer).order(ByteOrder.LITTLE_ENDIAN)
                            for (f in 0 until framesToProcess) {
                                var sum = 0
                                for (c in 0 until sourceChannels) {
                                    sum += byteBuf.short.toInt()
                                }
                                srcSamples[f] = (sum / sourceChannels).toShort()
                            }

                            val resampledSamples = mutableListOf<Short>()
                            var srcIdx = remainder
                            while (srcIdx < framesToProcess) {
                                val idxInt = srcIdx.toInt()
                                resampledSamples.add(srcSamples[idxInt])
                                srcIdx += ratio
                            }
                            remainder = srcIdx - framesToProcess

                            if (resampledSamples.isNotEmpty()) {
                                val outBytes = ByteBuffer.allocate(resampledSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                                for (sample in resampledSamples) {
                                    outBytes.putShort(sample)
                                }
                                fos.write(outBytes.array())
                            }
                        }
                    }
                } else if (outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Output format changed
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("VideoSubtitler", "Error decoding video audio to PCM", e)
            return false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
            try {
                fos?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Splits a single continuous transcribed string into multiple readable subtitle segments
     * with estimated start and end timestamps.
     */
    fun generateSubtitlesFromText(text: String): List<Subtitle> {
        if (text.isBlank()) return emptyList()
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val subtitles = mutableListOf<Subtitle>()
        var currentStart = 0L
        val wordsPerLine = 6
        val msPerWord = 350L

        for (i in words.indices step wordsPerLine) {
            val endIdx = minOf(i + wordsPerLine, words.size)
            val lineText = words.subList(i, endIdx).joinToString(" ")
            val wordCount = endIdx - i
            val duration = wordCount * msPerWord
            val currentEnd = currentStart + duration
            subtitles.add(Subtitle(currentStart, currentEnd, lineText))
            currentStart = currentEnd + 150L
        }
        return subtitles
    }

    /**
     * Generates SRT content from a list of subtitles.
     */
    fun generateSRT(subtitles: List<Subtitle>): String {
        fun formatTime(milliseconds: Long): String {
            val hours = milliseconds / 3600000
            val minutes = (milliseconds % 3600000) / 60000
            val seconds = (milliseconds % 60000) / 1000
            val millis = milliseconds % 1000
            return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
        }
        return subtitles.mapIndexed { index, subtitle ->
            """
            ${index + 1}
            ${formatTime(subtitle.startTime)} --> ${formatTime(subtitle.endTime)}
            ${subtitle.text}

            """.trimIndent()
        }.joinToString("\n")
    }

    /**
     * Generates subtitles for a video using actual on-device speech recognition.
     */
    suspend fun generateSubtitlesFromVideo(
        context: Context,
        videoPath: String,
        locale: java.util.Locale,
        useAdvanced: Boolean = false
    ): List<Subtitle> = withContext(Dispatchers.Default) {
        val cacheFile = java.io.File(context.cacheDir, "audio_16k_mono.pcm")
        try {
            Log.i("VideoSubtitler", "Extracting audio from $videoPath")
            val success = withContext(Dispatchers.IO) {
                decodeVideoAudioToPcm16kMono(context, Uri.parse(videoPath), cacheFile)
            }
            if (!success || !cacheFile.exists()) {
                return@withContext listOf(Subtitle(0, 3000, "Failed to decode audio track"))
            }

            Log.i("VideoSubtitler", "Initializing ML Kit Speech Recognizer with locale: $locale, advanced: $useAdvanced")
            val options = com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions.Builder().apply {
                this.locale = locale
                preferredMode = if (useAdvanced) {
                    com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions.Mode.MODE_ADVANCED
                } else {
                    com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions.Mode.MODE_BASIC
                }
            }.build()

            val client = com.google.mlkit.genai.speechrecognition.SpeechRecognition.getClient(options)

            val status = client.checkStatus()
            Log.i("VideoSubtitler", "Model status: $status")
            if (status != 3) { // 3 = AVAILABLE
                Log.i("VideoSubtitler", "Downloading model...")
                client.download().collect { progress ->
                    Log.d("VideoSubtitler", "Model download progress: $progress")
                }
            }

            val currentAudioTimeMs = java.util.concurrent.atomic.AtomicLong(0)

            val pipe = ParcelFileDescriptor.createPipe()
            val readPfd = pipe[0]
            val writePfd = pipe[1]

            // Launch the pipe writer in IO thread pool
            launch(Dispatchers.IO) {
                val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writePfd)
                val inputStream = cacheFile.inputStream()
                val buffer = ByteArray(6400) // 200ms of audio (16000 * 2 * 0.2)
                var totalBytesWritten = 0L
                try {
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead <= 0) break
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()
                        totalBytesWritten += bytesRead
                        currentAudioTimeMs.set(totalBytesWritten / 32)
                        // Sleep for 180ms (slightly faster than real-time to avoid any blockages)
                        kotlinx.coroutines.delay(180)
                    }
                } catch (e: Exception) {
                    Log.e("VideoSubtitler", "Error writing to audio pipe", e)
                } finally {
                    try { inputStream.close() } catch (ignored: Exception) {}
                    try { outputStream.close() } catch (ignored: Exception) {}
                }
            }

            val subtitleList = mutableListOf<Subtitle>()

            try {
                readPfd.use { rPfd ->
                    val audioSource = com.google.mlkit.genai.common.audio.AudioSource.fromPfd(rPfd)
                    val builder = com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest.Builder()
                    builder.audioSource = audioSource
                    val request = builder.build()

                    var lastPhraseEndTime = 0L

                    client.startRecognition(request).collect { response ->
                        when (response) {
                            is com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse.FinalTextResponse -> {
                                val phraseText = response.text.trim()
                                if (phraseText.isNotEmpty()) {
                                    val estimatedEndTime = maxOf(currentAudioTimeMs.get() - 1300L, lastPhraseEndTime + 300L)
                                    val wordCount = phraseText.split("\\s+".toRegex()).size
                                    val duration = wordCount * 320L
                                    val estimatedStartTime = maxOf(lastPhraseEndTime, estimatedEndTime - duration)
                                    
                                    subtitleList.add(Subtitle(estimatedStartTime, estimatedEndTime, phraseText))
                                    lastPhraseEndTime = estimatedEndTime
                                }
                            }
                            is com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse.ErrorResponse -> {
                                throw response.e
                            }
                            else -> {}
                        }
                    }
                }
            } finally {
                client.close()
            }

            Log.i("VideoSubtitler", "Transcribed subtitle count: ${subtitleList.size}")
            if (subtitleList.isEmpty()) {
                return@withContext listOf(Subtitle(0, 3000, "No speech detected in video"))
            }
            subtitleList
        } catch (e: Exception) {
            Log.e("VideoSubtitler", "On-device speech recognition failed", e)
            listOf(
                Subtitle(0, 3000, "Speech recognition failed: ${e.message}"),
                Subtitle(3000, 6000, "Please verify Google Play Services are up-to-date")
            )
        } finally {
            try {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Draws the given subtitle directly onto a mutable video frame bitmap using Canvas.
     * Uses StaticLayout to automatically wrap text lines to fit inside portrait video widths.
     */
    fun drawSubtitleOnMutableBitmap(
        bitmap: Bitmap,
        subtitle: String,
        fontStyle: String = "Plain",
        fontSize: Float = 48f,
        fontColor: Int = Color.WHITE,
        x: Float = 40f,
        y: Float = 80f
    ) {
        val canvas = Canvas(bitmap)
        val paintColor = when (fontStyle) {
            "Neon Glow" -> Color.rgb(0, 240, 255)
            "Retro Sunset" -> Color.rgb(255, 65, 108)
            else -> fontColor
        }
        val paint = android.text.TextPaint().apply {
            color = paintColor
            textSize = fontSize
            isAntiAlias = true
        }

        when (fontStyle) {
            "Neon Glow" -> {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.setShadowLayer(12f, 0f, 0f, paintColor)
            }
            "Retro Sunset" -> {
                paint.typeface = Typeface.MONOSPACE
                paint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }
            "Bold Impact" -> {
                paint.typeface = Typeface.DEFAULT_BOLD
            }
            else -> {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }
        }

        val videoWidth = bitmap.width
        val maxWidth = (videoWidth * 0.85f).toInt()

        val builder = android.text.StaticLayout.Builder.obtain(
            subtitle,
            0,
            subtitle.length,
            paint,
            maxWidth
        )
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.1f)
        .setIncludePad(true)
        val staticLayout = builder.build()

        // Center horizontally inside the video frame, and center vertically around the target y coordinate
        val xPos = (videoWidth - maxWidth) / 2f
        val yPos = y - staticLayout.height / 2f

        if (fontStyle == "Bold Impact") {
            val bgPaint = Paint().apply {
                color = Color.parseColor("#CC000000")
            }
            var maxLineWidth = 0f
            for (line in 0 until staticLayout.lineCount) {
                maxLineWidth = maxOf(maxLineWidth, staticLayout.getLineWidth(line))
            }
            val rectLeft = (videoWidth - maxLineWidth) / 2f - 20f
            val rectRight = (videoWidth + maxLineWidth) / 2f + 20f
            val rectTop = yPos - 10f
            val rectBottom = yPos + staticLayout.height + 10f
            canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, bgPaint)
        }

        canvas.save()
        canvas.translate(xPos, yPos)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * Draws the given subtitle onto a copy of the video frame using Canvas.
     */
    fun drawSubtitleOnBitmap(
        bitmap: Bitmap,
        subtitle: String,
        fontStyle: String = "Plain",
        fontSize: Float = 48f,
        fontColor: Int = Color.WHITE,
        x: Float = 40f,
        y: Float = 80f
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        drawSubtitleOnMutableBitmap(mutableBitmap, subtitle, fontStyle, fontSize, fontColor, x, y)
        return mutableBitmap
    }

    /**
     * Uses Media3 Transformer to decode the video, burn the dynamic styled subtitle overlays
     * onto video frames at the correct presentation timestamps, encode them, and copy to SAF Uri.
     */
    suspend fun exportVideoWithSubtitlesMux(
        context: Context,
        inputUri: Uri,
        subtitles: List<Subtitle>,
        fontStyle: String,
        fontSize: Float,
        fontColor: Int,
        outputUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        var retriever: android.media.MediaMetadataRetriever? = null
        try {
            Log.i("VideoSubtitler", "Starting Media3 styled burn-in transcode export")
            
            // Extract video dimensions from metadata
            retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, inputUri)
            val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            
            val originalWidth = widthStr?.toIntOrNull() ?: 1280
            val originalHeight = heightStr?.toIntOrNull() ?: 720
            val rotation = rotationStr?.toIntOrNull() ?: 0
            
            // If rotation is 90 or 270, the display width/height are swapped!
            val videoWidth = if (rotation == 90 || rotation == 270) originalHeight else originalWidth
            val videoHeight = if (rotation == 90 || rotation == 270) originalWidth else originalHeight
            Log.i("VideoSubtitler", "Video dimensions resolved: ${videoWidth}x${videoHeight} (Rotation: ${rotation}deg)")

            try { retriever.release() } catch (ignored: Exception) {}
            retriever = null

            val isHdr = isVideoHdr(context, inputUri)
            Log.i("VideoSubtitler", "Is source video HDR: $isHdr")
            val hdrMode = if (isHdr) {
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            } else {
                Composition.HDR_MODE_KEEP_HDR
            }

            val transformer = Transformer.Builder(context).build()

            val bitmapOverlay = object : BitmapOverlay() {
                var cachedBitmap: Bitmap? = null

                override fun getBitmap(presentationTimeUs: Long): Bitmap {
                    if (cachedBitmap == null) {
                        cachedBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
                    }
                    val bmp = cachedBitmap!!
                    bmp.eraseColor(Color.TRANSPARENT)

                    val timeMs = presentationTimeUs / 1000
                    val sub = subtitles.find { timeMs in it.startTime..it.endTime }
                    val text = sub?.text ?: ""

                    if (text.isNotBlank()) {
                        val yPos = videoHeight * 0.85f
                        drawSubtitleOnMutableBitmap(
                            bitmap = bmp,
                            subtitle = text,
                            fontStyle = fontStyle,
                            fontSize = fontSize,
                            fontColor = fontColor,
                            x = 0f,
                            y = yPos
                        )
                    }
                    return bmp
                }

                override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                    // Since the overlay matches the video size 1:1, we use default overlay settings (centered, 1:1 scale)
                    return OverlaySettings.Builder().build()
                }
            }

            val overlayEffect = OverlayEffect(ImmutableList.copyOf(listOf(bitmapOverlay)))
            val effects = Effects(listOf(), listOf(overlayEffect))

            val mediaItem = MediaItem.fromUri(inputUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            val sequence = EditedMediaItemSequence(editedMediaItem)
            val composition = Composition.Builder(listOf(sequence))
                .setHdrMode(hdrMode)
                .build()

            val tempOutputFile = File.createTempFile("subtitled_transcode", ".mp4", context.cacheDir)

            val success = suspendCoroutine<Boolean> { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        continuation.resume(true)
                    }

                    override fun onError(
                        comp: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e("VideoSubtitler", "Media3 Export failed", exportException)
                        continuation.resume(false)
                    }
                }

                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post {
                    try {
                        transformer.addListener(listener)
                        transformer.start(composition, tempOutputFile.absolutePath)
                    } catch (e: Exception) {
                        Log.e("VideoSubtitler", "Failed to start Media3 transformer", e)
                        continuation.resume(false)
                    }
                }
            }

            if (success && tempOutputFile.exists() && tempOutputFile.length() > 0) {
                // Copy the complete transcoded file to SAF
                context.contentResolver.openOutputStream(outputUri, "rwt")?.use { out ->
                    tempOutputFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                try { tempOutputFile.delete() } catch (ignored: Exception) {}
                try { bitmapOverlay.cachedBitmap?.recycle() } catch (ignored: Exception) {}
                true
            } else {
                try { tempOutputFile.delete() } catch (ignored: Exception) {}
                try { bitmapOverlay.cachedBitmap?.recycle() } catch (ignored: Exception) {}
                false
            }
        } catch (e: Exception) {
            try { retriever?.release() } catch (ignored: Exception) {}
            Log.e("VideoSubtitler", "Export failed in transcode stage", e)
            false
        }
    }

    private fun isVideoHdr(context: Context, videoUri: Uri): Boolean {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    if (format.containsKey(android.media.MediaFormat.KEY_COLOR_TRANSFER)) {
                        val transfer = format.getInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER)
                        if (transfer == android.media.MediaFormat.COLOR_TRANSFER_ST2084 ||
                            transfer == android.media.MediaFormat.COLOR_TRANSFER_HLG) {
                            return true
                        }
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("VideoSubtitler", "Error checking if video is HDR", e)
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
        }
        return false
    }
}
