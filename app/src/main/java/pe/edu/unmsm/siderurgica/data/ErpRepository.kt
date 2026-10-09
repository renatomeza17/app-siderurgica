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
    private val gson = Gson()
    val vault = SessionVault(context)
    private val dao = (context.applicationContext as SiderurgicaApp).db.dao()
    private val api: ErpApi = Retrofit.Builder().baseUrl(BuildConfig.ERP_API_URL)
        .client(OkHttpClient.Builder().addInterceptor { chain ->
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
        require(s.user.rol.uppercase() in setOf("ADMIN", "VENTAS")) { "Tu rol no permite operar ventas" }
        return s
    }
    private fun owner() = vault.read()?.user?.id?.toString() ?: error("Inicia sesión en el ERP")
    suspend fun quotes(force: Boolean = true): List<Quote> = fetch("quote", force, { api.quotes() }, Quote::id)
    suspend fun orders(force: Boolean = true): List<Order> = fetch("order", force, { api.orders() }, Order::id)
    suspend fun sales(force: Boolean = true): List<Sale> = fetch("sale", force, { api.sales() }, Sale::id)
    suspend fun inventory(force: Boolean = true): List<InventoryItem> = fetch("inventory", force, { api.inventory() }, InventoryItem::id)

    private suspend inline fun <reified T> fetch(kind: String, force: Boolean, crossinline remote: suspend () -> List<T>, crossinline id: (T) -> Long): List<T> {
        requireSales()
        val owner = owner()
        if (!force) return dao.cached(owner, kind).map { gson.fromJson(it.json, T::class.java) }
        return try {
            remote().also { result -> dao.replace(owner, kind, result.map { CachedRecord(owner, kind, id(it), gson.toJson(it), System.currentTimeMillis()) }) }
        } catch (e: Exception) {
            val cached = dao.cached(owner, kind)
            if (cached.isEmpty()) throw e
            cached.map { gson.fromJson(it.json, T::class.java) }
        }
    }
    suspend fun createQuote(body: QuoteCreate): Quote {
        val session = requireSales()
        require(body.usuarioId == session.user.id) { "Usuario no válido" }
        val response = api.createQuote(body)
        return response.cotizacion ?: error(response.mensaje ?: "No se creó la cotización")
    }
    suspend fun createOrder(order: Order): Order {
        requireSales()
        return api.createOrder(order.copy(id = 0, usuarioId = owner().toLong()))
    }
    suspend fun convertQuote(id: Long): Order { requireSales(); return api.convertQuote(id).pedido }
    suspend fun orderState(id: Long, state: String) {
        requireSales(); val response = api.orderState(id, StateBody(state))
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "No se cambió el estado")
        response.body()?.close()
    }
    suspend fun quoteState(id: Long, state: String) {
        requireSales(); val response = api.quoteState(id, StateBody(state))
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "No se cambió el estado")
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
            repo.quotes(); repo.orders(); repo.sales(); repo.inventory()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
