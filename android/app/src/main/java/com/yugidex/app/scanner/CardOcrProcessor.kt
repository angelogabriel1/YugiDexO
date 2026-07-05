package com.yugidex.app.scanner

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.text.Normalizer
import java.util.Locale

enum class CardDetectionType { SET_CODE, PASSCODE, NAME }

data class CardDetectionResult(
    val type: CardDetectionType,
    val value: String,
    val confidence: Float,
    val rawText: String,
    val passcodeCandidate: String? = null,
    val nameCandidate: String? = null
)

data class OcrTextRegions(
    val fullText: String,
    val topText: String = "",
    val middleLowerText: String = "",
    val bottomText: String = ""
)

/** Pure OCR interpretation kept separate from CameraX and the network layer. */
object CardOcrProcessor {
    private val exactSetCode = Regex(
        "\\b([A-Z0-9](?: ?[A-Z0-9]){1,5})\\s*[-:;]\\s*([A-Z]{2})?\\s*([0-9OILSB](?: ?[0-9OILSB]){2,3})\\b"
    )
    private val compactSetCandidate = Regex("[A-Z0-9]{2,8}[0-9OILSB]{3,4}")
    private val passcodeCandidate = Regex("(?<![A-Z0-9])(?:[0-9OILSB][ -]?){8}(?![A-Z0-9])")
    private val knownLanguages = setOf("EN", "PT", "BR", "FR", "DE", "ES", "IT", "JP", "KR", "AE", "SP")
    private val rejectedNameWords = Regex(
        "\\b(ATK|DEF|LEVEL|NIVEL|1ST EDITION|LIMITED EDITION|SPELL|TRAP|MAGIA|ARMADILHA|MONSTER|MONSTRO|KONAMI|ILLUS|SERIAL|PASSCODE)\\b"
    )

    fun processOcrText(regions: OcrTextRegions): CardDetectionResult? {
        val raw = normalizeText(regions.fullText)
        if (raw.isBlank()) return null

        val name = detectCardName(regions.topText).orElse { detectCardName(regions.fullText) }
        val passcodeSource = listOf(regions.bottomText, regions.middleLowerText, regions.fullText)
            .firstNotNullOfOrNull { source -> detectPasscode(source)?.let { it to source } }
        val codeSource = listOf(regions.middleLowerText, regions.bottomText, regions.fullText)
            .firstNotNullOfOrNull { source -> detectSetCode(source)?.let { it to source } }
        if (codeSource != null) {
            val (code, source) = codeSource
            val confidence = if (normalizeText(source).contains(code)) .98f else .92f
            return CardDetectionResult(
                type = CardDetectionType.SET_CODE,
                value = code,
                confidence = confidence,
                rawText = raw,
                passcodeCandidate = passcodeSource?.first,
                nameCandidate = name
            )
        }

        if (passcodeSource != null) {
            val (passcode, source) = passcodeSource
            val confidence = if (Regex("(?<!\\d)$passcode(?!\\d)").containsMatchIn(normalizeText(source))) .99f else .93f
            return CardDetectionResult(
                type = CardDetectionType.PASSCODE,
                value = passcode,
                confidence = confidence,
                rawText = raw,
                nameCandidate = name
            )
        }

        name ?: return null
        return CardDetectionResult(CardDetectionType.NAME, name, if (regions.topText.isNotBlank()) .82f else .68f, raw)
    }

    fun processOcrText(rawText: String): CardDetectionResult? = processOcrText(OcrTextRegions(rawText))

    fun normalizeText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        .replace(Regex("[‐‑‒–—−]"), "-")
        .replace(Regex("[^\\p{L}\\p{N}\\-'/&:.,()\\n ]"), " ")
        .lineSequence()
        .map { it.trim().replace(Regex("[ \\t]+"), " ") }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    fun detectSetCode(text: String): String? {
        val normalized = normalizeText(text).uppercase(Locale.ROOT)
        exactSetCode.findAll(normalized).forEach { match ->
            val prefix = match.groupValues[1].replace(" ", "")
            val language = match.groupValues[2]
            val number = normalizeDigits(match.groupValues[3].replace(" ", ""))
            if (prefix.count(Char::isLetter) >= 2 && number.length in 3..4) return "$prefix-${language}$number"
        }

        normalized.lineSequence().flatMap { line -> compactSetCandidate.findAll(line.replace(" ", "")) }.forEach { match ->
            val compact = match.value
            for (digitsLength in listOf(3, 4)) {
                if (compact.length <= digitsLength + 1) continue
                val rawNumber = compact.takeLast(digitsLength)
                if (!rawNumber.all { it in "0123456789OILSB" }) continue
                var prefixAndLanguage = compact.dropLast(digitsLength)
                val language = prefixAndLanguage.takeLast(2).takeIf { it in knownLanguages }.orEmpty()
                if (language.isNotEmpty()) prefixAndLanguage = prefixAndLanguage.dropLast(2)
                val looksLikeStats = listOf("ATK", "DEF", "LEVEL", "LP").any(prefixAndLanguage::startsWith)
                if (prefixAndLanguage.length in 2..6 && prefixAndLanguage.count(Char::isLetter) >= 2 && !looksLikeStats) {
                    return "$prefixAndLanguage-${language}${normalizeDigits(rawNumber)}"
                }
            }
        }
        return null
    }

    fun detectPasscode(text: String): String? {
        val normalized = normalizeText(text).uppercase(Locale.ROOT)
        return passcodeCandidate.findAll(normalized)
            .map { it.value.filter(Char::isLetterOrDigit) }
            .filter { candidate -> candidate.count(Char::isDigit) >= 6 }
            .map(::normalizeDigits)
            .firstOrNull { it.length == 8 && it != "00000000" }
    }

    fun detectCardName(text: String): String? = normalizeText(text).lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.length in 3..60 &&
                line.count(Char::isLetter) >= 3 &&
                !rejectedNameWords.containsMatchIn(line.uppercase(Locale.ROOT)) &&
                detectSetCode(line) == null &&
                detectPasscode(line) == null &&
                !Regex("^[\\d /+.-]+$").matches(line)
        }
        .maxByOrNull { line ->
            val words = line.split(' ').count { it.length > 1 }
            (words.coerceAtMost(7) * 10) - kotlin.math.abs(line.length - 24)
        }
        ?.replace(Regex("^[^\\p{L}]+|[^\\p{L}0-9)'&-]+$"), "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun normalizeDigits(value: String): String = value.uppercase(Locale.ROOT).map { char ->
        when (char) {
            'O' -> '0'
            'I', 'L' -> '1'
            'S' -> '5'
            'B' -> '8'
            else -> char
        }
    }.joinToString("")

    private inline fun <T> T?.orElse(block: () -> T?): T? = this ?: block()
}

fun extractOcrRegions(text: Text, imageWidth: Int, imageHeight: Int, cardAlreadyCropped: Boolean = false): OcrTextRegions {
    val lines = text.textBlocks.flatMap { it.lines }
        .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
    val full = lines.joinToString("\n") { line ->
        // Touch every element deliberately: element-level OCR often preserves codes split into pieces.
        line.elements.joinToString(" ") { it.text }.ifBlank { line.text }
    }
    if (imageWidth <= 0 || imageHeight <= 0) return OcrTextRegions(full)

    val card = if (cardAlreadyCropped) Rect(0, 0, imageWidth, imageHeight) else Rect(
        (imageWidth * .10f).toInt(), (imageHeight * .13f).toInt(),
        (imageWidth * .90f).toInt(), (imageHeight * .87f).toInt()
    )
    val cardHeight = card.height().coerceAtLeast(1)
    fun inBand(box: Rect?, from: Float, to: Float): Boolean {
        box ?: return false
        val centerX = box.centerX()
        val relativeY = (box.centerY() - card.top).toFloat() / cardHeight
        return centerX in card.left..card.right && relativeY in from..to
    }
    fun band(from: Float, to: Float) = lines.filter { inBand(it.boundingBox, from, to) }
        .joinToString("\n") { it.elements.joinToString(" ") { element -> element.text }.ifBlank { it.text } }

    return OcrTextRegions(
        fullText = full,
        topText = band(0f, .30f),
        middleLowerText = band(.42f, .82f),
        bottomText = band(.70f, 1f)
    )
}
