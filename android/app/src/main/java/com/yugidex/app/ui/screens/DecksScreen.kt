package com.yugidex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yugidex.app.UiState
import com.yugidex.app.data.*
import com.yugidex.app.ui.*

private const val OWNED = "owned"
private const val MISSING = "missing"

@Composable
fun DecksScreen(
    decks: List<DeckWithCards>,
    state: UiState,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<DeckWithCards?>(null) }
    Column(Modifier.fillMaxSize().background(DarkObsidian).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Meus Decks", style = MaterialTheme.typography.headlineMedium, color = PharaohGold)
                Text("Organize sua estratégia", color = TextGray)
            }
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Novo deck")
            }
        }
        state.message?.let { Text(it, Modifier.padding(bottom = 10.dp), color = GoldGlow) }
        if (decks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Style, null, Modifier.size(64.dp), tint = MysticGold)
                Text("Nenhum deck criado", style = MaterialTheme.typography.titleLarge, color = PharaohGold)
                Text("Crie um deck com cartas da sua coleção e marque as que ainda faltam.", color = TextGray)
                Button(onClick = onCreate, modifier = Modifier.padding(top = 16.dp)) { Text("Criar meu primeiro deck") }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(decks, key = { it.deck.id }) { deck ->
                    val owned = deck.cards.count { it.status == OWNED }
                    val missing = deck.cards.count { it.status == MISSING }
                    Card(
                        onClick = { onEdit(deck.deck.id) },
                        colors = CardDefaults.cardColors(containerColor = CardViolet)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Style, null, tint = PharaohGold)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(deck.deck.name, style = MaterialTheme.typography.titleLarge)
                                Text("${deck.cards.size} cartas", color = TextGray)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("$owned na coleção", color = MysticGold, style = MaterialTheme.typography.bodySmall)
                                    Text("$missing faltando", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { pendingDelete = deck }) {
                                Icon(Icons.Rounded.DeleteOutline, "Excluir deck", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir deck?") },
            text = { Text("O deck “${deck.deck.name}” será excluído. Suas cartas continuarão no inventário.") },
            confirmButton = {
                Button(onClick = { onDelete(deck.deck.id); pendingDelete = null }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun DeckEditorScreen(
    deck: DeckWithCards?,
    inventory: List<InventoryCard>,
    state: UiState,
    onBack: () -> Unit,
    onSearchMissing: (String) -> Unit,
    onSave: (String?, String, List<DeckCardPayload>) -> Unit
) {
    val deckId = deck?.deck?.id
    var name by remember(deckId) { mutableStateOf(deck?.deck?.name.orEmpty()) }
    var inventoryQuery by remember(deckId) { mutableStateOf("") }
    var missingQuery by remember(deckId) { mutableStateOf("") }
    val selected = remember(deckId) {
        mutableStateMapOf<Long, DeckCardPayload>().apply {
            deck?.cards?.forEach { card ->
                put(card.cardId, DeckCardPayload(card.cardId, card.name, card.imageUrl, card.type, card.attribute, card.rarity, card.status, card.quantity))
            }
        }
    }
    val ownedIds = remember(inventory) { inventory.mapTo(mutableSetOf()) { it.cardId } }
    val filteredInventory = remember(inventory, inventoryQuery) {
        inventory.filter { it.name.contains(inventoryQuery, ignoreCase = true) }
    }
    val missingResults = remember(state.deckSearchResults, ownedIds) {
        state.deckSearchResults.filterNot { it.id in ownedIds }
    }

    Column(Modifier.fillMaxSize().background(DarkObsidian)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
            Text(if (deck == null) "Novo deck" else "Editar deck", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = PharaohGold)
            Button(
                onClick = { onSave(deckId, name, selected.values.toList()); onBack() },
                enabled = name.isNotBlank()
            ) { Text("Salvar") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do deck") },
                    placeholder = { Text("Ex.: Meu Deck Principal") },
                    singleLine = true
                )
            }
            if (selected.isNotEmpty()) {
                item { SectionTitle("Cartas no deck", "${selected.size} selecionadas") }
                items(selected.values.toList(), key = { "selected-${it.cardId}" }) { card ->
                    DeckCardRow(card, selected = true, onToggle = { selected.remove(card.cardId) })
                }
            }
            item {
                SectionTitle("Da sua coleção", "Escolha as cartas que você já possui")
                OutlinedTextField(
                    value = inventoryQuery,
                    onValueChange = { inventoryQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Buscar no inventário") },
                    singleLine = true
                )
            }
            if (filteredInventory.isEmpty()) {
                item { Text("Nenhuma carta encontrada na sua coleção.", color = TextGray) }
            } else {
                items(filteredInventory, key = { "owned-${it.cardId}" }) { card ->
                    val payload = DeckCardPayload(card.cardId, card.name, card.imageUrl, card.type, card.attribute, card.rarity, OWNED)
                    DeckCardRow(payload, selected = selected[card.cardId]?.status == OWNED) {
                        if (selected[card.cardId]?.status == OWNED) selected.remove(card.cardId) else selected[card.cardId] = payload
                    }
                }
            }
            item {
                SectionTitle("Cartas que faltam", "Planeje cartas que ainda precisa adquirir")
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = missingQuery,
                        onValueChange = { missingQuery = it },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Rounded.TravelExplore, null) },
                        placeholder = { Text("Buscar carta pelo nome") },
                        singleLine = true
                    )
                    FilledTonalIconButton(
                        onClick = { onSearchMissing(missingQuery) },
                        enabled = !state.searchingDeckCards && missingQuery.trim().length >= 2
                    ) {
                        if (state.searchingDeckCards) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Search, "Buscar cartas")
                    }
                }
                Text("Resultados que já estão no inventário aparecem na seção acima.", color = TextGray, style = MaterialTheme.typography.bodySmall)
            }
            items(missingResults, key = { "missing-${it.id}" }) { card ->
                val payload = DeckCardPayload(
                    card.id, card.localized?.name ?: card.name, card.images.firstOrNull()?.url,
                    card.type, card.attribute, card.sets.firstOrNull()?.rarity, MISSING
                )
                DeckCardRow(payload, selected = selected[card.id]?.status == MISSING) {
                    if (selected[card.id]?.status == MISSING) selected.remove(card.id) else selected[card.id] = payload
                }
            }
            state.message?.let { message -> item { Text(message, color = GoldGlow) } }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = PharaohGold)
        Text(subtitle, color = TextGray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeckCardRow(card: DeckCardPayload, selected: Boolean, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardViolet)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artwork = card.imageUrl.takeUnless { it.isNullOrBlank() }
                ?: "https://images.ygoprodeck.com/images/cards/${card.cardId}.jpg"
            AsyncImage(artwork, card.name, Modifier.width(52.dp).aspectRatio(421f / 614f))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(card.rarity, card.attribute).joinToString(" • "), color = TextGray, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (card.status == OWNED) "Na Coleção" else "Faltando",
                    color = if (card.status == OWNED) MysticGold else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}
