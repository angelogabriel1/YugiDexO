package com.yugidex.app

import android.content.Context
import android.content.Intent

fun publicProfileUrl(baseUrl: String, username: String): String =
    "${baseUrl.trimEnd('/')}/colecao/${username.trimStart('@')}"

fun shareProfile(context: Context, username: String) {
    val url = publicProfileUrl(BuildConfig.API_BASE_URL, username)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Minha colecao no Yugidex")
        putExtra(Intent.EXTRA_TEXT, "Veja minha colecao de Yu-Gi-Oh! no Yugidex: $url")
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar perfil"))
}
