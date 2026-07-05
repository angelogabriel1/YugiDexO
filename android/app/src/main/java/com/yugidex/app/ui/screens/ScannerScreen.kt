package com.yugidex.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Size
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yugidex.app.UiState
import com.yugidex.app.scanner.CardTextAnalyzer
import com.yugidex.app.scanner.CardDetectionResult
import com.yugidex.app.scanner.CardDetectionType
import com.yugidex.app.scanner.CardOcrProcessor
import com.yugidex.app.scanner.OcrTextRegions
import com.yugidex.app.scanner.extractOcrRegions
import com.yugidex.app.ui.*
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(state: UiState, onDetection: (CardDetectionResult?, Boolean) -> Unit) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var searchOpen by remember { mutableStateOf(false) }
    var manualScan by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    val latestOnDetection = rememberUpdatedState(onDetection)
    val cameraResult = remember {
        { detection: CardDetectionResult?, manual: Boolean ->
            if (manual) capturing = false
            latestOnDetection.value(detection, !manual)
        }
    }
    val galleryRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(galleryRecognizer) { onDispose { galleryRecognizer.close() } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { InputImage.fromFilePath(context, uri) }.onSuccess { image ->
            capturing = true
            galleryRecognizer.process(image)
                .addOnSuccessListener { recognized ->
                    val regions = extractOcrRegions(recognized, image.width, image.height)
                    val detection = CardOcrProcessor.processOcrText(regions)
                    Log.d("YugidexOCR", "OCR galeria bruto:\n${regions.fullText}")
                    Log.d("YugidexOCR", "OCR galeria interpretado: ${detection?.type ?: "NENHUM"} ${detection?.value.orEmpty()}")
                    latestOnDetection.value(detection, false)
                }
                .addOnFailureListener { error ->
                    Log.e("YugidexOCR", "Falha no OCR da galeria", error)
                    latestOnDetection.value(null, false)
                }
                .addOnCompleteListener { capturing = false }
        }.onFailure { latestOnDetection.value(null, false) }
    }
    LaunchedEffect(Unit) { if (!permission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(DarkObsidian)) {
        if (permission) CameraPreview(cameraResult, manualScan) else PermissionPanel { permissionLauncher.launch(Manifest.permission.CAMERA) }
        ScannerFrame(Modifier.align(Alignment.Center).fillMaxWidth(.78f).aspectRatio(421f / 614f))
        Column(Modifier.align(Alignment.TopCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("YUGIDEX", color = PharaohGold, style = MaterialTheme.typography.headlineMedium)
            Text(if (capturing) "Lendo carta..." else state.scannerStatus, color = if (state.scanning) GoldGlow else TextGray)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { capturing = true; manualScan++ },
                enabled = !capturing && !state.scanning,
                colors = ButtonDefaults.buttonColors(containerColor = PharaohGold),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Rounded.Visibility, null)
                Spacer(Modifier.width(10.dp))
                Text(if (capturing || state.scanning) "REVELANDO..." else "DESPERTAR O OLHO")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = { gallery.launch("image/*") }) { Icon(Icons.Rounded.PhotoLibrary, "Abrir galeria") }
                FilledTonalButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text("Busca arcana")
                }
            }
        }
        state.message?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 142.dp, start = 20.dp, end = 20.dp).background(CardViolet, RoundedCornerShape(12.dp)).padding(12.dp), color = GoldGlow) }
    }

    if (searchOpen) {
        var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { searchOpen = false }, title = { Text("Busca pelo nome") }, text = {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Nome da carta") })
        }, confirmButton = { TextButton(enabled = query.length >= 3, onClick = {
            searchOpen = false
            latestOnDetection.value(CardDetectionResult(CardDetectionType.NAME, query.trim(), 1f, query.trim()), false)
        }) { Text("Consultar") } }, dismissButton = { TextButton(onClick = { searchOpen = false }) { Text("Cancelar") } })
    }
}

@Composable private fun PermissionPanel(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.NoPhotography, null, Modifier.size(64.dp), tint = MysticGold)
        Spacer(Modifier.height(18.dp)); Text("A camera abre o portal de leitura", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp)); Button(onClick = onRequest) { Text("Permitir camera") }
    }
}

@Composable private fun CameraPreview(onDetection: (CardDetectionResult?, Boolean) -> Unit, manualScan: Int) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember(onDetection) { CardTextAnalyzer(onDetection) }
    val stillRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(ocrResolution(Size(1920, 1080)))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    LaunchedEffect(manualScan) {
        if (manualScan <= 0) return@LaunchedEffect
        if (!cameraReady) {
            analyzer.forceScan()
            return@LaunchedEffect
        }
        runCatching {
            imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val source = try { image.toBitmap() } finally { image.close() }
                        val cardBitmap = cropCardFromCapture(source, rotation)
                        recognizeHighResolutionCard(stillRecognizer, cardBitmap) { detection ->
                            onDetection(detection, true)
                        }
                    } catch (error: Exception) {
                        Log.e("YugidexOCR", "Falha ao preparar captura em alta resolução", error)
                        analyzer.forceScan()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("YugidexOCR", "Falha na captura em alta resolução", exception)
                    analyzer.forceScan()
                }
            })
        }.onFailure {
            Log.e("YugidexOCR", "Não foi possível iniciar a captura", it)
            analyzer.forceScan()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            analyzer.close()
            stillRecognizer.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { ctx ->
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ocrResolution(Size(1280, 720)))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
                cameraReady = true
            }, ContextCompat.getMainExecutor(context))
        }
    }, modifier = Modifier.fillMaxSize())
}

private fun ocrResolution(size: Size) = ResolutionSelector.Builder()
    .setResolutionStrategy(
        ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
    )
    .build()

private fun cropCardFromCapture(source: Bitmap, rotationDegrees: Int): Bitmap {
    val oriented = if (rotationDegrees == 0) source else Bitmap.createBitmap(
        source, 0, 0, source.width, source.height,
        Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true
    ).also { source.recycle() }

    val cropHeight = (oriented.height * .88f).toInt().coerceAtLeast(1)
    val cropWidth = minOf((oriented.width * .82f).toInt(), (cropHeight * 421f / 614f).toInt()).coerceAtLeast(1)
    val left = ((oriented.width - cropWidth) / 2).coerceAtLeast(0)
    val top = ((oriented.height - cropHeight) / 2).coerceAtLeast(0)
    val cropped = Bitmap.createBitmap(oriented, left, top, cropWidth, cropHeight)
    if (cropped !== oriented) oriented.recycle()

    if (cropped.height <= 2000) return cropped
    val scaled = Bitmap.createScaledBitmap(cropped, (cropped.width * 2000f / cropped.height).toInt(), 2000, true)
    cropped.recycle()
    return scaled
}

private fun recognizeHighResolutionCard(
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    card: Bitmap,
    onComplete: (CardDetectionResult?) -> Unit
) {
    val top = enhancedBand(card, 0f, .30f)
    val middle = enhancedBand(card, .38f, .84f)
    val bottom = enhancedBand(card, .68f, 1f)
    val fullTask = recognizer.process(InputImage.fromBitmap(card, 0))
    val topTask = recognizer.process(InputImage.fromBitmap(top, 0))
    val middleTask = recognizer.process(InputImage.fromBitmap(middle, 0))
    val bottomTask = recognizer.process(InputImage.fromBitmap(bottom, 0))

    com.google.android.gms.tasks.Tasks.whenAllComplete(fullTask, topTask, middleTask, bottomTask)
        .addOnCompleteListener {
            fun textOf(task: com.google.android.gms.tasks.Task<Text>) = if (task.isSuccessful) task.result.text else ""
            val fullText = textOf(fullTask)
            val baseRegions = if (fullTask.isSuccessful) {
                extractOcrRegions(fullTask.result, card.width, card.height, cardAlreadyCropped = true)
            } else OcrTextRegions(fullText)
            val regions = OcrTextRegions(
                fullText = listOf(fullText, textOf(topTask), textOf(middleTask), textOf(bottomTask)).filter { it.isNotBlank() }.joinToString("\n"),
                topText = listOf(baseRegions.topText, textOf(topTask)).filter { it.isNotBlank() }.joinToString("\n"),
                middleLowerText = listOf(baseRegions.middleLowerText, textOf(middleTask)).filter { it.isNotBlank() }.joinToString("\n"),
                bottomText = listOf(baseRegions.bottomText, textOf(bottomTask)).filter { it.isNotBlank() }.joinToString("\n")
            )
            val detection = CardOcrProcessor.processOcrText(regions)
            Log.d("YugidexOCR", "OCR alta resolução bruto:\n${regions.fullText}")
            Log.d("YugidexOCR", "OCR alta resolução interpretado: ${detection?.type ?: "NENHUM"} ${detection?.value.orEmpty()}, nome=${detection?.nameCandidate.orEmpty()}")
            card.recycle(); top.recycle(); middle.recycle(); bottom.recycle()
            onComplete(detection)
        }
}

private fun enhancedBand(card: Bitmap, from: Float, to: Float): Bitmap {
    val start = (card.height * from).toInt().coerceIn(0, card.height - 1)
    val height = (card.height * (to - from)).toInt().coerceIn(1, card.height - start)
    val band = Bitmap.createBitmap(card, 0, start, card.width, height)
    val targetWidth = minOf(1800, maxOf(band.width, 1200))
    val scaled = if (targetWidth == band.width) band else Bitmap.createScaledBitmap(
        band, targetWidth, (band.height * targetWidth.toFloat() / band.width).toInt(), true
    ).also { band.recycle() }
    val output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
    val grayscale = ColorMatrix().apply { setSaturation(0f) }
    val contrast = 1.35f
    grayscale.postConcat(ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, -28f,
        0f, contrast, 0f, 0f, -28f,
        0f, 0f, contrast, 0f, -28f,
        0f, 0f, 0f, 1f, 0f
    )))
    AndroidCanvas(output).drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(grayscale)
    })
    scaled.recycle()
    return output
}

@Composable private fun ScannerFrame(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 5.dp.toPx(); val corner = 42.dp.toPx(); val ornament = 14.dp.toPx()
        fun line(a: Offset, b: Offset) = drawLine(PharaohGold, a, b, stroke)
        line(Offset(0f, corner), Offset(0f, 0f)); line(Offset(0f, 0f), Offset(corner, 0f))
        line(Offset(size.width - corner, 0f), Offset(size.width, 0f)); line(Offset(size.width, 0f), Offset(size.width, corner))
        line(Offset(0f, size.height - corner), Offset(0f, size.height)); line(Offset(0f, size.height), Offset(corner, size.height))
        line(Offset(size.width - corner, size.height), Offset(size.width, size.height)); line(Offset(size.width, size.height), Offset(size.width, size.height - corner))
        listOf(Offset(0f,0f), Offset(size.width,0f), Offset(0f,size.height), Offset(size.width,size.height)).forEach { center -> drawCircle(GoldGlow, ornament, center, alpha = .7f) }
    }
}
