package com.repositoro.fotorgrafi.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.opengl.GLSurfaceView
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.MotionEvent
import android.view.Surface
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.repositoro.fotorgrafi.encoder.SocialVideoEncoder
import com.repositoro.fotorgrafi.gl.ColorProfile
import com.repositoro.fotorgrafi.gl.GlCameraRenderer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val glSurfaceView: GLSurfaceView
) {
    private var cameraControl: CameraControl? = null
    private var glSurface: Surface? = null
    private var pendingVideoMode: Boolean = false
    private val glExecutor = Executors.newSingleThreadExecutor()

    val videoEncoder = SocialVideoEncoder(context)
    val renderer: GlCameraRenderer

    init {
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = GlCameraRenderer(glSurfaceView) { surface ->
            glSurface = surface
            bindCameraUseCases(pendingVideoMode)
        }
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    fun startCamera(isVideoMode: Boolean = false) {
        pendingVideoMode = isVideoMode
        if (glSurface != null) {
            bindCameraUseCases(isVideoMode)
        }
    }

    fun cycleColorProfile(): ColorProfile {
        val profiles = ColorProfile.entries
        val nextIndex = (renderer.currentProfile.ordinal + 1) % profiles.size
        val nextProfile = profiles[nextIndex]
        renderer.currentProfile = nextProfile
        glSurfaceView.requestRender()
        return nextProfile
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases(isVideoMode: Boolean) {
        val targetSurface = glSurface ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val aspectStrategy = if (isVideoMode) {
                AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            } else {
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectStrategy)
                .build()

            val previewBuilder = Preview.Builder()
                .setResolutionSelector(resolutionSelector)

            if (isVideoMode) {
                val extender = Camera2Interop.Extender(previewBuilder)
                // Gunakan 30fps konstan di kondisi indoor/redup agar noise & burik hilang,
                // atau ubah ke Range(60, 60) khusus di outdoor terang benderang
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(30, 30)
                )
            }

            val preview = previewBuilder.build().also {
                it.setSurfaceProvider { request ->
                    val res = request.resolution
                    glSurfaceView.post {
                        renderer.setTextureBufferSize(res.width, res.height)
                        request.provideSurface(targetSurface, glExecutor) {}
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                val camera = if (isVideoMode) {
                    val videoCapture = videoEncoder.initVideoCapture()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        videoCapture
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                }

                cameraControl = camera.cameraControl
                setupTapToFocus()
            } catch (exc: Exception) {
                Log.e("Fotorgrafi", "Gagal mengikat kamera ke OpenGL", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupTapToFocus() {
        glSurfaceView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = SurfaceOrientedMeteringPointFactory(
                    view.width.toFloat(),
                    view.height.toFloat()
                )
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                cameraControl?.startFocusAndMetering(action)
                view.performClick()
            }
            true
        }
    }

    fun takePhoto() {
        renderer.captureGradedFrame { bitmap ->
            saveBitmapToGallery(bitmap)
            bitmap.recycle()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Fotorgrafi")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                // Simpan kualitas 95% agar detail color grading tetap utuh saat upload sosmed
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            glSurfaceView.post {
                Toast.makeText(context, "Foto ter-grading tersimpan!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}