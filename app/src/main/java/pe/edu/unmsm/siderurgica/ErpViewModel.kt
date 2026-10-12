package pe.edu.unmsm.siderurgica

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.*
import pe.edu.unmsm.siderurgica.domain.BusinessRules
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

data class UiState(
    val session: ErpSession? = null, val firebaseEmail: String? = null,
    val quotes: List<Quote> = emptyList(), val orders: List<Order> = emptyList(),
    val sales: List<Sale> = emptyList(), val inventory: List<InventoryItem> = emptyList(),
    val drafts: List<Draft> = emptyList(), val suppliers: List<Supplier> = emptyList(),
    val purchases: List<Purchase> = emptyList(), val movements: List<StockMovement> = emptyList(),
    val documents: List<ScannedDocument> = emptyList(),
    val purchaseOrders: List<PurchaseOrder> = emptyList(), val receipts: List<Receipt> = emptyList(), val budget: Budget? = null,
    val loading: Boolean = false, val message: String = "", val offline: Boolean = false,
    val updatedAt: Long? = null
)

class ErpViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as SiderurgicaApp).repository
    private val operations = (app as SiderurgicaApp).db.operations()
    private val auth = if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val mutable = MutableStateFlow(UiState(session = repo.vault.read(), firebaseEmail = auth?.currentUser?.email))
    val state = mutable.asStateFlow()
    private var active: Job? = null
    init {
        if (mutable.value.session != null) refresh()
        val work = PeriodicWorkRequestBuilder<CacheSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("erp-cache", ExistingPeriodicWorkPolicy.KEEP, work)
    }
    private fun run(action: suspend () -> Unit) {
        if (mutable.value.loading) return
        active = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, message = "")
            try { action() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(offline = mutable.value.offline || e is java.io.IOException || repo.usedCache, message = when (e) {
                    is HttpException -> when(e.code()) {
                        401 -> "La sesión ERP venció. Cierra sesión y vuelve a ingresar."
                        403 -> "Tu cuenta no tiene permiso para esta operación."
                        else -> "El servidor no pudo completar la operación (${e.code()}). Si enviaste datos, revisa el listado antes de repetir."
                    }
                    is FirebaseAuthException -> "No pudimos acceder a Firebase. Revisa el correo y la contraseña o recupera tu acceso."
                    is java.io.IOException -> "Sin respuesta del servidor. Revisa tu conexión; si enviaste datos, verifica el listado antes de repetir."
                    else -> e.message ?: "No se pudo completar la operación."
                })
            } finally { mutable.value = mutable.value.copy(loading = false) }
        }
    }
    fun clearMessage() { mutable.value = mutable.value.copy(message = "") }

    fun loginUnificado(identifier: String, pass: String) = run {
        val email = identifier.trim()

        require(email.isNotBlank() && pass.isNotBlank()) {
            "Completa el correo y la contraseña."
        }

        require(auth == null || BusinessRules.validEmail(email)) {
            "Ingresa el correo completo registrado en Firebase."
        }

        // admin@siderurgica.com -> admin
        val username = email.substringBefore("@").trim()

        try {
            val session = repo.login(username, pass)

            auth?.signInWithEmailAndPassword(email, pass)?.await()

            mutable.value = mutable.value.copy(
                session = session,
                firebaseEmail = auth?.currentUser?.email
            )
        } catch (e: Exception) {
            repo.vault.clear()
            auth?.signOut()

            mutable.value = mutable.value.copy(
                session = null,
                firebaseEmail = null
            )

            when (e) {
                is kotlinx.coroutines.CancellationException -> throw e

                is com.google.firebase.auth.FirebaseAuthException ->
                    error(
                        "Firebase no aceptó el correo y la contraseña. " +
                                "Comprueba el acceso de esa cuenta."
                    )

                is retrofit2.HttpException -> {
                    if (e.code() == 401 || e.code() == 403) {
                        error(
                            "El ERP no aceptó el usuario '$username' " +
                                    "con esa contraseña."
                        )
                    } else {
                        throw e
                    }
                }

                else -> throw e
            }
        }

        refreshData()
    }
    fun registerUnificado(email: String, pass: String) = run {
        val cleanEmail = email.trim()

        require(BusinessRules.validEmail(cleanEmail)) {
            "Ingresa un correo válido."
        }
        require(pass.length >= 6) {
            "La contraseña debe tener al menos 6 caracteres."
        }

        val firebase = auth
            ?: error("Firebase no está configurado en esta aplicación.")

        try {
            // El usuario debe existir previamente en el ERP.
            // Su rol lo devuelve el backend.
            val session = repo.login(
                cleanEmail.substringBefore("@"),
                pass
            )

            firebase.createUserWithEmailAndPassword(
                cleanEmail,
                pass
            ).await()

            mutable.value = mutable.value.copy(
                session = session,
                firebaseEmail = firebase.currentUser?.email
            )
        } catch (e: Exception) {
            repo.vault.clear()
            firebase.signOut()
            mutable.value = mutable.value.copy(
                session = null,
                firebaseEmail = null
            )
            throw e
        }

        refreshData()
    }
    fun resetPassword(email: String) = run {
        require(BusinessRules.validEmail(email)) { "Ingresa tu correo para recuperar el acceso." }
        (auth ?: error("La recuperación Firebase no está disponible.")).sendPasswordResetEmail(email.trim()).await()
        mutable.value = mutable.value.copy(message = "Revisa tu correo para recuperar la contraseña.")
    }
    fun logout() { active?.cancel(); repo.vault.clear(); auth?.signOut(); mutable.value = UiState(message = "Sesión cerrada.") }
    fun refresh() = run { refreshData() }
    private suspend fun localData() {
        val owner = repo.vault.read()?.user?.id?.toString() ?: return
        mutable.value = mutable.value.copy(drafts = repo.drafts(), suppliers = (if(BusinessRules.canWarehouse(repo.requireSession().user.rol)) repo.suppliers(false).map { Supplier("erp-${it.id}", owner, it.nombre, it.ruc, it.contacto.orEmpty(), it.telefono.orEmpty(), "", it.id) } else emptyList()) + operations.suppliers(owner),
            purchases = operations.purchases(owner), movements = operations.movements(owner), documents = operations.documents(owner))
    }
    private suspend fun refreshData() {
        repo.resetCacheStatus()

        val failures = mutableListOf<String>()
        var networkFailure = false

        suspend fun load(name: String, action: suspend () -> Unit) {
            try {
                action()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is java.io.IOException) networkFailure = true

                val reason = when (e) {
                    is HttpException -> when (e.code()) {
                        401 -> "sesión vencida; vuelve a ingresar"
                        403 -> "sin permiso para consultar"
                        404 -> "ruta no disponible en el servidor"
                        else -> "error HTTP ${e.code()}"
                    }
                    is java.io.IOException -> "sin respuesta del servidor"
                    else -> "no se pudo cargar; revisa el error en Android Studio"
                }

                android.util.Log.w(
                    "ErpData",
                    "$name: ${e.javaClass.simpleName}"
                )
                failures += "$name: $reason"
            }
        }

        val role = repo.requireSession().user.rol

        load("Datos locales") {
            localData()
        }

        if (BusinessRules.canSell(role)) {
            load("Cotizaciones") {
                mutable.value = mutable.value.copy(quotes = repo.quotes())
            }
            load("Pedidos") {
                mutable.value = mutable.value.copy(orders = repo.orders())
            }
            load("Ventas") {
                mutable.value = mutable.value.copy(sales = repo.sales())
            }
        }

        if (BusinessRules.canInventory(role)) {
            load("Inventario") {
                mutable.value = mutable.value.copy(inventory = repo.inventory())
            }
        }

        if (BusinessRules.canWarehouse(role)) {
            load("Proveedores") {
                repo.suppliers()
                localData()
            }
            load("Órdenes de compra") {
                mutable.value = mutable.value.copy(
                    purchaseOrders = repo.purchaseOrders()
                )
            }
            load("Recepciones") {
                mutable.value = mutable.value.copy(receipts = repo.receipts())
            }
            load("Presupuesto") {
                mutable.value = mutable.value.copy(budget = repo.budget())
            }
        }

        val offline = repo.usedCache || networkFailure

        mutable.value = mutable.value.copy(
            offline = offline,
            updatedAt = if (!offline && failures.isEmpty()) {
                System.currentTimeMillis()
            } else {
                mutable.value.updatedAt
            },
            message = when {
                failures.isNotEmpty() ->
                    "Carga parcial. " + failures.joinToString(" · ")
                offline ->
                    "Mostrando datos guardados. Actualiza antes de operar."
                else ->
                    "Datos actualizados."
            }
        )
    }


    fun loadCache() = run {
        val role = repo.requireSession().user.rol
        if(BusinessRules.canSell(role)) mutable.value = mutable.value.copy(quotes = repo.quotes(false), orders = repo.orders(false), sales = repo.sales(false))
        if(BusinessRules.canInventory(role)) mutable.value = mutable.value.copy(inventory = repo.inventory(false))
        if(BusinessRules.canWarehouse(role)) mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders(false), receipts = repo.receipts(false))
        mutable.value = mutable.value.copy(offline = true, message = "Mostrando datos guardados. No representan el stock actual."); localData()
    }
    fun saveQuote(body: QuoteCreate, asDraft: Boolean = false) = run {
        repo.requireSales(); validate(body.cliente, body.producto, body.cantidad, body.precioUnitario, body.ruc)
        if (asDraft) { repo.saveDraft("QUOTE", repo.gson().toJson(body)); localData(); message("Borrador guardado.") }
        else { repo.createQuote(body); message("Cotización registrada."); mutable.value = mutable.value.copy(quotes = repo.quotes()) }
    }
    fun createOrder(order: Order, asDraft: Boolean = false) = run {
        repo.requireSales(); validate(order.cliente, order.producto, order.cantidad, order.precioUnitario, order.ruc)
        if (asDraft) { repo.saveDraft("ORDER", repo.gson().toJson(order)); localData(); message("Borrador guardado.") }
        else { repo.createOrder(order); message("Pedido registrado."); mutable.value = mutable.value.copy(orders = repo.orders()) }
    }
    private fun validate(client: String, product: String, quantity: Int, price: Double, ruc: String?) {
        require(client.isNotBlank() && product.isNotBlank()) { "Completa el cliente y el producto." }
        require(quantity > 0 && price.isFinite() && price > 0 && (quantity * price).isFinite()) { "Cantidad y precio deben ser mayores a cero." }
        require(ruc.isNullOrBlank() || BusinessRules.validRuc(ruc)) { "Revisa el RUC: debe tener 11 dígitos y ser válido." }
    }
    fun sendDraft(draft: Draft) = run {
        repo.requireSales()
        when (draft.kind) {
            "QUOTE" -> repo.createQuote(repo.gson().fromJson(draft.json, QuoteCreate::class.java))
            "ORDER" -> repo.createOrder(repo.gson().fromJson(draft.json, Order::class.java))
            else -> error("Tipo de borrador desconocido.")
        }
        repo.deleteDraft(draft.localId); localData(); message("Borrador enviado.")
        mutable.value = mutable.value.copy(quotes = repo.quotes(), orders = repo.orders())
    }
    fun discardDraft(draft: Draft) = run { repo.deleteDraft(draft.localId); localData(); message("Borrador eliminado.") }
    fun approveQuote(id: Long) = run {
        repo.quoteState(id, "APROBADA"); message("Cotización aprobada.")
        mutable.value = mutable.value.copy(quotes = repo.quotes(), orders = repo.orders())
    }
    fun orderState(id: Long, status: String) = run {
        repo.orderState(id, status); message("Estado actualizado.")
        mutable.value = mutable.value.copy(orders = repo.orders(), sales = repo.sales(), inventory = repo.inventory())
    }
    fun convertQuote(id: Long) = run { repo.convertQuote(id); message("Pedido creado desde cotización."); mutable.value = mutable.value.copy(quotes = repo.quotes(), orders = repo.orders()) }
    fun rejectQuote(id: Long) = run { repo.quoteState(id, "RECHAZADA"); message("Cotización rechazada."); mutable.value = mutable.value.copy(quotes = repo.quotes()) }
    fun sale(id: Long) = run { repo.saleFromOrder(id); message("Venta registrada."); mutable.value = mutable.value.copy(orders = repo.orders(), sales = repo.sales()) }
    private fun owner() = repo.requireSession().user.id.toString()
    private fun message(value: String) { mutable.value = mutable.value.copy(message = value) }
    fun saveSupplier(name: String, ruc: String, contact: String, phone: String, email: String, asDraft: Boolean = false) = run {
        repo.requireWarehouse()
        require(name.isNotBlank() && BusinessRules.validRuc(ruc)) { "Ingresa una razón social y RUC válido." }
        require(email.isBlank() || BusinessRules.validEmail(email)) { "Revisa el correo del proveedor." }
        val owner = owner()
        require(operations.suppliers(owner).none { it.ruc == ruc }) { "Ya existe un proveedor con ese RUC en el dispositivo." }
        require(mutable.value.suppliers.none { it.ruc == ruc }) { "Ya existe un proveedor con ese RUC." }
        if(asDraft) {
            operations.addSupplier(Supplier(id(), owner, name.trim(), ruc, contact.trim(), phone.trim(), email.trim()))
            localData(); message("Proveedor guardado como borrador local.")
        } else {
            require(!mutable.value.offline) { "Actualiza o guarda un borrador sin conexión." }
            require(repo.suppliers().none { it.ruc == ruc }) { "Ya existe ese RUC en el ERP." }
            repo.createSupplier(SupplierBody(name.trim(), ruc, contact.trim(), phone.trim())); repo.suppliers(); localData(); message("Proveedor registrado en el ERP.")
        }
    }
    fun savePurchase(supplier: Supplier, product: String, quantity: Int, price: Double, budget: Double, asDraft: Boolean = false) = run {
        repo.requireWarehouse()
        validate(supplier.name, product, quantity, price, supplier.ruc)
        require(supplier.owner == owner()) { "Proveedor no válido." }
        require(budget.isFinite() && budget > 0 && quantity * price <= budget + .001) { "El total supera el presupuesto." }
        if(asDraft) {
            operations.addPurchase(Purchase(id(), owner(), supplier.localId, supplier.name, product.trim(), quantity, price, budget, System.currentTimeMillis()))
            localData(); message("Borrador de compra guardado.")
        } else {
            require(!mutable.value.offline && supplier.serverId != null) { "Actualiza y selecciona un proveedor registrado en el ERP." }
            repo.createPurchaseOrder(PurchaseOrderBody(supplier.serverId, product.trim(), quantity.toString(), amount(quantity * price), repo.requireSession().user.id))
            mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders(), budget = runCatching { repo.budget() }.getOrNull()); message("Orden de compra registrada.")
        }
    }
    fun saveMovement(item: InventoryItem, kind: String, quantity: Int, reason: String, asDraft: Boolean = false) = run {
        repo.requireWarehouse()
        require((quantity > 0 || kind == "AJUSTE" && quantity == 0) && reason.isNotBlank() && kind in listOf("ENTRADA", "SALIDA", "AJUSTE")) { "Completa cantidad y motivo." }
        require(kind != "SALIDA" || item.stockDisponible == null || quantity <= item.stockDisponible) { "La salida supera el último stock disponible." }
        if(asDraft) {
            operations.addMovement(StockMovement(id(), owner(), item.id, item.producto.orEmpty(), kind, quantity, reason.trim(), System.currentTimeMillis()))
            localData(); message("Solicitud guardada. El stock del ERP no se ha modificado.")
        } else {
            require(!mutable.value.offline) { "Actualiza antes de modificar stock." }
            repo.changeStock(item.id, kind, quantity); mutable.value = mutable.value.copy(inventory = repo.inventory()); message("Movimiento registrado en el ERP.")
        }
    }
    fun saveDocument(path: String, ruc: String, number: String, date: String, amount: Double, code: String) = run {
        require(BusinessRules.validRuc(ruc) && number.isNotBlank()) { "Revisa RUC y número del documento." }
        require(runCatching { java.time.LocalDate.parse(date, java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.isSuccess) { "Usa una fecha válida: dd/MM/aaaa." }
        require(amount.isFinite() && amount > 0) { "Ingresa un monto válido." }
        require(java.io.File(path).exists()) { "Vuelve a capturar el documento." }
        operations.addDocument(ScannedDocument(id(), owner(), path, ruc, number, date, amount, code, System.currentTimeMillis()))
        localData(); message("Documento revisado y guardado en el dispositivo.")
    }
    fun saveCode(path: String, code: String) = run {
        require(code.isNotBlank() && java.io.File(path).exists()) { "Captura una imagen con un código legible." }
        operations.addDocument(ScannedDocument(id(), owner(), path, "", "Código escaneado", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")), 0.0, code, System.currentTimeMillis()))
        localData(); message("Código revisado y guardado en el dispositivo.")
    }
    fun uploadDocument(document: ScannedDocument) = run {
        require(document.owner == owner()) { "Documento no válido." }
        val uid = auth?.currentUser?.uid ?: error("Inicia sesión en Firebase para subir documentos.")
        val path = "usuarios/$uid/documentos/${document.localId}.jpg"
        com.google.firebase.storage.FirebaseStorage.getInstance().reference.child(path)
            .putFile(android.net.Uri.fromFile(java.io.File(document.imagePath)), com.google.firebase.storage.StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        operations.markUploaded(document.localId, owner(), path); localData(); message("Imagen subida a Firebase Storage.")
    }
    fun sendSupplier(supplier: Supplier) = run {
        repo.requireWarehouse(); require(supplier.owner == owner() && supplier.serverId == null) { "Proveedor no válido." }
        require(repo.suppliers().none { it.ruc == supplier.ruc }) { "El RUC ya existe en el ERP. Revisa la lista antes de reenviar." }
        repo.createSupplier(SupplierBody(supplier.name, supplier.ruc, supplier.contact, supplier.phone))
        operations.deleteSupplier(supplier.localId, owner()); repo.suppliers(); localData(); message("Proveedor enviado al ERP.")
    }
    fun sendPurchase(purchase: Purchase) = run {
        require(purchase.owner == owner()) { "Borrador no válido." }; repo.requireWarehouse()
        val localSupplier = operations.suppliers(owner()).firstOrNull { it.localId == purchase.supplierId }
        val supplier = repo.suppliers().firstOrNull { "erp-${it.id}" == purchase.supplierId || localSupplier?.ruc == it.ruc }
            ?: error("El proveedor debe estar registrado en el ERP. Crea una nueva orden con el proveedor actualizado.")
        repo.createPurchaseOrder(PurchaseOrderBody(supplier.id, purchase.product, purchase.quantity.toString(), amount(purchase.quantity * purchase.unitPrice), repo.requireSession().user.id))
        operations.deletePurchase(purchase.localId, owner()); localData(); mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders()); message("Orden enviada al ERP.")
    }
    fun sendMovement(movement: StockMovement) = run {
        require(movement.owner == owner()) { "Solicitud no válida." }; repo.requireWarehouse()
        repo.changeStock(movement.inventoryId, movement.kind, movement.quantity); operations.deleteMovement(movement.localId, owner()); localData()
        mutable.value = mutable.value.copy(inventory = repo.inventory()); message("Movimiento enviado al ERP.")
    }
    fun purchaseState(order: PurchaseOrder, status: String) = run {
        repo.purchaseState(order.id, status); mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders()); message("Estado de compra actualizado.")
    }
    fun receivePurchase(order: PurchaseOrder, quantity: Int, price: Double) = run {
        repo.requireWarehouse(); require(quantity > 0 && price.isFinite() && price > 0 && (quantity * price).isFinite()) { "Cantidad y precio deben ser positivos." }
        require(amount(quantity * price) == amount(order.total)) { "La recepción debe coincidir con el importe de la orden." }
        require(repo.receipts().none { it.ordenCompraId == order.id }) { "Esta orden ya tiene una recepción registrada." }
        repo.receivePurchase(ReceiptBody(order.id, order.proveedorId, mutable.value.inventory.firstOrNull { it.producto.equals(order.producto, true) }?.id,
            order.producto, quantity, price, amount(quantity * price), repo.requireSession().user.id))
        mutable.value = mutable.value.copy(receipts = repo.receipts(), purchaseOrders = repo.purchaseOrders(), inventory = repo.inventory()); message("Compra recibida e inventario actualizado.")
    }
    private fun amount(value: Double) = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    fun firebaseConfigured() = auth != null
    private fun id() = java.util.UUID.randomUUID().toString()
}
