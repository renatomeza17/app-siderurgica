package pe.edu.unmsm.siderurgica

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.*
import java.util.concurrent.TimeUnit

data class UiState(
    val session: ErpSession? = null, val firebaseEmail: String? = null,
    val quotes: List<Quote> = emptyList(), val orders: List<Order> = emptyList(),
    val sales: List<Sale> = emptyList(), val inventory: List<InventoryItem> = emptyList(),
    val drafts: List<Draft> = emptyList(), val loading: Boolean = false,
    val message: String = "", val offline: Boolean = false
)

class ErpViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ErpRepository(app)
    private val auth: FirebaseAuth? = if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val mutable = MutableStateFlow(UiState(session = repo.vault.read(), firebaseEmail = auth?.currentUser?.email))
    val state = mutable.asStateFlow()

    init {
        if (mutable.value.session != null) refresh()
        val work = PeriodicWorkRequestBuilder<CacheSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("erp-cache", ExistingPeriodicWorkPolicy.KEEP, work)
    }
    private fun run(action: suspend () -> Unit) = viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, message = "")
        try { action() } catch (e: Exception) {
            mutable.value = mutable.value.copy(message = e.message ?: "Operación fallida")
        } finally { mutable.value = mutable.value.copy(loading = false) }
    }
    fun loginErp(username: String, password: String) = run {
        val session = repo.login(username, password)
        mutable.value = mutable.value.copy(session = session, message = "Acceso ERP correcto")
        refreshData()
    }
    fun firebaseLogin(email: String, password: String, register: Boolean = false) = run {
        val firebase = auth ?: error("Agrega google-services.json para activar Firebase")
        if (register) firebase.createUserWithEmailAndPassword(email.trim(), password).await()
        else firebase.signInWithEmailAndPassword(email.trim(), password).await()
        mutable.value = mutable.value.copy(firebaseEmail = firebase.currentUser?.email,
            message = "Cuenta Firebase conectada. Vincula tu cuenta ERP para operar.")
    }
    fun resetPassword(email: String) = run {
        (auth ?: error("Firebase no está configurado")).sendPasswordResetEmail(email.trim()).await()
        mutable.value = mutable.value.copy(message = "Revisa tu correo para recuperar la contraseña")
    }
    fun logout() {
        repo.vault.clear(); auth?.signOut()
        mutable.value = UiState(message = "Sesión cerrada")
    }
    fun refresh() = run { refreshData() }
    private suspend fun refreshData() {
        repo.requireSales()
        val quotes = repo.quotes(); val orders = repo.orders(); val sales = repo.sales(); val inventory = repo.inventory()
        mutable.value = mutable.value.copy(quotes = quotes, orders = orders, sales = sales,
            inventory = inventory, drafts = repo.drafts(), message = "Datos cargados (red o copia local)")
    }
    fun loadCache() = run {
        mutable.value = mutable.value.copy(quotes = repo.quotes(false), orders = repo.orders(false),
            sales = repo.sales(false), inventory = repo.inventory(false), drafts = repo.drafts(),
            offline = true, message = "Mostrando datos guardados")
    }
    fun saveQuote(body: QuoteCreate, asDraft: Boolean = false) = run {
        repo.requireSales()
        require(body.cliente.isNotBlank() && body.producto.isNotBlank() && body.cantidad > 0 && body.precioUnitario > 0)
        if (asDraft) {
            repo.saveDraft("QUOTE", repo.gson().toJson(body))
            mutable.value = mutable.value.copy(drafts = repo.drafts(), message = "Borrador guardado en este dispositivo")
        } else {
            repo.createQuote(body)
            mutable.value = mutable.value.copy(quotes = repo.quotes(), message = "Cotización registrada")
        }
    }
    fun sendDraft(draft: Draft) = run {
        if (draft.kind == "QUOTE") repo.createQuote(repo.gson().fromJson(draft.json, QuoteCreate::class.java))
        else if (draft.kind == "ORDER") repo.createOrder(repo.gson().fromJson(draft.json, Order::class.java))
        else error("Borrador desconocido")
        repo.deleteDraft(draft.localId)
        mutable.value = mutable.value.copy(drafts = repo.drafts(), quotes = repo.quotes(),
            orders = repo.orders(), message = "Borrador enviado")
    }
    fun createOrder(order: Order, asDraft: Boolean = false) = run {
        repo.requireSales()
        require(order.cliente.isNotBlank() && order.producto.isNotBlank() && order.cantidad > 0 && order.precioUnitario > 0)
        if (asDraft) {
            repo.saveDraft("ORDER", repo.gson().toJson(order))
            mutable.value = mutable.value.copy(drafts = repo.drafts(), message = "Borrador guardado")
        } else {
            repo.createOrder(order)
            mutable.value = mutable.value.copy(orders = repo.orders(), message = "Pedido registrado")
        }
    }
    fun approveQuote(id: Long) = run {
        // Backend creates the order in the same transaction when quote is approved.
        repo.quoteState(id, "APROBADA")
        mutable.value = mutable.value.copy(quotes = repo.quotes(), orders = repo.orders(), message = "Pedido creado")
    }
    fun orderState(id: Long, status: String) = run {
        repo.orderState(id, status)
        mutable.value = mutable.value.copy(orders = repo.orders(), sales = repo.sales(), inventory = repo.inventory(), message = "Estado actualizado")
    }
    fun sale(id: Long) = run {
        repo.saleFromOrder(id)
        mutable.value = mutable.value.copy(orders = repo.orders(), sales = repo.sales(), message = "Venta registrada")
    }
    fun firebaseConfigured() = auth != null
}
