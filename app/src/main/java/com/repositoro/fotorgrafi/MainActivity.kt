package com.repositoro.fotorgrafi

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.repositoro.fotorgrafi.camera.CameraEngine

class MainActivity : AppCompatActivity() {

    private lateinit var glViewFinder: GLSurfaceView
    private lateinit var cameraEngine: CameraEngine
    private lateinit var btnCapture: FloatingActionButton
    private lateinit var btnSwitchMode: MaterialButton
    private lateinit var btnColorTone: MaterialButton

    private var isVideoMode = false

    private val r9yMnTm4NSzvG9rrwjM2ec8xZgh1cafXH8 =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true) {
                cameraEngine.startCamera(isVideoMode)
            } else {
                Toast.makeText(this, "Izin kamera wajib diaktifkan", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        glViewFinder = findViewById(R.id.glViewFinder)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchMode = findViewById(R.id.btnSwitchMode)
        btnColorTone = findViewById(R.id.btnColorTone)

        cameraEngine = CameraEngine(this, this, glViewFinder)

        btnColorTone.setOnClickListener {
            val newProfile = cameraEngine.cycleColorProfile()
            btnColorTone.text = newProfile.label
        }

        btnSwitchMode.setOnClickListener {
            if (cameraEngine.videoEncoder.isRecording) {
                Toast.makeText(this, "Hentikan rekaman terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isVideoMode = !isVideoMode
            btnSwitchMode.text = if (isVideoMode) "MODE: VIDEO (1080p60)" else "MODE: FOTO"
            btnCapture.backgroundTintList = ColorStateList.valueOf(
                if (isVideoMode) Color.parseColor("#FF3B30") else Color.WHITE
            )
            cameraEngine.startCamera(isVideoMode)
        }

        btnCapture.setOnClickListener {
            if (isVideoMode) {
                cameraEngine.videoEncoder.toggleRecording { recording ->
                    btnCapture.backgroundTintList = ColorStateList.valueOf(
                        if (recording) Color.parseColor("#8E0000") else Color.parseColor("#FF3B30")
                    )
                }
            } else {
                cameraEngine.takePhoto()
            }
        }

        if (allPermissionsGranted()) {
            cameraEngine.startCamera(isVideoMode)
        } else {
            r9yMnTm4NSzvG9rrwjM2ec8xZgh1cafXH8.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        glViewFinder.onResume()
    }

    override fun onPause() {
        glViewFinder.onPause()
        super.onPause()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}