package com.yugidex.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yugidex.app.data.*
import com.yugidex.app.scanner.CardDetectionResult
import com.yugidex.app.scanner.CardDetectionType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID

data class UiState(
    val selected: Card? = null,
    val scanning: Boolean = false,
    val loadingDetails: Boolean = false,
    val message: String? = null,
    val token: String? = null,
    val username: String? = null,
    val syncing: Boolean = false,
    val deckSearchResults: List<Card> = emptyList(),
    val searchingDeckCards: Boolean = false,
    val scannerStatus: String = "Lendo carta..."
)

private fun Throwable.userMessage(action: String): String = when (this) {
    is IOException -> "Nao foi possivel conectar ao servidor para $action. Confirme que celular e computador estao no mesmo Wi-Fi e que o backend esta aberto."
    is HttpException -> runCatching {
        val raw = response()?.errorBody()?.string().orEmpty()
        com.google.gson.JsonParser.parseString(raw).asJsonObject.get("error")?.asString
    }.getOrNull() ?: "O servidor recusou a solicitacao para $action (HTTP ${code()})."
    else -> message ?: "Falha inesperada ao $action."
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class YugidexViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as YugidexApplication).container
    private val repository = container.repository
    private val dao = container.database.inventory()
    private val deckDao = container.database.decks()
    private val backend = container.backend
    private val prefs = application.getSharedPreferences("session", 0)
    private val sortByName = MutableStateFlow(false)
    private val _state = MutableStateFlow(UiState(token = prefs.getString("token", null), username = prefs.getString("username", null)))
    private var stableKey: String? = null
    private var stableCount = 0
    private var stableAt = 0L
    private var lastLookupKey: String? = null
    private var lastLookupAt = 0L
    val state = _state.asStateFlow()
    val inventory = sortByName.flatMapLatest { if (it) dao.observeByName() else dao.observeByDate() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val decks = deckDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            inventory.filter { it.isNotEmpty() }.first()
                .filter { it.estimatedUnitValue == null }
                .forEach { card ->
                    runCatching { repository.estimate(card) }.getOrNull()?.let { value ->
                        dao.save(card.copy(estimatedUnitValue = value))
                    }
                }
        }
    }

    fun identify(detection: CardDetectionResult?, requireStability: Boolean, onFound: () -> Unit) {
        if (detection == null) {
            if (!requireStability) _state.update { it.copy(scannerStatus = "Não consegui ler. Tente aproximar ou usar busca manual.") }
            return
        }

        val key = "${detection.type}:${detection.value.uppercase()}"
        val now = System.currentTimeMillis()
        val label = when (detection.type) {
            CardDetectionType.SET_CODE -> "Código detectado: ${detection.value}"
            CardDetectionType.PASSCODE -> "Passcode detectado: ${detection.value}"
            CardDetectionType.NAME -> "Nome detectado: ${detection.value}"
        }
        _state.update { it.copy(scannerStatus = label) }

        if (requireStability) {
            if (stableKey == key && now - stableAt <= STABILITY_WINDOW_MS) stableCount++ else {
                stableKey = key
                stableCount = 1
            }
            stableAt = now
            if (stableCount < REQUIRED_STABLE_FRAMES) return
        }
        if (_state.value.scanning || (lastLookupKey == key && now - lastLookupAt < LOOKUP_DEBOUNCE_MS)) return

        lastLookupKey = key
        lastLookupAt = now
        _state.update { it.copy(scanning = true, message = null, scannerStatus = "Buscando carta...") }
        viewModelScope.launch {
            runCatching { repository.identify(detection) }
                .onSuccess { card ->
                    _state.update { it.copy(selected = card, scanning = false, loadingDetails = true, scannerStatus = "Carta encontrada!") }
                    onFound()
                    runCatching { repository.details(card) }.onSuccess { details -> _state.update { it.copy(selected = details) } }
                    _state.update { it.copy(loadingDetails = false) }
                }
                .onFailure { error -> _state.update {
                    it.copy(
                        scanning = false,
                        scannerStatus = "Não consegui ler. Tente aproximar ou usar busca manual.",
                        message = if (error is IOException) error.userMessage("identificar a carta")
                            else "Nenhuma carta encontrada. Tente enquadrar somente a carta ou use a busca pelo nome."
                    )
                } }
        }
    }

    fun saveSelected(collectionName: String?) = viewModelScope.launch {
        _state.value.selected?.let {
            repository.save(it, collectionName)
            _state.update { state -> state.copy(message = "Carta adicionada ao inventario") }
        }
    }
    fun delete(card: InventoryCard) = viewModelScope.launch { dao.delete(card) }
    fun toggleSort() { sortByName.value = !sortByName.value }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun prepareDeckEditor() = _state.update {
        it.copy(deckSearchResults = emptyList(), searchingDeckCards = false, message = null)
    }

    fun searchDeckCards(query: String) {
        if (query.trim().length < 2) {
            _state.update { it.copy(deckSearchResults = emptyList(), message = "Digite ao menos 2 caracteres para buscar.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searchingDeckCards = true, message = null) }
            runCatching { repository.search(query.trim()).take(30) }
                .onSuccess { cards ->
                    _state.update {
                        it.copy(
                            deckSearchResults = cards,
                            searchingDeckCards = false,
                            message = if (cards.isEmpty()) "Nenhuma carta encontrada." else null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(searchingDeckCards = false, message = error.userMessage("buscar cartas")) }
                }
        }
    }

    fun saveDeck(deckId: String?, name: String, cards: List<DeckCardPayload>) = viewModelScope.launch {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _state.update { it.copy(message = "Dê um nome ao deck antes de salvar.") }
            return@launch
        }
        val id = deckId ?: UUID.randomUUID().toString()
        val existing = decks.value.firstOrNull { it.deck.id == id }?.deck
        val now = Instant.now().toString()
        deckDao.save(
            DeckEntity(id, cleanName.take(80), existing?.createdAt ?: now, now),
            normalizeDeckCards(cards, inventory.value.mapTo(mutableSetOf()) { it.cardId }).map { card ->
                DeckCardEntity(
                    deckId = id, cardId = card.cardId, name = card.name,
                    imageUrl = card.imageUrl, type = card.type, attribute = card.attribute,
                    rarity = card.rarity, status = card.status, quantity = card.quantity,
                    affiliateUrl = card.affiliate?.url,
                    affiliateLabel = card.affiliate?.label,
                    affiliateProvider = card.affiliate?.provider
                )
            }
        )
        _state.update { it.copy(message = "Deck salvo com sucesso.") }
        syncDecksIfAuthenticated("Deck salvo neste aparelho, mas ainda não foi sincronizado.")
    }

    fun deleteDeck(deckId: String) = viewModelScope.launch {
        deckDao.deleteDeck(deckId)
        _state.update { it.copy(message = "Deck excluído.") }
        syncDecksIfAuthenticated("Deck excluído neste aparelho, mas a nuvem ainda não foi atualizada.")
    }

    fun authenticate(email: String, password: String, username: String?, register: Boolean) = viewModelScope.launch {
        _state.update { it.copy(syncing = true, message = null) }
        runCatching {
            val auth = if (register) backend.register(Credentials(email, password, username)) else backend.login(Credentials(email, password))
            val loadedCards = if (auth.token != null) {
                val remote = backend.inventory("Bearer ${auth.token}").cards
                val remoteById = remote.associateBy { it.cardId }
                val localWithHostedImages = inventory.value.map { local ->
                    local.copy(
                        imageUrl = remoteById[local.cardId]?.imageUrl ?: local.imageUrl,
                        estimatedUnitValue = local.estimatedUnitValue ?: remoteById[local.cardId]?.estimatedUnitValue
                    )
                }
                val merged = (remote + localWithHostedImages).associateBy { it.cardId }.values.toList()
                if (merged.isNotEmpty()) dao.saveAll(merged)
                val remoteDecks = runCatching { backend.decks("Bearer ${auth.token}").decks }.getOrDefault(emptyList())
                val localDecks = deckDao.getAll().map(DeckWithCards::toPayload)
                val mergedDecks = (remoteDecks + localDecks).associateBy { it.id }.values
                mergedDecks.forEach { deckDao.savePayload(it) }
                runCatching { backend.syncDecks("Bearer ${auth.token}", DeckSyncBody(mergedDecks.toList())) }
                remote.size
            } else 0
            auth to loadedCards
        }.onSuccess { (auth, loadedCards) ->
            if (auth.token == null) _state.update { it.copy(syncing = false, message = "Confirme seu email antes de entrar") }
            else {
                prefs.edit()
                    .putString("token", auth.token)
                    .putString("refresh_token", auth.refreshToken)
                    .putString("username", auth.username)
                    .apply()
                val message = if (loadedCards > 0) "Duelista autenticado. $loadedCards cartas recuperadas" else "Duelista autenticado"
                _state.update { it.copy(token = auth.token, username = auth.username, syncing = false, message = message) }
            }
        }.onFailure { error -> _state.update { state -> state.copy(syncing = false, message = error.userMessage("autenticar")) } }
    }

    fun sync() = viewModelScope.launch {
        val token = _state.value.token ?: return@launch
        _state.update { it.copy(syncing = true, message = null) }
        runCatching {
            val result = backend.sync("Bearer $token", SyncBody(inventory.value))
            val deckResult = backend.syncDecks("Bearer $token", DeckSyncBody(deckDao.getAll().map(DeckWithCards::toPayload)))
            result to deckResult
        }
            .onSuccess { (result, deckResult) ->
                _state.update { it.copy(syncing = false, message = "${result.synced} cartas e ${deckResult.synced} decks sincronizados") }
            }
            .onFailure { error ->
                if (error is HttpException && error.code() == 401) {
                    clearAuthSession()
                    _state.update { it.copy(token = null, username = null, syncing = false, message = "Sua sessao antiga expirou. Entre novamente para usar o Neon.") }
                } else {
                    _state.update { it.copy(syncing = false, message = error.userMessage("sincronizar o inventario")) }
                }
            }
    }

    fun logout() = viewModelScope.launch {
        val token = _state.value.token ?: return@launch
        // Local logout is immediate and remains effective even if the backend is offline.
        clearAuthSession()
        _state.update {
            it.copy(
                token = null,
                username = null,
                syncing = false,
                message = "Você saiu da conta."
            )
        }
        runCatching { backend.logout("Bearer $token") }.onFailure {
            _state.update { state -> state.copy(message = "Você saiu da conta localmente; a sessão remota não pôde ser encerrada.") }
        }
    }

    /** Removes authentication only. The Room inventory is deliberately untouched. */
    fun clearAuthSession() {
        prefs.edit().remove("token").remove("refresh_token").remove("username").apply()
    }

    private suspend fun syncDecksIfAuthenticated(failureMessage: String) {
        val token = _state.value.token ?: return
        runCatching {
            backend.syncDecks("Bearer $token", DeckSyncBody(deckDao.getAll().map(DeckWithCards::toPayload)))
        }.onFailure { _state.update { state -> state.copy(message = failureMessage) } }
    }

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 2
        const val STABILITY_WINDOW_MS = 2_500L
        const val LOOKUP_DEBOUNCE_MS = 4_000L
    }
}

private fun DeckWithCards.toPayload() = DeckPayload(
    id = deck.id,
    name = deck.name,
    createdAt = deck.createdAt,
    updatedAt = deck.updatedAt,
    cards = cards.map { card ->
        DeckCardPayload(
            cardId = card.cardId, name = card.name, imageUrl = card.imageUrl,
            type = card.type, attribute = card.attribute, rarity = card.rarity,
            status = card.status, quantity = card.quantity,
            affiliate = card.affiliateUrl?.let {
                AffiliateLink(it, card.affiliateLabel ?: "Ver oferta da carta", card.affiliateProvider)
            }
        )
    }
)

private suspend fun DeckDao.savePayload(payload: DeckPayload) {
    val now = Instant.now().toString()
    save(
        DeckEntity(
            id = payload.id,
            name = payload.name,
            createdAt = payload.createdAt ?: now,
            updatedAt = payload.updatedAt ?: now
        ),
        payload.cards.map { card ->
            DeckCardEntity(
                deckId = payload.id, cardId = card.cardId, name = card.name,
                imageUrl = card.imageUrl, type = card.type, attribute = card.attribute,
                rarity = card.rarity, status = card.status, quantity = card.quantity,
                affiliateUrl = card.affiliate?.url,
                affiliateLabel = card.affiliate?.label,
                affiliateProvider = card.affiliate?.provider
            )
        }
    )
}
