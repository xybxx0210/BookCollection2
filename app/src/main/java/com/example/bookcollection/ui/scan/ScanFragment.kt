package com.example.bookcollection.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.bookcollection.R
import com.example.bookcollection.data.network.NetworkModule
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class ScanFragment : Fragment(R.layout.fragment_scan) {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvResult: TextView
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // 请求相机权限
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            tvResult.text = "未获得相机权限，无法扫码"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = view.findViewById(R.id.viewFinder)
        tvResult = view.findViewById(R.id.tvResult)

        // 检查权限
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 预览
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // 图像分析（扫码）
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer { isbn ->
                // 扫码成功回调（UI线程更新状态）
                requireActivity().runOnUiThread {
                    tvResult.text = "正在获取图书信息: $isbn"
                }

                // =========== 第5步：发起网络请求 ===========
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = NetworkModule.googleBooksApi.searchBookByIsbn(isbn)
                        val book = response.items?.firstOrNull()?.volumeInfo

                        if (book != null) {
                            val title = book.title ?: "未知书名"
                            val author = book.authors?.joinToString(", ") ?: "未知作者"
                            val publisher = book.publisher ?: "未知出版社"

                            // 打印到 Logcat 验收
                            Log.d("API_TEST", "获取成功！")
                            Log.d("API_TEST", "书名: $title")
                            Log.d("API_TEST", "作者: $author")
                            Log.d("API_TEST", "出版社: $publisher")
                            Log.d("API_TEST", "封面: ${book.imageLinks?.thumbnail}")

                            // 更新到界面上
                            requireActivity().runOnUiThread {
                                tvResult.text = "书名: $title\n作者: $author\n出版社: $publisher"
                            }
                        } else {
                            requireActivity().runOnUiThread { tvResult.text = "未查到该图书信息" }
                        }
                    } catch (e: Exception) {
                        Log.e("API_TEST", "网络请求失败: ${e.message}")
                        requireActivity().runOnUiThread { tvResult.text = "网络异常，请重试" }
                    }
                }
                // =========================================
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("ScanFragment", "绑定相机失败", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // 内部类：处理图片识别条码
    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8).build()
        )

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { isbn ->
                                onBarcodeDetected(isbn)
                            }
                        }
                    }
                    .addOnFailureListener { Log.e("ScanFragment", "扫描失败", it) }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}