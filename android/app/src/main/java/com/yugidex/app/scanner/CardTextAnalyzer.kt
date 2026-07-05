package com.yugidex.app.scanner

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class CardTextAnalyzer(
    private val onDetection: (CardDetectionResult?, Boolean) -> Unit
) : ImageAnalysis.Analyzer, Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)
    private val manualRequested = AtomicBoolean(false)
    @Volatile private var lastRun = 0L

    fun forceScan() {
        manualRequested.set(true)
        lastRun = 0L
    }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val now = System.currentTimeMillis()
        val manual = manualRequested.get()
        if (mediaImage == null || (!manual && now - lastRun < LIVE_SCAN_INTERVAL_MS) || !busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        if (manual) manualRequested.set(false)
        lastRun = now
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        try {
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            recognizer.process(input)
                .addOnSuccessListener { recognized ->
                    val regions = extractOcrRegions(recognized, rotatedWidth, rotatedHeight)
                    val detection = CardOcrProcessor.processOcrText(regions)
                    Log.d(TAG, "OCR bruto:\n${regions.fullText}")
                    Log.d(TAG, "OCR interpretado: ${detection?.type ?: "NENHUM"} ${detection?.value.orEmpty()} " +
                        "(confianca=${detection?.confidence ?: 0f}, nome=${detection?.nameCandidate.orEmpty()})")
                    onDetection(detection, manual)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Falha no reconhecimento OCR", error)
                    if (manual) onDetection(null, true)
                }
                .addOnCompleteListener {
                    busy.set(false)
                    imageProxy.close()
                }
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao preparar o frame para OCR", error)
            busy.set(false)
            imageProxy.close()
            if (manual) onDetection(null, true)
        }
    }

    override fun close() = recognizer.close()

    private companion object {
        const val TAG = "YugidexOCR"
        const val LIVE_SCAN_INTERVAL_MS = 550L
    }
}
