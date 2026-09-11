package io.github.sceneview.sample.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Live Camera background feed powered by Android Camera2.
 * Supports:
 * - Single full-screen camera view for AR
 * - Dual stereoscopic side-by-side (SBS) camera view for MR ("Double Camera")
 * - Dynamic fallback with spatial environment if camera hardware is unavailable
 */
@Composable
fun CameraFeedView(
    isMRMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_feed_container")
    ) {
        if (hasCameraPermission) {
            if (isMRMode) {
                // MR Mode: Stereoscopic Double Camera View (Side-by-Side)
                DualCameraPreview(modifier = Modifier.fillMaxSize())
            } else {
                // AR Mode: Single Full-screen Camera View
                SingleCameraPreview(modifier = Modifier.fillMaxSize())
            }
        } else {
            // Permission request overlay with futuristic spatial canvas
            SpatialCameraFallback(
                isMR = isMRMode,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Single full-screen Camera2 Preview for standard AR mode.
 */
@Composable
fun SingleCameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val handlerThread = HandlerThread("Camera2SingleThread").apply { start() }
        val handler = Handler(handlerThread.looper)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null

        onDispose {
            try {
                captureSession?.close()
                cameraDevice?.close()
                handlerThread.quitSafely()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error closing camera", e)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        startCameraPreview(ctx, listOf(Surface(st)))
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Stereoscopic Dual Camera2 Preview for MR mode ("Double Camera").
 * Left Eye and Right Eye side-by-side feeds from the camera sensor.
 */
@Composable
fun DualCameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var leftSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var rightSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    // When both textures are available, bind them to Camera2 capture session
    LaunchedEffect(leftSurfaceTexture, rightSurfaceTexture) {
        val left = leftSurfaceTexture
        val right = rightSurfaceTexture
        if (left != null && right != null) {
            val surfaces = listOf(Surface(left), Surface(right))
            startCameraPreview(context, surfaces)
        } else if (left != null) {
            startCameraPreview(context, listOf(Surface(left)))
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Left Eye Camera Feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                leftSurfaceTexture = st
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                leftSurfaceTexture = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Center stereoscopic divider line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 40.dp)
        )

        // Right Eye Camera Feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                rightSurfaceTexture = st
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                rightSurfaceTexture = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Starts Camera2 capture session feeding into the provided surfaces.
 */
@SuppressLint("MissingPermission")
private fun startCameraPreview(context: Context, surfaces: List<Surface>) {
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) return

        // Find back camera
        val cameraId = cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraIdList.first()

        val thread = HandlerThread("CameraPreviewThread").apply { start() }
        val handler = Handler(thread.looper)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                try {
                    val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    surfaces.forEach { surface ->
                        if (surface.isValid) {
                            previewRequestBuilder.addTarget(surface)
                        }
                    }

                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            try {
                                previewRequestBuilder.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, handler)
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Failed to start repeating request", e)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("CameraPreview", "Camera capture session configuration failed")
                        }
                    }, handler)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Failed to create capture session", e)
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }
        }, handler)
    } catch (e: Exception) {
        Log.e("CameraPreview", "Camera setup error", e)
    }
}

/**
 * Visual spatial fallback if camera permission is not yet granted.
 */
@Composable
fun SpatialCameraFallback(
    isMR: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0F14),
                        Color(0xFF141720),
                        Color(0xFF090A0E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Grid pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(size.height / step).toInt()) {
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                    start = Offset(0f, y * step),
                    end = Offset(size.width, y * step),
                    strokeWidth = 1f
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isMR) "MR Stereoscopic Camera Feed" else "AR Live Camera Feed",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isMR)
                    "Camera access is required for dual-eye stereoscopic Mixed Reality view."
                else
                    "Camera access is required to view 3D models in your physical environment.",
                color = Color(0xFF8F939D),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Enable Camera",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
