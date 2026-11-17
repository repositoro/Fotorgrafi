package com.repositoro.fotorgrafi.encoder

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class SocialVideoEncoder(private val context: Context) {

    var videoCapture: VideoCapture<Recorder>? = null
        private set

    private var activeRecording: Recording? = null

    val isRecording: Boolean
        get() = activeRecording != null

    fun initVideoCapture(): VideoCapture<Recorder> {
        val qualitySelector = QualitySelector.from(Quality.FHD)

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            // Kunci bitrate ke 22 Mbps agar detail video tajam & tidak pecah
            .setTargetVideoEncodingBitRate(22_000_000)
            .build()

        return VideoCapture.withOutput(recorder).also {
            videoCapture = it
        }
    }

    fun toggleRecording(onStateChanged: (isRecording: Boolean) -> Unit) {
        val currentRecording = activeRecording
        if (currentRecording != null) {
            currentRecording.stop()
            activeRecording = null
            onStateChanged(false)
            return
        }

        val capture = videoCapture ?: return

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Fotorgrafi")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        val pendingRecording = capture.output
            .prepareRecording(context, mediaStoreOutput)
            .apply {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    onStateChanged(true)
                    Toast.makeText(context, "Merekam 1080p CFR...", Toast.LENGTH_SHORT).show()
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    onStateChanged(false)
                    if (!recordEvent.hasError()) {
                        Toast.makeText(context, "Video tersimpan di Movies/Fotorgrafi", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("Fotorgrafi", "Error merekam video: ${recordEvent.error}")
                        Toast.makeText(context, "Gagal menyimpan video", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}