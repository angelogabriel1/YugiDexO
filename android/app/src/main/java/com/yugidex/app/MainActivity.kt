package com.yugidex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.yugidex.app.ui.YugidexTheme
import com.yugidex.app.ui.screens.*
import kotlinx.serialization.Serializable

@Serializable data object ScannerKey : NavKey
@Serializable data object DetailsKey : NavKey
@Serializable data object InventoryKey : NavKey
@Serializable data object DecksKey : NavKey
@Serializable data class DeckEditorKey(val deckId: String? = null) : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YugidexTheme { YugidexRoot() } }
    }
}

@Composable
private fun YugidexRoot(vm: YugidexViewModel = viewModel()) {
    val backStack = rememberNavBackStack(ScannerKey)
    val state by vm.state.collectAsStateWithLifecycle()
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current = backStack.lastOrNull()
    Scaffold(
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) },
        bottomBar = {
            if (current !is DetailsKey && current !is DeckEditorKey) NavigationBar {
                NavigationBarItem(selected = current is ScannerKey, onClick = { backStack.clear(); backStack.add(ScannerKey) }, icon = { Icon(Icons.Rounded.PhotoCamera, null) }, label = { Text("Scanner") })
                NavigationBarItem(selected = current is InventoryKey, onClick = { backStack.clear(); backStack.add(InventoryKey) }, icon = { Icon(Icons.Rounded.Inventory2, null) }, label = { Text("Inventario") })
                NavigationBarItem(selected = current is DecksKey, onClick = { backStack.clear(); backStack.add(DecksKey) }, icon = { Icon(Icons.Rounded.Style, null) }, label = { Text("Decks") })
            }
        }
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding),
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<ScannerKey> { ScannerScreen(state, onDetection = { detection, requireStability -> vm.identify(detection, requireStability) { backStack.add(DetailsKey) } }) }
                entry<DetailsKey> { CardDetailsScreen(state.selected, state.loadingDetails, onBack = { backStack.removeLastOrNull() }, onSave = vm::saveSelected) }
                entry<InventoryKey> { InventoryScreen(inventory, state, onDelete = vm::delete, onSort = vm::toggleSort, onAuth = vm::authenticate, onSync = vm::sync, onLogout = vm::logout, onShare = { shareProfile(context, it) }) }
                entry<DecksKey> {
                    DecksScreen(
                        decks = decks,
                        state = state,
                        onCreate = { vm.prepareDeckEditor(); backStack.add(DeckEditorKey()) },
                        onEdit = { vm.prepareDeckEditor(); backStack.add(DeckEditorKey(it)) },
                        onDelete = vm::deleteDeck
                    )
                }
                entry<DeckEditorKey> { key ->
                    DeckEditorScreen(
                        deck = decks.firstOrNull { it.deck.id == key.deckId },
                        inventory = inventory,
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onSearchMissing = vm::searchDeckCards,
                        onSave = vm::saveDeck
                    )
                }
            }
        )
    }
}
