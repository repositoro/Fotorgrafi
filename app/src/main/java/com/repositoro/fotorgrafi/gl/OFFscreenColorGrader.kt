package com.repositoro.fotorgrafi.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OffscreenColorGrader {

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision highp float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform int uProfile;

        vec3 softHighlightRollOff(vec3 c) {
            return c / (1.0 + max(vec3(0.0), c - 0.72) * 0.45);
        }

        vec3 applySCurve(vec3 c, float strength) {
            vec3 x = clamp(c, 0.0, 1.0);
            vec3 curve = x * x * (3.0 - 2.0 * x);
            return mix(c, curve, strength);
        }

        vec3 adjustSaturation(vec3 c, float sat) {
            float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
            return mix(vec3(luma), c, sat);
        }

        void main() {
            vec4 tex = texture2D(uTexture, vTexCoord);
            vec3 color = tex.rgb;

            if (uProfile == 1) {
                color = softHighlightRollOff(color);
                color = applySCurve(color, 0.28);
                color.r *= 1.045;
                color.g *= 1.015;
                color.b *= 0.945;
                color = adjustSaturation(color, 1.14);
            } else if (uProfile == 2) {
                color = softHighlightRollOff(color);
                color = mix(vec3(0.03), vec3(0.97), color);
                color = applySCurve(color, 0.22);
                color.r *= 1.02;
                color.b *= 0.97;
                color = adjustSaturation(color, 0.92);
            } else {
                color = softHighlightRollOff(color);
                color = applySCurve(color, 0.12);
            }

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), tex.a);
        }
    """.trimIndent()

    fun gradeBitmap(source: Bitmap, profile: ColorProfile): Bitmap {
        val width = source.width
        val height = source.height

        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context: EGLContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val surface: EGLSurface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)

        EGL14.eglMakeCurrent(display, surface, surface, context)

        // Setup Shader
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        val program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
            GLES20.glUseProgram(it)
        }

        // Upload Bitmap ke Texture 2D
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, source, 0)

        val quadCoords = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        val vBuf: FloatBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quadCoords).apply { position(0) }
        val tBuf: FloatBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(texCoords).apply { position(0) }

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uProfile"), profile.id)

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, tBuf)

        GLES20.glViewport(0, 0, width, height)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        val outBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, outBuffer)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBuffer.rewind()
        result.copyPixelsFromBuffer(outBuffer)

        // Bersihkan resource EGL/OpenGL
        GLES20.glDeleteTextures(1, textures, 0)
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)

        return result
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }
    }
}