package com.repositoro.fotorgrafi.gl

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GlCameraRenderer(
    private val glSurfaceView: GLSurfaceView,
    private val onSurfaceReady: (Surface) -> Unit
) : GLSurfaceView.Renderer {

    private val shaderProgram = CineShaderProgram()
    private var oesTextureId = -1
    private var surfaceTexture: SurfaceTexture? = null
    private val texMatrix = FloatArray(16)

    @Volatile
    var currentProfile: ColorProfile = ColorProfile.IPHONE_WARM

    @Volatile
    private var captureCallback: ((Bitmap) -> Unit)? = null

    private var viewportWidth = 0
    private var viewportHeight = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        shaderProgram.init()

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                glSurfaceView.requestRender()
            }
        }

        val surface = Surface(surfaceTexture)
        glSurfaceView.post {
            onSurfaceReady(surface)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)

        shaderProgram.draw(oesTextureId, texMatrix, currentProfile)

        // Jika ada permintaan jepret foto, baca piksel ter-grading langsung dari framebuffer GPU
        val callback = captureCallback
        if (callback != null && viewportWidth > 0 && viewportHeight > 0) {
            captureCallback = null
            val bitmap = readPixelsToBitmap(viewportWidth, viewportHeight)
            callback(bitmap)
        }
    }

    fun captureGradedFrame(onCaptured: (Bitmap) -> Unit) {
        captureCallback = onCaptured
        glSurfaceView.requestRender()
    }

    fun setTextureBufferSize(width: Int, height: Int) {
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    private fun readPixelsToBitmap(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

        val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        rawBitmap.copyPixelsFromBuffer(buffer)

        // OpenGL membaca koordinat Y dari bawah ke atas, balikkan vertikal agar orientasi normal
        val flipMatrix = Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
        val corrected = Bitmap.createBitmap(rawBitmap, 0, 0, width, height, flipMatrix, true)
        rawBitmap.recycle()
        return corrected
    }
}