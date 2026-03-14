//
//  ScannerActivity.kt
//
//  Copyright 2011-2026 Vision Smarts SRL. All rights reserved.
//

package com.visionsmarts.barcode

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.graphics.Point
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.visionsmarts.BarQrCodeUtil
import com.visionsmarts.VSBarcodeReader
import com.visionsmarts.barcode.databinding.ScannerBinding
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ScannerActivity : AppCompatActivity() {
    private lateinit var binding: ScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var sensorRotation: Int = 0

    private var mBatchScanning = false
    private var mScanInFrame = false
    private var mRedLineScan = false
    private var mSymbologies = 0x8007
    private var mPreferFrontCamera = false
    private var mDecodedBarcodeList = ArrayList<String>()
    private var mHasCameraAutoFocus = false

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialize barcode reader library
        VSBarcodeReader.VSinit()

        Log.d(TAG, "created")

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * When activity is started:
     *
     *  * checks permission to access camera and start camera if permission granted
     *  * prevents screen from dimming during scan
     *
     */
    override fun onStart() {
        super.onStart()
        if (checkCameraPermission()) {
            // prevent screen dimming during (batch) scan
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startCamera()
        }
    }

    /**
     * When activity is paused:
     *
     * * allows screen to dim again
     *
     */
    override fun onPause() {
        Log.d(TAG, "onPause()")

        // allow screen dimming again
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onPause()
    }

    /**
     * If a camera issue is encountered, display message and finish activity.
     */
    private fun onCameraIssue() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.camera_issue_msg))
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    /**
     * Sets whether front or back camera is preferred (default is back camera).
     *
     * @param preferFrontCamera true if front camera preferred, false if back camera is preferred
     */
    fun setPreferFrontCamera(preferFrontCamera: Boolean) {
        mPreferFrontCamera = preferFrontCamera
    }

    /**
     * Starts the scanner.
     */
    private fun startCamera() {

        VSBarcodeReader.reset()

        val cameraController = LifecycleCameraController(baseContext)
        val previewView: PreviewView = binding.viewFinder
        cameraController.setImageAnalysisAnalyzer(
            cameraExecutor
        ) { imageProxy ->
            decode(imageProxy.planes[0],
                imageProxy.width, imageProxy.height)
            imageProxy.close()
        }

        if (mPreferFrontCamera && cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cameraSelector = cameraController.cameraSelector
        }

        // The following two lines are a workaround for a device bug (Redmi 12C)
        cameraController.imageAnalysisTargetSize = CameraController.OutputSize(Size(1080,1920))
        cameraController.imageCaptureTargetSize = CameraController.OutputSize(Size(1080,1920))
        cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST // default
        // OUTPUT_IMAGE_FORMAT_YUV_420_888 is the default and is what we want
        cameraController.isTapToFocusEnabled = true
        cameraController.isPinchToZoomEnabled = true

        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.controller = cameraController
        cameraController.bindToLifecycle(this)

        val initFuture = cameraController.initializationFuture
        initFuture.addListener({
            try {
                initFuture.get()
                setupScannerButtons()
            } catch (exception: Exception) {
                onCameraIssue()
            }
        }, ContextCompat.getMainExecutor(this))
    }


    /**
     * Setup the UI.
     */
    private fun setupScannerButtons() {
        val controller = binding.viewFinder.controller!!
        val multipleCameras = controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                && controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        if (multipleCameras) {
            binding.buttonFlip.setOnClickListener {
                flipCamera()
            }
        }
        else {
            binding.buttonFlip.visibility = View.INVISIBLE
        }

        binding.buttonTorch.setOnClickListener {
            toggleTorch()
        }

        binding.buttonCancelDone.setOnClickListener {
            doneScanner()
        }

        // set which symbologies scanner live view needs to decode
        mSymbologies = intent.getIntExtra("EXTRA_SYMBOLOGIES", 0x8007)

        // set scanning modes and corresponding overlays
        mBatchScanning = intent.getBooleanExtra("EXTRA_BATCH_SCAN", false)

        mScanInFrame = intent.getBooleanExtra("EXTRA_FRAME_SCAN", false)

        mRedLineScan = intent.getBooleanExtra("EXTRA_REDLINE_SCAN", false)

        binding.scannerOverlay.visibility = if (!mScanInFrame && !mRedLineScan) ImageView.VISIBLE else ImageView.GONE
        binding.scannerFrameOverlay.visibility = if (mScanInFrame) ImageView.VISIBLE else ImageView.GONE
        binding.scannerRedlineOverlay.visibility = if (mRedLineScan) ImageView.VISIBLE else ImageView.GONE

        updateRotationAndTorchAndFocus()
    }

    /**
     * Setup the UI and records focus info after change of camera.
     */
    private fun updateRotationAndTorchAndFocus() {
        val controller = binding.viewFinder.controller!!

        sensorRotation = controller.cameraInfo?.sensorRotationDegrees ?: 0

        if (CameraSelector.DEFAULT_BACK_CAMERA == controller.cameraSelector) {
            // We'll assume that the back camera always has autofocus, as method below is not
            // fully reliable
            mHasCameraAutoFocus = true
        } else {
            val factory = binding.viewFinder.meteringPointFactory
            val point = factory.createPoint(0.5f, 0.5f, 1.0f)
            val action = FocusMeteringAction.Builder(point).build()
            val info = controller.cameraInfo
            info?.let {
                mHasCameraAutoFocus = it.isFocusMeteringSupported(action)
            }
        }

        if (controller.cameraInfo?.hasFlashUnit() == true) {
            binding.buttonTorch.isVisible = true
        } else {
            binding.buttonTorch.isVisible = false
            return
        }

        val isTorchOn = controller.torchState.value == TorchState.ON
        if (isTorchOn) {
            binding.buttonTorch.setImageResource(R.drawable.ic_torch_selected)
        } else {
            binding.buttonTorch.setImageResource(R.drawable.ic_torch)
        }
    }


    /**
     * Flips between back and front cameras.
     */
    private fun flipCamera() {
        val controller = binding.viewFinder.controller!!
        if (CameraSelector.DEFAULT_FRONT_CAMERA == controller.cameraSelector) {
            controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
        else {
            controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
        cameraSelector = controller.cameraSelector
        updateRotationAndTorchAndFocus()
    }

    /**
     * Toggles torch on/off.
     */
    private fun toggleTorch() {
        val controller = binding.viewFinder.controller!!
        val isTorchOn = controller.torchState.value == TorchState.ON
        controller.enableTorch(!isTorchOn)
        updateRotationAndTorchAndFocus()
    }

    /**
     * The main barcode scanning function:
     * * sets the active scanning area if not the entire image.
     * * calls the VSBarcodeReader library with each frame acquired from the camera.
     * * validates any decoded barcode before passing it to the application.
     */
    private fun decode(luminancePlane: ImageProxy.PlaneProxy,
                       width: Int, height: Int) {

        val buffer = luminancePlane.buffer
        val stride = luminancePlane.rowStride
        val frameData = ByteArray(height * width)
        buffer.rewind()
        if (width == stride) {
            buffer.get(frameData)
        }
        else { // for completeness, but there is no evidence this ever happens
            for (r in 0 until height) {
                buffer.position(r*stride)
                buffer.get(frameData, r*width, width)
            }
        }


        Log.d(TAG, "decode() w:$width h:$height AF:$mHasCameraAutoFocus")

        // Accepts blurry barcode on an autofocus device
        VSBarcodeReader.setBlurryAcceptanceThresholdWithAF(0.0)

        // For optimal speed, only activate the symbologies(s) you need (mSymbologies)
        // Also, symbologies like ITF with weak or no error detection can cause misreads
        // They need to be validated before stopping the scanner
        // Omnidirectional decoding: searches all locations and orientations
        var topLeft = VSBarcodeReader.VSPoint(0.0, 0.0)
        var bottomRight = VSBarcodeReader.VSPoint(1.0, 1.0)

        val widthHeightRatio = width.toDouble() / height.toDouble()
        var activePercentage = 1.0
        val narrowFrame = false // future use
        if (mScanInFrame) {
            activePercentage = if (narrowFrame) { // height of narrow frame is 1/8 of view width
                0.125
            } else { // height of tall frame is 1/2 of view width
                0.5
            }
            activePercentage /= widthHeightRatio
        } else if (mRedLineScan) {
            activePercentage = 0.0 // that is how we signal the single-line scanning mode
        }

        if (activePercentage != 1.0) {
            topLeft = VSBarcodeReader.VSPoint(x=0.5-activePercentage/2.0, y=0.0)
            bottomRight = VSBarcodeReader.VSPoint(x=0.5+activePercentage/2.0, y=1.0)
        }

        val overlayCorners: MutableList<Array<Point>> = mutableListOf()

        VSBarcodeReader.decodeNextImageMultiple(frameData, width, height,
            if (mHasCameraAutoFocus) 1 else 0,
            mSymbologies, topLeft, bottomRight).let { barcodeArray ->

            if (barcodeArray.isEmpty()) {
                return@let
            }

            for (barcodeData in barcodeArray) {
                var barcode = ""
                var type = 0

                // The text (String) of QR or DataMatrix is not decoded by the library
                // Do it here with application-appropriate character encoding defaults
                if ((barcodeData.symbology == VSBarcodeReader.BARCODE_TYPE_QR || barcodeData.symbology == VSBarcodeReader.BARCODE_TYPE_DATAMATRIX)
                ) {
                    val defaultCharset =
                        if (barcodeData.symbology == VSBarcodeReader.BARCODE_TYPE_QR) Charset.forName(
                            "UTF-8"
                        ) else Charset.forName("ISO-8859-1")

                    //                    Detect ECI if expected by the application
                    //                    if (barcodeData.data!!.size > 3) {
                    //                    val prefix : String = String(barcodeData.data!!.sliceArray(0..2),  Charset.forName("UTF-8"))
                    //                    if ((prefix == "]d4") || (prefix == "]d5") || (prefix == "]d6") ||
                    //                            (prefix == "]Q2") || (prefix == "]Q4") || (prefix == "]Q6")) {
                    //                       }
                    //                    }

                    barcodeData.data?.let {
                        barcodeData.text = BarQrCodeUtil.format(
                            it,
                            barcodeData.mode,
                            defaultCharset
                        )
                    }
                }

                if (null == barcodeData.text) {
                    continue
                }

                // crude trick to avoid misreads in demo
                // a real app would be able to validate against expected length, etc.
                if (barcodeData.symbology != mSymbologies) { // more than one was enabled
                    if ((barcodeData.symbology == VSBarcodeReader.BARCODE_TYPE_CODABAR) ||
                        (barcodeData.symbology == VSBarcodeReader.BARCODE_TYPE_ITF) ||
                        (barcodeData.symbology == VSBarcodeReader.BARCODE_TYPE_CODE39)
                    ) {
                        if (barcodeData.text!!.length < 4) {
                            continue
                        }
                    }
                }

                barcode = barcodeData.text!!
                type = barcodeData.symbology

                if (barcode.isNotEmpty()) {
                    Log.d(TAG, "decoded barcode of type $type : $barcode")

                    val newQuad: Array<Point> = arrayOf(
                        Point(barcodeData.corner1.x.toInt(), barcodeData.corner1.y.toInt()),
                        Point(barcodeData.corner2.x.toInt(), barcodeData.corner2.y.toInt()),
                        Point(barcodeData.corner4.x.toInt(), barcodeData.corner4.y.toInt()), // order!
                        Point(barcodeData.corner3.x.toInt(), barcodeData.corner3.y.toInt())
                    )
                    overlayCorners.add(newQuad)

                    val formattedBarcode = VSBarcodeReader.formatForDemo(barcode, type)
                    // Extra tests about barcode validity, checksums, having the expected set, etc., can be done here
                    // Here we only check that it's not already in present in scanned list
                    if (mDecodedBarcodeList.isEmpty() || null == mDecodedBarcodeList.find{it == formattedBarcode}) {
                        mDecodedBarcodeList.add(formattedBarcode)
                        setBarcodeText()

                        notifyBarcodeFound(barcode)
                    }

                    if (! mBatchScanning) {
                        break
                    }
                }
            }
        }

        // Display the quadrangles as overlay
        runOnUiThread {
            val lensFrontFacing = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
            binding.barcodeOverlayView.setCorners(overlayCorners,
                width, height,
                lensFrontFacing, sensorRotation)
        }

        val evaluationDays = VSBarcodeReader.getValidity()
        if (evaluationDays == 0) {
            Log.d(TAG, "****** VSBarcodeReader evaluation license has expired ******")
        } else if (evaluationDays > 0) {
            Log.d(TAG, "Evaluation license will expire in $evaluationDays days.")
        }

    }

    /**
     * Sets barcode list in barcode text view.
     */
    private fun setBarcodeText() {
        if (mDecodedBarcodeList.size == 0) {
            binding.barcodeText.text = resources.getString(R.string.scanner_scanning)
        } else {
            binding.barcodeText.post {
                binding.barcodeText.text = mDecodedBarcodeList.takeLast(10).joinToString("\n")
                    .plus("\n(${mDecodedBarcodeList.size} scanned)")
            }
        }
    }

    /**
     * Stops the scanner and send results back
     */
    private fun doneScanner() {

        setResult(
            Activity.RESULT_OK, intent.putStringArrayListExtra("decodedBarcodeList",
                ArrayList(mDecodedBarcodeList.takeLast(20)) ))
        finish()
    }

    /**
     * Notifies that a new barcode has been found:
     *
     *  * vibrates
     *
     *
     * @param barcode
     * barcode found
     */
    private fun notifyBarcodeFound(barcode: String) {
        Log.d(TAG, "notifyBarcodeFound($barcode)")

        Log.d(TAG, "buzz")
        @Suppress("DEPRECATION")
        val vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= 26) {
                it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100)
            }
        }

        // you can do something with the found barcode here: record it and keep scanning or finish the Activity

        if (!mBatchScanning) {
            doneScanner()
        }

        // replace "Cancel" by "Done" on bottom button
        binding.barcodescanner.post { binding.buttonCancelDone.text = getString(R.string.scanner_done) }
    }

    /**
     * Checks camera permission and request permission if not granted.
     * @return true if access to camera is granted, false otherwise
     */
    private fun checkCameraPermission(): Boolean {
        // request camera permissions if not granted
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (permissionGranted) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startCamera()
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    AlertDialog.Builder(this@ScannerActivity)
                        .setMessage(R.string.camera_permission_required)
                        .setPositiveButton(android.R.string.ok) { _, _ -> checkCameraPermission() }
                        .setOnCancelListener { finish() }
                        .show()
                } else {
                    // if user asked to not request permission again, show message and exit, permission has to be enabled in Android
                    AlertDialog.Builder(this@ScannerActivity)
                        .setMessage(R.string.camera_permission_required_enable_in_android)
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                        .setOnCancelListener { finish() }
                        .show()
                }
            }
        }
    }

    companion object {
        private val TAG = ScannerActivity::class.java.simpleName
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1418
    }
}