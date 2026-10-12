package pe.edu.unmsm.siderurgica.data

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import okhttp3.OkHttpClient
import pe.edu.unmsm.siderurgica.BuildConfig
import pe.edu.unmsm.siderurgica.SiderurgicaApp
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import pe.edu.unmsm.siderurgica.domain.BusinessRules

data class ErpSession(val user: ErpUser, val token: String)

/** AES-GCM key is non-exportable in Android Keystore. Never store ERP passwords. */
class SessionVault(context: Context) {
    private val prefs = context.getSharedPreferences("erp_session", Context.MODE_PRIVATE)
    private val alias = "erp-session-v1"
    private val gson = Gson()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(session: ErpSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(gson.toJson(session).toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("data", Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)).apply()
    }
    fun read(): ErpSession? {
        return try {
            val value = prefs.getString("data", null) ?: return null
            val all = Base64.decode(value, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
            }
            gson.fromJson(String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8), ErpSession::class.java)
        } catch (_: Exception) { clear(); null }
    }
    fun clear() { prefs.edit().clear().apply() }
}

class ErpRepository(private val context: Context) {
    var usedCache = false
        private set
    fun resetCacheStatus() { usedCache = false }
    private val gson = Gson()
    val vault = SessionVault(context)
    private val dao = (context.applicationContext as SiderurgicaApp).db.dao()
    private val api: ErpApi = Retrofit.Builder().baseUrl(BuildConfig.ERP_API_URL)
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(75, java.util.concurrent.TimeUnit.SECONDS).addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            vault.read()?.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }.build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ErpApi::class.java)

    suspend fun login(username: String, password: String): ErpSession {
        val result = api.login(LoginBody(username.trim(), password))
        require(result.success && result.usuario != null && !result.accessToken.isNullOrBlank()) {
            result.mensaje ?: "No se pudo acceder al ERP"
        }
        return ErpSession(result.usuario, result.accessToken).also(vault::save)
    }
    fun requireSales(): ErpSession {
        val s = vault.read() ?: error("Vincula una cuenta del ERP")
        require(BusinessRules.canSell(s.user.rol)) { "Tu rol no permite operar ventas" }
        return s
    }
    fun requireSession(): ErpSession = vault.read() ?: error("Inicia sesión en el ERP")
    fun requireWarehouse(): ErpSession = requireSession().also { require(BusinessRules.canWarehouse(it.user.rol)) { "Esta operación requiere rol ALMACEN o ADMIN." } }
    fun requireInventory(): ErpSession = requireSession().also { require(BusinessRules.canInventory(it.user.rol)) { "Tu rol no permite consultar inventario." } }
    private fun owner() = vault.read()?.user?.id?.toString() ?: error("Inicia sesión en el ERP")
    suspend fun quotes(force: Boolean = true): List<Quote> { requireSales(); return fetch("quote", force, { api.quotes() }, Quote::id) }
    suspend fun orders(force: Boolean = true): List<Order> { requireSales(); return fetch("order", force, { api.orders() }, Order::id) }
    suspend fun sales(force: Boolean = true): List<Sale> { requireSales(); return fetch("sale", force, { api.sales() }, Sale::id) }
    suspend fun inventory(force: Boolean = true): List<InventoryItem> { requireInventory(); return fetch("inventory", force, { api.inventory() }, InventoryItem::id) }

    private suspend inline fun <reified T> fetch(kind: String, force: Boolean, crossinline remote: suspend () -> List<T>, crossinline id: (T) -> Long): List<T> {
        requireSession()
        val owner = owner()
        if (!force) return dao.cached(owner, kind).map { gson.fromJson(it.json, T::class.java) }
        return try {
            remote().also { result -> dao.replace(owner, kind, result.map { CachedRecord(owner, kind, id(it), gson.toJson(it), System.currentTimeMillis()) }) }
        } catch (e: Exception) {
            if (e is CancellationException || e is HttpException) throw e
            val cached = dao.cached(owner, kind)
            if (cached.isEmpty()) throw e
            usedCache = true
            cached.map { gson.fromJson(it.json, T::class.java) }
        }
    }

    suspend fun suppliers(force: Boolean = true): List<RemoteSupplier> { requireWarehouse(); return fetch("supplier", force, { api.suppliers() }, RemoteSupplier::id) }
    suspend fun purchaseOrders(force: Boolean = true): List<PurchaseOrder> { requireWarehouse(); return fetch("purchase", force, { api.purchaseOrders() }, PurchaseOrder::id) }
    suspend fun receipts(force: Boolean = true): List<Receipt> { requireWarehouse(); return fetch("receipt", force, { api.receipts() }, Receipt::id) }
    suspend fun budget(): Budget { requireWarehouse(); return api.budget() }
    suspend fun createSupplier(body: SupplierBody): RemoteSupplier { requireWarehouse(); return api.createSupplier(body) }
    suspend fun createPurchaseOrder(body: PurchaseOrderBody): PurchaseOrder { requireWarehouse(); return api.createPurchaseOrder(body) }
    suspend fun purchaseState(id: Long, status: String): PurchaseOrder { requireWarehouse(); return api.purchaseState(id, StateBody(status)) }
    suspend fun receivePurchase(body: ReceiptBody): ReceiptResult { requireWarehouse(); return api.receivePurchase(body) }
    suspend fun changeStock(id: Long, kind: String, quantity: Int) {
        requireWarehouse()
        val response = if(kind == "AJUSTE") {
            val current = api.inventoryItem(id)
            require(quantity >= (current.stockReservado ?: 0)) { "El ajuste no puede reducir el stock reservado." }
            api.adjustStock(id, InventoryUpdate(current.producto.orEmpty(), current.categoria, quantity, current.stockMinimo, current.precioUnitario, current.unidad, current.stockObjetivo))
        } else api.changeStock(id, StockBody(kind, quantity))
        require(response.success) { response.mensaje ?: "No se actualizó el stock." }
    }

    suspend fun createQuote(body: QuoteCreate): Quote {
        val session = requireSales()
        require(body.usuarioId == session.user.id) { "Usuario no válido" }
        val response = api.createQuote(body)
        require(response.success) { response.mensaje ?: "No se creó la cotización" }
        return response.cotizacion ?: error(response.mensaje ?: "No se creó la cotización")
    }
    suspend fun createOrder(order: Order): Order {
        requireSales()
        return api.createOrder(order.copy(id = 0, usuarioId = owner().toLong()))
    }
    suspend fun convertQuote(id: Long): Order { requireSales(); return api.convertQuote(id).pedido }
    suspend fun orderState(id: Long, state: String) {
        requireSales(); val response = api.orderState(id, StateBody(state))
        if (!response.isSuccessful) error("No se cambió el estado (${response.code()}). Actualiza el listado antes de volver a confirmar.")
        response.body()?.close()
    }
    suspend fun quoteState(id: Long, state: String) {
        requireSales(); val response = api.quoteState(id, StateBody(state))
        if (!response.isSuccessful) error("No se cambió el estado (${response.code()}). Actualiza el listado antes de volver a confirmar.")
        response.body()?.close()
    }
    suspend fun saleFromOrder(id: Long): Sale { requireSales(); return api.saleFromOrder(id) }
    suspend fun saveDraft(kind: String, json: String): Draft {
        val draft = Draft(java.util.UUID.randomUUID().toString(), owner(), kind, json, System.currentTimeMillis())
        dao.saveDraft(draft); return draft
    }
    suspend fun drafts(): List<Draft> = dao.drafts(owner())
    suspend fun deleteDraft(id: String) = dao.deleteDraft(id, owner())
    fun gson() = gson
}

/** Background refresh is read-only: this ERP has no idempotency key for safe automatic POST retries. */
class CacheSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = ErpRepository(applicationContext)
            if (repo.vault.read() == null) return Result.success()
            val role = repo.vault.read()!!.user.rol
            if (BusinessRules.canSell(role)) { repo.quotes(); repo.orders(); repo.sales() }
            if (BusinessRules.canWarehouse(role)) { repo.suppliers(); repo.purchaseOrders(); repo.receipts() }
            if (!BusinessRules.canInventory(role)) return Result.success()
            val stock = repo.inventory()
            if (!repo.usedCache) pe.edu.unmsm.siderurgica.StockNotifications.check(applicationContext, stock, repo.vault.read()!!.user.id.toString())
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }
}


