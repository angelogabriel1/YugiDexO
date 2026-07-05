package com.yugidex.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSharingTest {
    @Test
    fun buildsPublicProfileUrlWithoutDuplicateSlashOrAtSign() {
        assertEquals(
            "https://yugidex.example/colecao/duelista",
            publicProfileUrl("https://yugidex.example/", "@duelista")
        )
    }
}
