//
//  MainActivity.kt
//
//  Copyright 2011-2026 Vision Smarts SRL. All rights reserved.
//

package com.visionsmarts.barcode

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.visionsmarts.barcode.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        val view = binding.root
        setContentView(view)

        binding.buttonScanOne.setOnClickListener {
            startScanner(batch= false, frame=false, redline=false)
        }
        binding.buttonBatchScan.setOnClickListener {
            startScanner(batch= true, frame=false, redline=false)
        }
        binding.buttonScanInFrame.setOnClickListener {
            startScanner(batch= false, frame=true, redline=false)
        }
        binding.buttonRedLineScan.setOnClickListener {
            startScanner(batch= false, frame=false, redline=true)
        }

        binding.buttonShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, binding.resultsView.text.toString() + "\nScanned with Vision Smarts")
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }

        binding.buttonInfo.setOnClickListener {
            val infoIntent = Intent(Intent.ACTION_VIEW)
            infoIntent.data = Uri.parse("https://www.visionsmarts.com/")
            startActivity(infoIntent)
        }

        binding.resultsView.movementMethod = ScrollingMovementMethod()
    }

    private fun startScanner(batch: Boolean, frame: Boolean, redline: Boolean) {
        val symbologyMask = binding.buttonEANUPC.activeMask
        if (symbologyMask == 0) {
            val myToast = Toast.makeText(
                applicationContext,
                "Please select at least one barcode format",
                Toast.LENGTH_SHORT
            )
            myToast.setGravity(Gravity.TOP, 0, 150)
            myToast.show()
            return
        }

        val intent = Intent(this, ScannerActivity::class.java).apply {
            putExtra("EXTRA_BATCH_SCAN", batch)
            putExtra("EXTRA_FRAME_SCAN", frame)
            putExtra("EXTRA_REDLINE_SCAN", redline)
            putExtra("EXTRA_SYMBOLOGIES", binding.buttonEANUPC.activeMask) // could ask any ToggleGroupButton
        }
        startScannerActivity.launch(intent)
    }

    private val startScannerActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        binding.resultsView.text =
            when (result.resultCode) {
                RESULT_OK ->
                    result.data?.getStringArrayListExtra("decodedBarcodeList")
                        ?.joinToString(separator = "\n") ?: ""
                else -> ""
            }
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName
    }
}
