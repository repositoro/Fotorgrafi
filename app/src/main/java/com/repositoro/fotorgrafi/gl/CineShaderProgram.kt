package com.repositoro.fotorgrafi.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

enum class ColorProfile(val id: Int, val label: String) {
    NATURAL(0, "TONE: NATURAL"),
    IPHONE_WARM(1, "TONE: VIVID WARM"),
    FILMIC_SOFT(2, "TONE: FILMIC")
}

class CineShaderProgram {

    private var programId = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexMatrixLoc = 0
    private var uTextureLoc = 0
    private var uProfileLoc = 0

    // Koordinat quad layar penuh
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f,  1.0f,
        1.0f,  1.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertexCoords)
            position(0)
        }

    private val texBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(texCoords)
            position(0)
        }

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """.trimIndent()

    // Fragment Shader: Highlight roll-off, warm skin protection, & S-curve contrast
    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexture;
        uniform int uProfile;

        // Filmic highlight roll-off (mencegah langit/kulit pecah putih)
        vec3 softHighlightRollOff(vec3 c) {
            return c / (1.0 + max(vec3(0.0), c - 0.72) * 0.45);
        }

        // S-Curve kontras halus
        vec3 applySCurve(vec3 c, float strength) {
            vec3 x = clamp(c, 0.0, 1.0);
            vec3 curve = x * x * (3.0 - 2.0 * x);
            return mix(c, curve, strength);
        }

        // Saturasi selektif berbasis luma Rec.709
        vec3 adjustSaturation(vec3 c, float sat) {
            float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
            return mix(vec3(luma), c, sat);
        }

        void main() {
            vec4 tex = texture2D(uTexture, vTexCoord);
            vec3 color = tex.rgb;

            if (uProfile == 1) {
                // iPhone Vivid Warm: Warm skin tone + highlight lembut + punchy color
                color = softHighlightRollOff(color);
                color = applySCurve(color, 0.28);
                color.r *= 1.045;
                color.g *= 1.015;
                color.b *= 0.945;
                color = adjustSaturation(color, 1.14);
            } else if (uProfile == 2) {
                // Filmic Soft: Shadow terangkat + highlight creamy
                color = softHighlightRollOff(color);
                color = mix(vec3(0.03), vec3(0.97), color);
                color = applySCurve(color, 0.22);
                color.r *= 1.02;
                color.b *= 0.97;
                color = adjustSaturation(color, 0.92);
            } else {
                // Natural: Clean sRGB dengan proteksi highlight ringan
                color = softHighlightRollOff(color);
                color = applySCurve(color, 0.12);
            }

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), tex.a);
        }
    """.trimIndent()

    fun init() {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }

        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
        uProfileLoc = GLES20.glGetUniformLocation(programId, "uProfile")
    }

    fun draw(textureId: Int, texMatrix: FloatArray, profile: ColorProfile) {
        GLES20.glUseProgram(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)
        GLES20.glUniform1i(uProfileLoc, profile.id)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e("FotorgrafiGL", "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
            }
        }
    }
}