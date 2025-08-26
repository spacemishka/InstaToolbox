package com.spacemishka.app.instatoolbox

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VideoCaptioner provides functions to generate captions using an on-device LLM,
 * render captions on video frames using Canvas for real-time editing,
 * and export the final video with captions using FFMPEG.
 */
object VideoCaptioner {

    /**
     * Generates a caption for a given video segment using an on-device LLM.
     * Replace this placeholder with actual LLM integration.
     */
    /**
     * Extracts audio from a video file and returns the path to the extracted audio file (WAV).
     */
    fun extractAudioFromVideo(context: Context, videoPath: String): String? {
        // Handles content Uris by copying to a temp file if needed
        val outputFile = java.io.File(context.cacheDir, "extracted_audio.aac")
        try {
            val extractor = android.media.MediaExtractor()
            val uri = try {
                android.net.Uri.parse(videoPath)
            } catch (e: Exception) {
                null
            }
            val actualPath: String? = if (uri != null && videoPath.startsWith("content://")) {
                // Copy content Uri to temp file
                val tempFile = java.io.File(context.cacheDir, "temp_video")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.absolutePath
            } else if (videoPath.startsWith("/")) {
                videoPath
            } else {
                null
            }
            if (actualPath == null) return null
            extractor.setDataSource(actualPath)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex == -1) {
                extractor.release()
                return null
            }
            extractor.selectTrack(audioTrackIndex)
            val outStream = java.io.FileOutputStream(outputFile)
            val buffer = ByteArray(4096)
            while (true) {
                val sampleSize = extractor.readSampleData(java.nio.ByteBuffer.wrap(buffer), 0)
                if (sampleSize < 0) break
                outStream.write(buffer, 0, sampleSize)
                extractor.advance()
            }
            outStream.close()
            extractor.release()
            return outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Uses Android's SpeechRecognizer to perform on-device speech-to-text on the given audio file.
     * Returns a list of Caption objects.
     */
    suspend fun recognizeSpeechFromAudio(context: Context, audioPath: String): List<Caption> {
        // ML Kit's speech-to-text for audio files is not available in the current dependencies.
        // For production, use ML Kit's on-device transcription when available, or a third-party library.
        return withContext(Dispatchers.Default) {
            listOf(
                Caption(0, 5000, "Speech-to-text from audio file is not implemented. Use ML Kit or Vosk for real results.")
            )
        }
    }

    data class Caption(
        val startTime: Long,
        val endTime: Long,
        val text: String
    )

    /**
     * Generates SRT content from a list of captions.
     */
    fun generateSRT(captions: List<Caption>): String {
        fun formatTime(milliseconds: Long): String {
            val hours = milliseconds / 3600000
            val minutes = (milliseconds % 3600000) / 60000
            val seconds = (milliseconds % 60000) / 1000
            val millis = milliseconds % 1000
            return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
        }
        return captions.mapIndexed { index, caption ->
            """
            ${index + 1}
            ${formatTime(caption.startTime)} --> ${formatTime(caption.endTime)}
            ${caption.text}

            """.trimIndent()
        }.joinToString("\n")
    }

    /**
     * Generates captions for a video using on-device speech recognition.
     * Returns a list of Caption objects.
     */
    suspend fun generateCaptionsFromVideo(context: Context, videoPath: String): List<Caption> {
        // 1. Extract audio from video
        val audioPath = extractAudioFromVideo(context, videoPath)
        if (audioPath == null) {
            return listOf(Caption(0, 2000, "Audio extraction failed"))
        }
        // 2. Run speech recognition on audio
        return recognizeSpeechFromAudio(context, audioPath)
    }

    /**
     * Draws the given caption onto a video frame using Canvas.
     * Allows customization of font type and size.
     */
    fun drawCaptionOnBitmap(
        bitmap: Bitmap,
        caption: String,
        fontSize: Float = 48f,
        fontColor: Int = Color.WHITE,
        fontFamily: Typeface = Typeface.DEFAULT_BOLD,
        x: Float = 40f,
        y: Float = 80f
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = fontColor
            textSize = fontSize
            typeface = fontFamily
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(caption, x, y, paint)
        return mutableBitmap
    }

    /**
     * Exports the video with captions using FFMPEG for maximum compatibility.
     * Requires MobileFFmpeg library.
     * @param inputVideoPath Path to the input video file.
     * @param captionFilePath Path to the subtitle/caption file (e.g., .srt).
     * @param outputVideoPath Path to save the output video.
     */
    /**
     * Exports the video with captions using MediaCodec/MediaMuxer and Canvas overlays.
     * This is the recommended approach for compatibility with Instagram and TikTok.
     * @param inputVideoPath Path to the input video file.
     * @param caption The caption text to overlay.
     * @param outputVideoPath Path to save the output video.
     * @return true if export succeeds, false otherwise.
     */
    suspend fun exportVideoWithCaptions(
        inputVideoPath: String,
        caption: String,
        outputVideoPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        // TODO: Implement using MediaExtractor, MediaCodec, MediaMuxer, and Canvas for overlays.
        // 1. Extract video frames using MediaExtractor/MediaCodec.
        // 2. Draw caption on each frame using Canvas.
        // 3. Encode frames back to video using MediaCodec/MediaMuxer.
        // 4. Copy audio track if present.
        // This is a complex process and should be implemented for production use.
        // For now, return false as a stub.
        false
    }
}