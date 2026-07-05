package com.yugidex.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yugidex.app.data.Card
import com.yugidex.app.data.CardSet
import com.yugidex.app.ui.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CardDetailsScreen(
    card: Card?,
    loading: Boolean,
    onBack: () -> Unit,
    onSave: (String?) -> Unit
) {
    if (card == null) return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    val name = card.localized?.name ?: card.name
    val kind = remember(name) { legendaryKind(name) }
    var portuguese by remember(card.id) { mutableStateOf(card.localized != null) }
    var summoning by remember(card.id) { mutableStateOf(kind != null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var collectionName by remember(card.id) { mutableStateOf<String?>(null) }
    var added by remember(card.id) { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SpellPurple, DarkObsidian)))) {
        kind?.let { LegendaryAmbient(it) }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 112.dp)) {
            Box {
                AsyncImage(
                    card.images.firstOrNull()?.url ?: "https://images.ygoprodeck.com/images/cards/${card.id}.jpg",
                    name,
                    Modifier.fillMaxWidth().aspectRatio(421f / 520f),
                    alignment = Alignment.TopCenter
                )
                IconButton(onClick = onBack, Modifier.padding(12.dp).background(DarkObsidian.copy(alpha = .72f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar")
                }
            }
            Column(Modifier.padding(20.dp)) {
                Text(name, style = MaterialTheme.typography.headlineLarge, color = PharaohGold, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(card.type, card.race, card.attribute).joinToString(" • "), color = TextGray)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.level?.let { AssistChip({}, { Text("Nivel $it") }) }
                    card.atk?.let { AssistChip({}, { Text("ATK $it") }) }
                    card.def?.let { AssistChip({}, { Text("DEF $it") }) }
                }
                if (card.localized != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EN", color = TextGray); Switch(portuguese, { portuguese = it }); Text("PT", color = TextGray)
                }
                Text(
                    if (portuguese) card.localized?.description ?: card.desc.orEmpty() else card.desc.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(24.dp))
                Text("Colecao da carta", style = MaterialTheme.typography.titleLarge, color = MysticGold)
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { pickerOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    color = CardViolet,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (collectionName == null) TextGray.copy(alpha = .35f) else PharaohGold)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CollectionsBookmark, null, tint = PharaohGold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(collectionName ?: "Escolher colecao ou edicao")
                            Text("Toque para alterar", color = TextGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Edicoes e cotacoes", style = MaterialTheme.typography.titleLarge, color = MysticGold)
                card.prices?.editions?.forEach { price -> PriceRow(price.edition, price.price) }
                    ?: card.sets.take(8).forEach { set -> PriceRow(set.name, null, set.rarity) }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (collectionName == null) pickerOpen = true
                else if (!added) { onSave(collectionName); added = true }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = if (added) MysticGold else PharaohGold,
            icon = { Icon(if (added) Icons.Rounded.CheckCircle else Icons.Rounded.BookmarkAdd, null) },
            text = {
                AnimatedContent(added, label = "inventory-button") { saved ->
                    Text(if (saved) "ADICIONADA AO INVENTARIO" else if (collectionName == null) "ESCOLHER COLECAO" else "ADICIONAR AO INVENTARIO")
                }
            }
        )
        AnimatedVisibility(added, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp)) {
            Surface(color = CardViolet, shape = RoundedCornerShape(30.dp), shadowElevation = 8.dp) {
                Text("Carta selada em “$collectionName”", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = GoldGlow)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        if (summoning && kind != null) LegendarySummoning(card, kind) { summoning = false }
    }

    if (pickerOpen) CollectionPicker(
        sets = card.sets,
        selected = collectionName,
        onDismiss = { pickerOpen = false },
        onSelected = { collectionName = it; added = false; pickerOpen = false }
    )
}

@Composable
private fun CollectionPicker(sets: List<CardSet>, selected: String?, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val editions = remember(sets) { sets.distinctBy { it.code }.sortedBy { it.name } }
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onde esta carta pertence?") },
        text = {
            Column {
                Text("Escolha uma edicao oficial ou crie sua propria colecao.", color = TextGray)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(editions, key = { it.code }) { set ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelected("${set.name} • ${set.code}") }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected == "${set.name} • ${set.code}", onClick = { onSelected("${set.name} • ${set.code}") })
                            Column { Text(set.name); Text(set.code, color = TextGray, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                OutlinedTextField(
                    custom, { custom = it }, Modifier.fillMaxWidth(),
                    label = { Text("Colecao personalizada") },
                    placeholder = { Text("Ex.: Fichario Lendario") },
                    singleLine = true
                )
            }
        },
        confirmButton = { Button(onClick = { onSelected(custom.trim()) }, enabled = custom.isNotBlank()) { Text("Usar esta colecao") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable private fun PriceRow(label: String, price: Double?, note: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).background(CardViolet, RoundedCornerShape(10.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) { Text(label); note?.let { Text(it, color = TextGray, style = MaterialTheme.typography.bodySmall) } }
        price?.let { Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(it), color = PharaohGold, fontWeight = FontWeight.Bold) }
    }
}
