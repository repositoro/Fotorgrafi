package com.repositoro.fotorgrafi

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.repositoro.fotorgrafi.camera.CameraEngine
import com.repositoro.fotorgrafi.camera.SensorCapabilities
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var glViewFinder: GLSurfaceView
    private lateinit var cameraEngine: CameraEngine
    private lateinit var btnCapture: View
    private lateinit var btnColorTone: TextView
    private lateinit var btnTogglePro: TextView
    private lateinit var tabVideoMode: TextView
    private lateinit var tabPhotoMode: TextView
    private lateinit var proControlPanel: LinearLayout

    private lateinit var tvProLabel: TextView
    private lateinit var btnResetAuto: TextView
    private lateinit var seekEv: SeekBar
    private lateinit var seekIso: SeekBar
    private lateinit var seekShutter: SeekBar

    private var isVideoMode = false
    private var isProExpanded = false
    private var isManualMode = false
    private var currentEvText = "0.0"
    private var currentIsoValue = 200
    private var currentShutterNs = 16_666_666L

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
        btnColorTone = findViewById(R.id.btnColorTone)
        btnTogglePro = findViewById(R.id.btnTogglePro)
        tabVideoMode = findViewById(R.id.tabVideoMode)
        tabPhotoMode = findViewById(R.id.tabPhotoMode)
        proControlPanel = findViewById(R.id.proControlPanel)

        tvProLabel = findViewById(R.id.tvProLabel)
        btnResetAuto = findViewById(R.id.btnResetAuto)
        seekEv = findViewById(R.id.seekEv)
        seekIso = findViewById(R.id.seekIso)
        seekShutter = findViewById(R.id.seekShutter)

        cameraEngine = CameraEngine(this, this, glViewFinder)

        cameraEngine.onCapabilitiesReady = { caps ->
            runOnUiThread { setupProControls(caps) }
        }

        btnColorTone.setOnClickListener {
            val newProfile = cameraEngine.cycleColorProfile()
            btnColorTone.text = newProfile.label
        }

        btnTogglePro.setOnClickListener {
            isProExpanded = !isProExpanded
            proControlPanel.visibility = if (isProExpanded) View.VISIBLE else View.GONE
            btnTogglePro.text = if (isProExpanded) "PRO ▼" else "PRO ▲"
            btnTogglePro.setTextColor(if (isProExpanded) Color.parseColor("#FFD60A") else Color.WHITE)
        }

        tabPhotoMode.setOnClickListener { switchCameraMode(video = false) }
        tabVideoMode.setOnClickListener { switchCameraMode(video = true) }

        btnCapture.setOnClickListener {
            // Efek feedback tombol shutter ala iPhone
            btnCapture.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                btnCapture.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()

            if (isVideoMode) {
                cameraEngine.videoEncoder.toggleRecording { recording ->
                    btnCapture.background.setTint(
                        if (recording) Color.parseColor("#8E0000") else Color.parseColor("#FF3B30")
                    )
                }
            } else {
                cameraEngine.takePhoto()
            }
        }

        btnResetAuto.setOnClickListener {
            isManualMode = false
            cameraEngine.resetToAutoExposure()
            cameraEngine.sensorCapabilities?.let { caps ->
                seekEv.progress = -caps.minEv
            }
            currentEvText = "0.0"
            updateProStatusLabel()
        }

        if (allPermissionsGranted()) {
            cameraEngine.startCamera(isVideoMode)
        } else {
            r9yMnTm4NSzvG9rrwjM2ec8xZgh1cafXH8.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun switchCameraMode(video: Boolean) {
        if (isVideoMode == video) return
        if (cameraEngine.videoEncoder.isRecording) {
            Toast.makeText(this, "Hentikan rekaman terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        isVideoMode = video
        val iosYellow = Color.parseColor("#FFD60A")
        val iosRed = Color.parseColor("#FF3B30")

        tabPhotoMode.setTextColor(if (!isVideoMode) iosYellow else Color.WHITE)
        tabVideoMode.setTextColor(if (isVideoMode) iosYellow else Color.WHITE)

        btnCapture.background.setTint(
            if (isVideoMode) iosRed else Color.WHITE
        )

        val params = glViewFinder.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = if (isVideoMode) "9:16" else "3:4"
        glViewFinder.layoutParams = params

        cameraEngine.startCamera(isVideoMode)
    }

    private fun setupProControls(caps: SensorCapabilities) {
        val totalEvSteps = caps.maxEv - caps.minEv
        seekEv.max = totalEvSteps
        seekEv.progress = -caps.minEv

        seekEv.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                isManualMode = false
                val evIndex = progress + caps.minEv
                cameraEngine.setExposureCompensation(evIndex)
                val evValue = evIndex * caps.evStep
                currentEvText = String.format(Locale.US, "%+.1f", evValue)
                updateProStatusLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekIso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                isManualMode = true
                val ratio = progress / 100.0
                currentIsoValue = (caps.minIso + (caps.maxIso - caps.minIso) * ratio).roundToInt()
                cameraEngine.setManualIsoAndShutter(currentIsoValue, currentShutterNs)
                updateProStatusLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekShutter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                isManualMode = true
                val ratio = progress / 100.0
                val minLog = caps.minExposureNs.toDouble()
                val maxLog = caps.maxExposureNs.toDouble()
                currentShutterNs = (minLog * (maxLog / minLog).pow(ratio)).toLong()
                cameraEngine.setManualIsoAndShutter(currentIsoValue, currentShutterNs)
                updateProStatusLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateProStatusLabel() {
        if (!isManualMode) {
            tvProLabel.text = "EV: $currentEvText  |  ISO: AUTO  |  SS: AUTO"
        } else {
            val denom = (1_000_000_000.0 / currentShutterNs).roundToInt()
            val ssText = if (denom >= 2) "1/${denom}s" else String.format(Locale.US, "%.1fs", currentShutterNs / 1e9)
            tvProLabel.text = "EV: MANUAL  |  ISO: $currentIsoValue  |  SS: $ssText"
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