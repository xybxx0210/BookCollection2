package com.example.bookcollection.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import com.example.bookcollection.R
import com.example.bookcollection.data.repository.BookLookupResult
import com.example.bookcollection.data.repository.BookRepository
import com.example.bookcollection.ui.BookDetailActivity
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * 扫码页：扫到书背面的 ISBN 条码 → 联网查书 → 打开详情页确认加入书架。
 */
class ScanFragment : Fragment(R.layout.fragment_scan) {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvResult: TextView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val repository by lazy { BookRepository() }

    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                .build()
        )
    }

    /** 防止条码每一帧都触发一次网络请求 */
    private var isLookingUp = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else if (isAdded) {
            tvResult.text = "未获得相机权限，无法扫码\n（点这里可以用测试 ISBN 体验）"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = view.findViewById(R.id.viewFinder)
        tvResult = view.findViewById(R.id.tvResult)

        // 没有相机时，点文字用《三体》的 ISBN 走一遍完整流程
        tvResult.setOnClickListener {
            lookupByIsbn("9787536692930")
        }

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
            if (!isAdded) return@addListener

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeBarcode(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "绑定相机失败", exc)
                tvResult.text = "相机启动失败：${exc.message}"
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeBarcode(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val isbn = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (isbn != null && !isLookingUp) {
                    activity?.runOnUiThread { lookupByIsbn(isbn) }
                }
            }
            .addOnFailureListener { Log.e(TAG, "条码识别失败", it) }
            .addOnCompleteListener { imageProxy.close() }
    }

    // ==================== 联网查书 ====================

    private fun lookupByIsbn(isbn: String) {
        if (isLookingUp) return

        isLookingUp = true
        tvResult.text = "已识别 ISBN：$isbn\n正在联网查询图书信息…"

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.lookupByIsbn(isbn)) {
                is BookLookupResult.Success -> {
                    val book = result.book
                    tvResult.text = "找到《${book.title}》，正在打开详情…"
                    startActivity(
                        Intent(requireContext(), BookDetailActivity::class.java).apply {
                            putExtra(BookDetailActivity.EXTRA_ISBN, book.isbn ?: isbn)
                            putExtra(BookDetailActivity.EXTRA_TITLE, book.title)
                            putExtra(BookDetailActivity.EXTRA_AUTHOR, book.author)
                            putExtra(BookDetailActivity.EXTRA_PUBLISHER, book.publisher)
                            putExtra(BookDetailActivity.EXTRA_COVER_URL, book.coverUrl)
                            putExtra(BookDetailActivity.EXTRA_TOTAL_PAGES, book.totalPages)
                        }
                    )
                }

                is BookLookupResult.NotFound -> {
                    isLookingUp = false
                    tvResult.text = "${result.message}\n换个角度再扫试试～"
                }

                is BookLookupResult.Failure -> {
                    isLookingUp = false
                    tvResult.text = "${result.message}\n请检查网络后重试"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从详情页返回后允许重新扫码
        isLookingUp = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 相机跟着 Fragment 生命周期解绑
        ProcessCameraProvider.getInstance(requireContext()).get().unbindAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }

    companion object {
        private const val TAG = "ScanFragment"
    }
}
