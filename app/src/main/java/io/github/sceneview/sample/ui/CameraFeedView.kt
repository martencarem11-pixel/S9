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
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Live Camera background feed powered by Android Camera2.
 * Includes complete lifecycle management to prevent abandoned buffer queues.
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
            key(isMRMode) {
                CameraSurfacePreview(
                    isMR = isMRMode,
                    modifier = Modifier.fillMaxSize()
                )
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
 * Lifecycle-safe Camera2 Preview View.
 * Supports single camera preview for AR mode and double camera preview (Left Eye & Right Eye) for MR mode.
 */
@Composable
fun CameraSurfacePreview(
    isMR: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember(context, isMR) { CameraPreviewController(context) }

    DisposableEffect(controller) {
        onDispose {
            controller.stop()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isMR) {
            // Dual Camera Viewports: Left Eye & Right Eye side-by-side for stereoscopic MR
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Eye Camera
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("camera_preview_left")
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        controller.addSurface(holder.surface)
                                    }

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int
                                    ) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        controller.removeSurface(holder.surface)
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Center Divider
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF00E5FF).copy(alpha = 0.4f))
                )

                // Right Eye Camera
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("camera_preview_right")
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        controller.addSurface(holder.surface)
                                    }

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int
                                    ) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        controller.removeSurface(holder.surface)
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Ocular framing overlay
            MRStereoscopicCameraOverlay(
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Single full-screen Camera Viewport for AR mode
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                controller.addSurface(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {}

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                controller.removeSurface(holder.surface)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Controller that safely coordinates Camera2 opening, multi-surface capture session, and synchronous teardown.
 */
class CameraPreviewController(private val context: Context) {
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val activeSurfaces = java.util.Collections.synchronizedList(mutableListOf<Surface>())
    @Volatile
    private var isStopped = false
    private var pendingReconfigure: Runnable? = null

    @SuppressLint("MissingPermission")
    fun addSurface(surface: Surface) {
        if (!surface.isValid) return
        synchronized(activeSurfaces) {
            if (!activeSurfaces.contains(surface)) {
                activeSurfaces.add(surface)
            }
        }
        val bgHandler = handler
        if (bgHandler != null && cameraDevice != null) {
            pendingReconfigure?.let { bgHandler.removeCallbacks(it) }
            val run = Runnable { startOrReconfigureSession() }
            pendingReconfigure = run
            bgHandler.postDelayed(run, 50L)
        } else if (handler == null) {
            initCamera()
        }
    }

    fun removeSurface(surface: Surface) {
        synchronized(activeSurfaces) {
            activeSurfaces.remove(surface)
        }
        val count = synchronized(activeSurfaces) { activeSurfaces.size }
        if (count == 0) {
            stop()
        } else {
            val bgHandler = handler
            if (bgHandler != null && cameraDevice != null) {
                pendingReconfigure?.let { bgHandler.removeCallbacks(it) }
                val run = Runnable { startOrReconfigureSession() }
                pendingReconfigure = run
                bgHandler.postDelayed(run, 50L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initCamera() {
        stop()
        isStopped = false

        val thread = HandlerThread("CamPreviewThread_${System.currentTimeMillis()}").apply { start() }
        handlerThread = thread
        val bgHandler = Handler(thread.looper)
        handler = bgHandler

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) return

            val cameraId = cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraIdList.first()

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (isStopped) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    startOrReconfigureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice == camera) cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice == camera) cameraDevice = null
                }
            }, bgHandler)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera setup error", e)
        }
    }

    private fun startOrReconfigureSession() {
        val camera = cameraDevice ?: return
        val bgHandler = handler ?: return
        if (isStopped) return

        val validSurfaces = synchronized(activeSurfaces) { activeSurfaces.filter { it.isValid } }
        if (validSurfaces.isEmpty()) return

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null

            val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            for (s in validSurfaces) {
                previewRequestBuilder.addTarget(s)
            }
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            camera.createCaptureSession(
                validSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (isStopped) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequestBuilder.build(), null, bgHandler)
                        } catch (e: Exception) {
                            Log.w("CameraPreview", "Capture request stopped or error", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        if (captureSession == session) captureSession = null

                        // Resilient fallback: if multiple surfaces failed on this driver, try single primary surface
                        val fallbackSurface = synchronized(activeSurfaces) { activeSurfaces.firstOrNull { it.isValid } }
                        if (validSurfaces.size > 1 && fallbackSurface != null && !isStopped) {
                            try {
                                val singleReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                singleReq.addTarget(fallbackSurface)
                                camera.createCaptureSession(
                                    listOf(fallbackSurface),
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(s: CameraCaptureSession) {
                                            captureSession = s
                                            s.setRepeatingRequest(singleReq.build(), null, bgHandler)
                                        }

                                        override fun onConfigureFailed(s: CameraCaptureSession) {
                                            s.close()
                                        }
                                    },
                                    bgHandler
                                )
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Fallback session failed", e)
                            }
                        }
                    }

                    override fun onClosed(session: CameraCaptureSession) {
                        if (captureSession == session) captureSession = null
                    }
                },
                bgHandler
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to start capture session", e)
        }
    }

    fun stop() {
        isStopped = true
        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            captureSession?.close()
        } catch (e: Exception) {
            // session already closed or invalid
        }
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (e: Exception) {
            // camera device already closed
        }
        cameraDevice = null

        synchronized(activeSurfaces) {
            activeSurfaces.clear()
        }

        try {
            handlerThread?.quitSafely()
        } catch (e: Exception) {
            // thread already ended
        }
        handlerThread = null
        handler = null
    }
}

/**
 * Stereoscopic overlay framing for Mixed Reality mode.
 * Shows center divider line and ocular lens alignments for MR headset passthrough.
 */
@Composable
fun MRStereoscopicCameraOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Vertical stereoscopic divider line for left and right eyes
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00E5FF).copy(alpha = 0.6f),
                            Color(0xFF00E5FF),
                            Color(0xFF00E5FF).copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Alignment tick marks on center divider
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
        ) {
            val midY = size.height / 2f
            for (i in -4..4) {
                val y = midY + i * 24.dp.toPx()
                val tickWidth = if (i == 0) 18.dp.toPx() else 10.dp.toPx()
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = if (i == 0) 0.8f else 0.4f),
                    start = Offset((size.width - tickWidth) / 2f, y),
                    end = Offset((size.width + tickWidth) / 2f, y),
                    strokeWidth = 1.5f
                )
            }
        }
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

        // Center line if MR mode
        if (isMR) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.3f))
            )
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
