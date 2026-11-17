package com.repositoro.fotorgrafi.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
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
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import com.repositoro.fotorgrafi.gl.OffscreenColorGrader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SensorCapabilities(
    val minEv: Int,
    val maxEv: Int,
    val evStep: Float,
    val minIso: Int,
    val maxIso: Int,
    val minExposureNs: Long,
    val maxExposureNs: Long
)

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val glSurfaceView: GLSurfaceView
) {
    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var glSurface: Surface? = null
    private var pendingVideoMode: Boolean = false
    private val glExecutor = Executors.newSingleThreadExecutor()
    private val photoExecutor = Executors.newSingleThreadExecutor()
    private val offscreenGrader = OffscreenColorGrader()

    val videoEncoder = SocialVideoEncoder(context)
    val renderer: GlCameraRenderer

    var sensorCapabilities: SensorCapabilities? = null
        private set

    var onCapabilitiesReady: ((SensorCapabilities) -> Unit)? = null

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
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(98)
                        .setResolutionSelector(resolutionSelector)
                        .build()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }

                activeCamera = camera
                cameraControl = camera.cameraControl
                readSensorCapabilities(camera)
                setupTapToFocus()
            } catch (exc: Exception) {
                Log.e("Fotorgrafi", "Gagal mengikat kamera ke OpenGL", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun readSensorCapabilities(camera: Camera) {
        val exposureState = camera.cameraInfo.exposureState
        val evRange = exposureState.exposureCompensationRange
        val evStep = exposureState.exposureCompensationStep.toFloat()

        val cam2Info = Camera2CameraInfo.from(camera.cameraInfo)
        val isoRange = cam2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ?: Range(50, 3200)
        val expRange = cam2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?: Range(1_000_000L, 250_000_000L)

        // Batasi shutter maksimal di 1/4 detik (250ms) agar handheld tetap tajam tanpa blur ekstrem
        val clampedMaxExp = minOf(expRange.upper, 250_000_000L)

        val caps = SensorCapabilities(
            minEv = evRange.lower,
            maxEv = evRange.upper,
            evStep = evStep,
            minIso = isoRange.lower,
            maxIso = minOf(isoRange.upper, 6400),
            minExposureNs = maxOf(expRange.lower, 125_000L), // 1/8000s
            maxExposureNs = clampedMaxExp
        )
        sensorCapabilities = caps
        onCapabilitiesReady?.invoke(caps)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setExposureCompensation(index: Int) {
        val cam = activeCamera ?: return
        val cam2Control = Camera2CameraControl.from(cam.cameraControl)
        val resetAeOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .build()
        cam2Control.setCaptureRequestOptions(resetAeOptions)
        cam.cameraControl.setExposureCompensationIndex(index)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setManualIsoAndShutter(iso: Int, exposureTimeNs: Long) {
        val cam = activeCamera ?: return
        val cam2Control = Camera2CameraControl.from(cam.cameraControl)
        val manualOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs)
            .build()
        cam2Control.setCaptureRequestOptions(manualOptions)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun resetToAutoExposure() {
        val cam = activeCamera ?: return
        val cam2Control = Camera2CameraControl.from(cam.cameraControl)
        val autoOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .build()
        cam2Control.setCaptureRequestOptions(autoOptions)
        cam.cameraControl.setExposureCompensationIndex(0)
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
        val capture = imageCapture ?: return
        val activeProfile = renderer.currentProfile

        capture.takePicture(
            photoExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val rotation = image.imageInfo.rotationDegrees
                        image.close()

                        var decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            val rotated = Bitmap.createBitmap(
                                decoded, 0, 0, decoded.width, decoded.height, matrix, true
                            )
                            decoded.recycle()
                            decoded = rotated
                        }

                        val graded = offscreenGrader.gradeBitmap(decoded, activeProfile)
                        decoded.recycle()

                        saveBitmapToGallery(graded)
                        graded.recycle()
                    } catch (e: Exception) {
                        Log.e("Fotorgrafi", "Gagal memproses foto resolusi penuh", e)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Fotorgrafi", "Gagal menangkap foto: ${exc.message}", exc)
                    glSurfaceView.post {
                        Toast.makeText(context, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            glSurfaceView.post {
                Toast.makeText(
                    context,
                    "Tersimpan (${bitmap.width}x${bitmap.height})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}