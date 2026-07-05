package com.yugidex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yugidex.app.UiState
import com.yugidex.app.data.InventoryCard
import com.yugidex.app.ui.*

@Composable
fun InventoryScreen(cards: List<InventoryCard>, state: UiState, onDelete: (InventoryCard) -> Unit, onSort: () -> Unit, onAuth: (String, String, String?, Boolean) -> Unit, onSync: () -> Unit, onLogout: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var authOpen by remember { mutableStateOf(false) }
    val filtered = remember(cards, query) { cards.filter { it.name.contains(query, ignoreCase = true) } }
    Column(Modifier.fillMaxSize().background(DarkObsidian).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Meu Inventario", style = MaterialTheme.typography.headlineMedium, color = PharaohGold); Text("${cards.sumOf { it.quantity }} cartas", color = TextGray) }
            IconButton(onClick = onSort) { Icon(Icons.Rounded.SortByAlpha, "Alternar ordenacao") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Buscar") })
            if (state.token == null) FilledTonalIconButton(onClick = { authOpen = true }) { Icon(Icons.AutoMirrored.Rounded.Login, "Entrar") }
        }
        state.username?.let { username ->
            Text("Conectado como @$username", Modifier.padding(top = 8.dp), color = MysticGold)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                FilledTonalButton(onClick = onSync, enabled = !state.syncing) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sincronizar")
                }
                OutlinedButton(onClick = onLogout, enabled = !state.syncing) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sair da conta")
                }
            }
        }
        state.message?.let { Text(it, Modifier.padding(vertical = 8.dp), color = GoldGlow) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 0.dp, top = 14.dp, end = 0.dp, bottom = 90.dp)) {
            items(filtered, key = { it.cardId }) { card -> InventoryRow(card, { onDelete(card) }) }
        }
    }
    if (authOpen) AuthDialog(onDismiss = { authOpen = false }, onSubmit = { email, password, username, register -> onAuth(email, password, username, register); authOpen = false })
}

@Composable private fun InventoryRow(card: InventoryCard, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardViolet)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val artwork = card.imageUrl.takeUnless { it.isNullOrBlank() }
                ?: "https://images.ygoprodeck.com/images/cards/${card.cardId}.jpg"
            AsyncImage(artwork, card.name, Modifier.width(60.dp).aspectRatio(421f / 614f))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(card.rarity, card.attribute).joinToString(" • "), color = TextGray)
                card.collectionName?.let { Text("Colecao: $it", color = PharaohGold, style = MaterialTheme.typography.bodySmall) }
                Text("Quantidade: ${card.quantity}", color = MysticGold)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Excluir", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable private fun AuthDialog(onDismiss: () -> Unit, onSubmit: (String, String, String?, Boolean) -> Unit) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (register) "Registrar duelista" else "Entrar no portal") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (register) OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Senha (8+ caracteres)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            TextButton(onClick = { register = !register }) { Text(if (register) "Ja possuo uma conta" else "Criar uma nova conta") }
        }
    }, confirmButton = { Button(enabled = email.isNotBlank() && password.length >= 8 && (!register || username.length >= 3), onClick = { onSubmit(email, password, username.takeIf { register }, register) }) { Text(if (register) "Registrar" else "Entrar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
