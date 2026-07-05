package com.yugidex.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardOcrProcessorTest {
    @Test fun detectsCommonSetCodeFormats() {
        assertEquals("LOB-001", CardOcrProcessor.detectSetCode("LOB-001"))
        assertEquals("LOB-EN001", CardOcrProcessor.detectSetCode("LOB-EN001"))
        assertEquals("SDY-PT001", CardOcrProcessor.detectSetCode("SDYPT001"))
        assertEquals("MAGO-EN012", CardOcrProcessor.detectSetCode("MAGO - ENO12"))
        assertEquals("RA01-EN003", CardOcrProcessor.detectSetCode("RA01-EN003"))
    }

    @Test fun repairsSpacingAndCommonOcrConfusions() {
        assertEquals("LOB-001", CardOcrProcessor.detectSetCode("L O B - O O I"))
        assertEquals("46986414", CardOcrProcessor.detectPasscode("4698 6414"))
        assertEquals("89631139", CardOcrProcessor.detectPasscode("8963I139"))
    }

    @Test fun respectsDetectionPriority() {
        val result = CardOcrProcessor.processOcrText(
            OcrTextRegions(
                fullText = "DARK MAGICIAN\nLOB-EN005\n46986414",
                topText = "DARK MAGICIAN",
                middleLowerText = "LOB-EN005",
                bottomText = "46986414"
            )
        )
        assertEquals(CardDetectionType.SET_CODE, result?.type)
        assertEquals("LOB-EN005", result?.value)
    }

    @Test fun ignoresStatsAndFindsTopName() {
        assertNull(CardOcrProcessor.detectSetCode("ATK 2500 DEF 2100"))
        assertEquals("Mago Negro", CardOcrProcessor.detectCardName("Mago Negro\nATK 2500 / DEF 2100"))
    }

    @Test fun preservesNameWhenAFalseSetCodeWinsPriority() {
        val result = CardOcrProcessor.processOcrText(
            OcrTextRegions(
                fullText = "THOUSAND-EYES RESTRICT\nFU-510\nFusion Monster",
                topText = "THOUSAND-EYES RESTRICT",
                middleLowerText = "FU-510"
            )
        )
        assertEquals(CardDetectionType.SET_CODE, result?.type)
        assertEquals("FU-510", result?.value)
        assertEquals("THOUSAND-EYES RESTRICT", result?.nameCandidate)
    }
}
