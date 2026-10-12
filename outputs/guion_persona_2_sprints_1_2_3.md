# Guion para dos expositores — Sprints 1, 2 y 3

El reparto equilibra el discurso y la carga de código, no el número de historias. Persona 1 tiene aproximadamente 2188 palabras habladas (52%) y Persona 2, 1988 (48%); no se cuentan bloques de código ni referencias de pantalla. El tiempo real depende de la demo y las pausas.

Como referencia para una sesión de 40 minutos, reservar unos 20 minutos por persona, incluyendo explicación del código. No es necesario leer cada línea: leer la narración y señalar el fragmento correspondiente. Los nombres «Persona 1» y «Persona 2» pueden sustituirse por los de los integrantes.

| Orden | Expositor | Contenido |
|---|---|---|
| Apertura | Persona 1 | Presentación del recorrido y alcance |
| HU01 · Sprint 1 | Persona 1 | Base MVVM, Clean Code y Material Design 3 |
| HU02 · Sprint 1 | Persona 1 | Autenticación, sesión y biometría |
| HU03 · Sprint 1 | Persona 2 | Consulta offline y sincronización |
| HU04 · Sprint 2 | Persona 1 | Cotizaciones desde el móvil |
| HU05 · Sprint 2 | Persona 2 | Pedidos, aprobación y venta |
| HU06 · Sprint 2 | Persona 1 | PDF, vista previa, compartir y Storage |
| HU07 · Sprint 3 | Persona 1 | Inventario, movimientos y alertas de stock |
| HU08 · Sprint 3 | Persona 2 | Proveedores, órdenes de compra y recepción |
| HU09 · Sprint 3 | Persona 2 | Captura de documentos, OCR y códigos |
| Cierre | Persona 2 | Avance global, pendientes y paso a la demo |

Se conserva íntegro el contenido técnico del guion original, sus fragmentos y advertencias de alcance. Los pases de palabra se añaden después de completar una historia. Cada sección la presenta una sola persona, incluida su navegación entre pantalla, lógica, repositorio y persistencia.

Quien expone controla la navegación del código o indica al compañero qué fragmento mostrar. Antes de la presentación, ensayar especialmente los cambios HU02→HU03, HU03→HU04, HU04→HU05, HU05→HU06 y HU07→HU08. El segundo expositor escucha los pasos anteriores: las historias reutilizan piezas ya explicadas.


## Tu primera intervención

Entras después de HU02. Continúa desde la sesión y los permisos ya explicados.

*Recibes la palabra después de HU02. Continúa con el recorrido siguiente.*

## Persona 2 · Sprint 1 · HU03: Consulta offline y sincronización

HU03 busca que una interrupción de internet no nos deje sin los últimos datos y que podamos preparar operaciones. Voy a seguir dos caminos: una lectura con caché y un borrador confirmado por el usuario.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 120–120.*

```kotlin
                            { page = it }, vm::loadCache, { vm.logout(); unlocked = true }, activity)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 275–281.*

```kotlin
    fun loadCache() = run {
        val role = repo.requireSession().user.rol
        if(BusinessRules.canSell(role)) mutable.value = mutable.value.copy(quotes = repo.quotes(false), orders = repo.orders(false), sales = repo.sales(false))
        if(BusinessRules.canInventory(role)) mutable.value = mutable.value.copy(inventory = repo.inventory(false))
        if(BusinessRules.canWarehouse(role)) mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders(false), receipts = repo.receipts(false))
        mutable.value = mutable.value.copy(offline = true, message = "Mostrando datos guardados. No representan el stock actual."); localData()
    }
```

El evento Consulta guardada llega a loadCache. Según el rol, pedimos listados con force=false. Para cotizaciones sigo la llamada al repositorio y luego a fetch, que es la implementación común de las lecturas.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 90–90.*

```kotlin
    suspend fun quotes(force: Boolean = true): List<Quote> { requireSales(); return fetch("quote", force, { api.quotes() }, Quote::id) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 95–99.*

```kotlin
    private suspend inline fun <reified T> fetch(kind: String, force: Boolean, crossinline remote: suspend () -> List<T>, crossinline id: (T) -> Long): List<T> {
        requireSession()
        val owner = owner()
        if (!force) return dao.cached(owner, kind).map { gson.fromJson(it.json, T::class.java) }
        return try {
```

Al pedir datos locales, obtenemos el usuario propietario y consultamos LocalDao. La selección usa owner y kind para que no mezclemos cotizaciones de diferentes cuentas. Sigo la query hasta SQLite a través de Room.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 14–16.*

```kotlin
@Dao interface LocalDao {
    @Query("SELECT * FROM cache WHERE owner = :owner AND kind = :kind ORDER BY serverId DESC")
    suspend fun cached(owner: String, kind: String): List<CachedRecord>
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 5–6.*

```kotlin
@Entity(tableName = "cache", primaryKeys = ["owner", "kind", "serverId"])
data class CachedRecord(val owner: String, val kind: String, val serverId: Long, val json: String, val cachedAt: Long)
```

La respuesta de Room contiene registros JSON, que fetch convierte al modelo correspondiente. Regresamos al ViewModel, publicamos los listados y marcamos que estamos viendo datos guardados. Eso informa a la persona que el stock puede haber cambiado.

Si hacemos una consulta online, el mismo fetch llama primero al endpoint remoto y reemplaza la caché con la respuesta. Sigo esa rama y su operación transaccional local.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 100–107.*

```kotlin
            remote().also { result -> dao.replace(owner, kind, result.map { CachedRecord(owner, kind, id(it), gson.toJson(it), System.currentTimeMillis()) }) }
        } catch (e: Exception) {
            if (e is CancellationException || e is HttpException) throw e
            val cached = dao.cached(owner, kind)
            if (cached.isEmpty()) throw e
            usedCache = true
            cached.map { gson.fromJson(it.json, T::class.java) }
        }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 17–21.*

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCache(rows: List<CachedRecord>)
    @Query("DELETE FROM cache WHERE owner = :owner AND kind = :kind") suspend fun clearKind(owner: String, kind: String)
    @Transaction suspend fun replace(owner: String, kind: String, rows: List<CachedRecord>) {
        clearKind(owner, kind); putCache(rows)
    }
```

Aquí eliminamos el conjunto anterior de ese usuario y tipo y guardamos el nuevo dentro de una transacción Room. Ante una excepción no HTTP y no de cancelación, intentamos recuperar datos previos. Un 401 o un 403 se propaga; no lo ocultamos bajo una lectura local. Si no hay caché, también propagamos el error.

Para preparar una operación sin conexión, presiono Guardar como borrador. El formulario llama al mismo submit con draft=true y entrega el DTO al ViewModel.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 200–200.*

```kotlin
                OutlinedButton(onClick = { submit(true) }, enabled = valid && !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Guardar como borrador") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 193–198.*

```kotlin
            fun submit(draft: Boolean) {
                submitted = true
                if(quote) vm.saveQuote(QuoteCreate(client.trim(), ruc.ifBlank { null }, product.trim(), n, selectedId, unit, state.session!!.user.id), draft)
                else vm.createOrder(Order(cliente = client.trim(), ruc = ruc.ifBlank { null }, producto = product.trim(), cantidad = n, inventarioId = selectedId, precioUnitario = unit, usuarioId = state.session!!.user.id), draft)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 282–286.*

```kotlin
    fun saveQuote(body: QuoteCreate, asDraft: Boolean = false) = run {
        repo.requireSales(); validate(body.cliente, body.producto, body.cantidad, body.precioUnitario, body.ruc)
        if (asDraft) { repo.saveDraft("QUOTE", repo.gson().toJson(body)); localData(); message("Borrador guardado.") }
        else { repo.createQuote(body); message("Cotización registrada."); mutable.value = mutable.value.copy(quotes = repo.quotes()) }
    }
```

En esta rama validamos igual que en línea, serializamos el payload y delegamos en saveDraft. Esta llamada no envía un POST.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 151–154.*

```kotlin
    suspend fun saveDraft(kind: String, json: String): Draft {
        val draft = Draft(java.util.UUID.randomUUID().toString(), owner(), kind, json, System.currentTimeMillis())
        dao.saveDraft(draft); return draft
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 8–12.*

```kotlin
@Entity(tableName = "drafts")
data class Draft(
    @PrimaryKey val localId: String, val owner: String, val kind: String, val json: String,
    val createdAt: Long, val status: String = "PENDIENTE"
)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 22–24.*

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(draft: Draft)
    @Query("SELECT * FROM drafts WHERE owner = :owner AND status = 'PENDIENTE' ORDER BY createdAt")
    suspend fun drafts(owner: String): List<Draft>
```

Persistimos el borrador con UUID, usuario, tipo y fecha. Al volver a la pantalla aparece pendiente. Cuando el usuario confirma su envío, seguimos sendDraft: escogemos el endpoint por tipo y borramos el registro local solo después de que la llamada remota retorna sin error.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 297–306.*

```kotlin
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
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 156–156.*

```kotlin
    suspend fun deleteDraft(id: String) = dao.deleteDraft(id, owner())
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 25–25.*

```kotlin
    @Query("DELETE FROM drafts WHERE localId = :id AND owner = :owner") suspend fun deleteDraft(id: String, owner: String)
```

La parte periódica se agenda al inicializar el ViewModel con una restricción de red conectada. WorkManager administra cuándo puede ejecutarse; quince minutos es el intervalo configurado, no una promesa de ejecución exacta.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 41–43.*

```kotlin
        if (mutable.value.session != null) refresh()
        val work = PeriodicWorkRequestBuilder<CacheSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 161–180.*

```kotlin
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


```

El worker consulta datos según rol y revisa stock cuando la consulta no usó caché. No consume ni envía los borradores: solo actualiza lecturas. Cierro esta historia con persistencia local, consulta offline y refresco periódico presentes. Queda pendiente el criterio de sincronizar escrituras automáticamente evitando duplicados. Una respuesta perdida después de un POST todavía puede causar un reenvío duplicado; la solución necesita idempotencia del servidor. Esta distinción nos acompaña ahora en el sprint comercial.

**Persona 2 — pase de palabra:**

Ya vimos qué se guarda localmente y qué queda pendiente de envío. Ahora mi compañero seguirá una cotización desde el formulario hasta su respuesta en el ERP.

*Recibes la palabra después de HU04. Continúa con el recorrido siguiente.*

## Persona 2 · Sprint 2 · HU05: Pedidos, aprobación y venta

HU05 continúa el recorrido: la propuesta comercial se convierte en un compromiso de entrega y luego en una venta. Podemos partir de la conversión anterior o crear un pedido directo. Para este segundo camino el formulario llama createOrder.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 196–196.*

```kotlin
                else vm.createOrder(Order(cliente = client.trim(), ruc = ruc.ifBlank { null }, producto = product.trim(), cantidad = n, inventarioId = selectedId, precioUnitario = unit, usuarioId = state.session!!.user.id), draft)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 287–291.*

```kotlin
    fun createOrder(order: Order, asDraft: Boolean = false) = run {
        repo.requireSales(); validate(order.cliente, order.producto, order.cantidad, order.precioUnitario, order.ruc)
        if (asDraft) { repo.saveDraft("ORDER", repo.gson().toJson(order)); localData(); message("Borrador guardado.") }
        else { repo.createOrder(order); message("Pedido registrado."); mutable.value = mutable.value.copy(orders = repo.orders()) }
    }
```

Repetimos control de rol y validación de cliente, producto, cantidad, precio y RUC. Si no es borrador, seguimos al repositorio, que fija el identificador a cero y sustituye usuarioId por el propietario de la sesión.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 135–138.*

```kotlin
    suspend fun createOrder(order: Order): Order {
        requireSales()
        return api.createOrder(order.copy(id = 0, usuarioId = owner().toLong()))
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 51–51.*

```kotlin
    @POST("pedidos") suspend fun createOrder(@Body body: Order): Order
```

El endpoint devuelve un Order. Al regresar, el ViewModel vuelve a consultar pedidos y publica Pedido registrado. La pantalla también muestra el último stock y bloquea la confirmación directa cuando la cantidad supera el disponible conocido; esa observación no reemplaza la validación concurrente del ERP.

Sobre un pedido pendiente, presiono Aprobar. Sigo el evento hasta orderState, el repositorio y el PATCH.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 69–69.*

```kotlin
                                Button(onClick = { confirm = Confirmation("¿Aprobar pedido?", "El ERP validará y descontará el stock para el pedido de ${order.cliente}. La venta se genera después.") { vm.orderState(order.id, "APROBADO") } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Aprobar pedido") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 312–315.*

```kotlin
    fun orderState(id: Long, status: String) = run {
        repo.orderState(id, status); message("Estado actualizado.")
        mutable.value = mutable.value.copy(orders = repo.orders(), sales = repo.sales(), inventory = repo.inventory())
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 140–144.*

```kotlin
    suspend fun orderState(id: Long, state: String) {
        requireSales(); val response = api.orderState(id, StateBody(state))
        if (!response.isSuccessful) error("No se cambió el estado (${response.code()}). Actualiza el listado antes de volver a confirmar.")
        response.body()?.close()
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 52–52.*

```kotlin
    @PATCH("pedidos/{id}/estado") suspend fun orderState(@Path("id") id: Long, @Body body: StateBody): Response<ResponseBody>
```

Aquí enviamos StateBody con APROBADO y comprobamos el código HTTP. Tras la respuesta recargamos pedidos, ventas e inventario. El móvil muestra el resultado del servidor; no ejecuta la reserva o el descuento mediante una query local. ANULADO y RECHAZADO utilizan la misma cadena con distinto estado.

Cuando el pedido queda APROBADO, la interfaz ofrece Generar venta. Sigo esa acción sin detenerme en otro archivo ajeno al flujo.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 73–73.*

```kotlin
                                if(order.estado == "APROBADO") Button(onClick = { confirm = Confirmation("¿Generar venta?", "Se registrará la venta y el pedido quedará facturado.") { vm.sale(order.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Generar venta") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 318–318.*

```kotlin
    fun sale(id: Long) = run { repo.saleFromOrder(id); message("Venta registrada."); mutable.value = mutable.value.copy(orders = repo.orders(), sales = repo.sales()) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 150–150.*

```kotlin
    suspend fun saleFromOrder(id: Long): Sale { requireSales(); return api.saleFromOrder(id) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 54–54.*

```kotlin
    @POST("ventas/desde-pedido/{id}") suspend fun saleFromOrder(@Path("id") id: Long): Sale
```

La API devuelve Sale con cliente, producto, cantidad e importes. Después de crear la venta recargamos pedidos y ventas para que la pantalla refleje el resultado. En esta función no recargamos inventario; la recarga de inventario está en orderState y en el refresco general.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 34–39.*

```kotlin
data class Sale(
    val id: Long = 0, val codigo: String? = null, val cliente: String = "", val ruc: String? = null,
    val producto: String = "", val cantidad: Int = 0, val precioUnitario: Double = 0.0,
    val subtotal: Double = 0.0, val igv: Double = 0.0, val total: Double = 0.0,
    val estado: String? = null, val pedidoId: Long? = null
)
```

Cierro HU05 con registro, cambios de estado y generación de venta visibles en el código. No afirmo que las transacciones o la actualización de stock del ERP estén verificadas: faltan sus fuentes y una prueba integrada. Tampoco tenemos reintentos seguros de escritura; OkHttp está configurado sin retryOnConnectionFailure. El README describía venta automática al aprobar y la UI actual ofrece una acción posterior, por lo que esa secuencia debe contrastarse con el servidor real. Ahora tomo la venta recibida para generar su constancia.

**Persona 2 — pase de palabra:**

La venta ya nos devuelve sus datos e importes. Le paso la palabra a mi compañero para mostrar cómo usamos ese resultado para generar y compartir su constancia digital.

*Recibes la palabra después de HU07. Continúa con el recorrido siguiente.*

## Persona 2 · Sprint 3 · HU08: Proveedores, órdenes de compra y recepción

HU08 resuelve el abastecimiento: registrar a quién compramos, controlar el importe preparado y reconocer cuándo la mercadería realmente ingresa. Comienzo creando un proveedor; después uso ese proveedor para una orden y finalmente registro su recepción.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 132–132.*

```kotlin
        TextButton(onClick = { submitted = true; vm.saveSupplier(name, ruc, contact, phone, "") }, enabled = valid && !state.loading && !state.offline) { Text("Registrar en ERP") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 321–329.*

```kotlin
    fun saveSupplier(name: String, ruc: String, contact: String, phone: String, email: String, asDraft: Boolean = false) = run {
        repo.requireWarehouse()
        require(name.isNotBlank() && BusinessRules.validRuc(ruc)) { "Ingresa una razón social y RUC válido." }
        require(email.isBlank() || BusinessRules.validEmail(email)) { "Revisa el correo del proveedor." }
        val owner = owner()
        require(operations.suppliers(owner).none { it.ruc == ruc }) { "Ya existe un proveedor con ese RUC en el dispositivo." }
        require(mutable.value.suppliers.none { it.ruc == ruc }) { "Ya existe un proveedor con ese RUC." }
        if(asDraft) {
            operations.addSupplier(Supplier(id(), owner, name.trim(), ruc, contact.trim(), phone.trim(), email.trim()))
```

Aquí recibimos razón social, RUC y contacto. Aplicamos la validación de RUC que vimos en HU04 y consultamos OperationsDao para detectar duplicados locales; también revisamos la lista cargada.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 15–15.*

```kotlin
    @Query("SELECT * FROM suppliers WHERE owner=:owner ORDER BY name COLLATE NOCASE") suspend fun suppliers(owner: String): List<Supplier>
```

Si elijo borrador, addSupplier persiste la entidad. Su índice único por owner y ruc añade una barrera local de integridad.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 5–6.*

```kotlin
@Entity(tableName = "suppliers", indices = [Index(value = ["owner", "ruc"], unique = true)])
data class Supplier(@PrimaryKey val localId: String, val owner: String, val name: String, val ruc: String, val contact: String, val phone: String, val email: String, val serverId: Long? = null)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 19–19.*

```kotlin
    @Insert suspend fun addSupplier(value: Supplier)
```

Si registro en línea, además de exigir conexión consultamos proveedores remotos, verificamos el RUC y llamamos createSupplier. Sigo esa llamada hasta el POST.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 330–336.*

```kotlin
            localData(); message("Proveedor guardado como borrador local.")
        } else {
            require(!mutable.value.offline) { "Actualiza o guarda un borrador sin conexión." }
            require(repo.suppliers().none { it.ruc == ruc }) { "Ya existe ese RUC en el ERP." }
            repo.createSupplier(SupplierBody(name.trim(), ruc, contact.trim(), phone.trim())); repo.suppliers(); localData(); message("Proveedor registrado en el ERP.")
        }
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 114–114.*

```kotlin
    suspend fun createSupplier(body: SupplierBody): RemoteSupplier { requireWarehouse(); return api.createSupplier(body) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 60–60.*

```kotlin
    @POST("proveedor") suspend fun createSupplier(@Body body: SupplierBody): RemoteSupplier
```

La respuesta retorna un proveedor ERP y después refrescamos el listado. Ya con un proveedor remoto seleccionado, presiono Registrar orden ERP. El diálogo entrega producto, cantidad, precio y presupuesto a savePurchase.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 203–203.*

```kotlin
        TextButton(onClick = { submitted = true; vm.savePurchase(supplier, product, n, p, b) }, enabled = valid && !state.loading && !state.offline && supplier.serverId != null && state.budget != null) { Text("Registrar orden ERP") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 337–350.*

```kotlin
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
```

Validamos el propietario del proveedor y que cantidad por precio no exceda el límite ingresado. Una orden online requiere serverId. Antes del POST redondeamos el importe con amount; sigo ese helper porque forma parte del payload enviado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 414–414.*

```kotlin
    private fun amount(value: Double) = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 115–115.*

```kotlin
    suspend fun createPurchaseOrder(body: PurchaseOrderBody): PurchaseOrder { requireWarehouse(); return api.createPurchaseOrder(body) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 76–76.*

```kotlin
data class PurchaseOrderBody(val proveedorId: Long, val producto: String, val cantidad: String, val total: Double, val usuarioId: Long)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 62–62.*

```kotlin
    @POST("orden-compra") suspend fun createPurchaseOrder(@Body body: PurchaseOrderBody): PurchaseOrder
```

Enviamos la cantidad como texto porque ese es el contrato de PurchaseOrderBody. El precio y el presupuesto no son campos de ese DTO; mandamos el total calculado. La UI consulta el presupuesto ERP y lo muestra, pero el control transaccional de presupuesto debe confirmarse en el servidor.

Al volver recargamos órdenes y presupuesto. Si preparé la compra offline, la rama asDraft inserta Purchase en Room y no reserva fondos. Sigo la persistencia correspondiente.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 7–8.*

```kotlin
@Entity(tableName = "purchases")
data class Purchase(@PrimaryKey val localId: String, val owner: String, val supplierId: String, val supplierName: String, val product: String, val quantity: Int, val unitPrice: Double, val budget: Double, val createdAt: Long)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 21–21.*

```kotlin
    @Insert suspend fun addPurchase(value: Purchase)
```

El envío del borrador es explícito: buscamos el proveedor ERP por identificador o RUC, mandamos la orden y solo después eliminamos la compra local.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 390–397.*

```kotlin
    fun sendPurchase(purchase: Purchase) = run {
        require(purchase.owner == owner()) { "Borrador no válido." }; repo.requireWarehouse()
        val localSupplier = operations.suppliers(owner()).firstOrNull { it.localId == purchase.supplierId }
        val supplier = repo.suppliers().firstOrNull { "erp-${it.id}" == purchase.supplierId || localSupplier?.ruc == it.ruc }
            ?: error("El proveedor debe estar registrado en el ERP. Crea una nueva orden con el proveedor actualizado.")
        repo.createPurchaseOrder(PurchaseOrderBody(supplier.id, purchase.product, purchase.quantity.toString(), amount(purchase.quantity * purchase.unitPrice), repo.requireSession().user.id))
        operations.deletePurchase(purchase.localId, owner()); localData(); mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders()); message("Orden enviada al ERP.")
    }
```

Antes de recibir, el usuario aprueba la orden. La acción salta a purchaseState, delega al repositorio y usa PATCH para cambiar su estado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 162–162.*

```kotlin
                        ConfirmAction("Aprobar orden", "Confirma la aprobación de esta orden de compra.", !state.loading && !state.offline) { vm.purchaseState(order, "Aprobada") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 403–405.*

```kotlin
    fun purchaseState(order: PurchaseOrder, status: String) = run {
        repo.purchaseState(order.id, status); mutable.value = mutable.value.copy(purchaseOrders = repo.purchaseOrders()); message("Estado de compra actualizado.")
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 116–116.*

```kotlin
    suspend fun purchaseState(id: Long, status: String): PurchaseOrder { requireWarehouse(); return api.purchaseState(id, StateBody(status)) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 63–63.*

```kotlin
    @PATCH("orden-compra/{id}/estado") suspend fun purchaseState(@Path("id") id: Long, @Body body: StateBody): PurchaseOrder
```

La recepción se habilita para una orden aprobada o en tránsito que todavía no tenga recepción cargada. Cuando la mercadería llega, presiono Recibir y actualizar stock. Sigo el método invocado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 221–221.*

```kotlin
    }, confirmButton = { TextButton(onClick = { submitted = true; vm.receivePurchase(order, n, p) }, enabled = !state.loading && n > 0 && p > 0 && kotlin.math.abs(n * p - order.total) < .005) { Text("Recibir y actualizar stock") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 406–413.*

```kotlin
    fun receivePurchase(order: PurchaseOrder, quantity: Int, price: Double) = run {
        repo.requireWarehouse(); require(quantity > 0 && price.isFinite() && price > 0 && (quantity * price).isFinite()) { "Cantidad y precio deben ser positivos." }
        require(amount(quantity * price) == amount(order.total)) { "La recepción debe coincidir con el importe de la orden." }
        require(repo.receipts().none { it.ordenCompraId == order.id }) { "Esta orden ya tiene una recepción registrada." }
        repo.receivePurchase(ReceiptBody(order.id, order.proveedorId, mutable.value.inventory.firstOrNull { it.producto.equals(order.producto, true) }?.id,
            order.producto, quantity, price, amount(quantity * price), repo.requireSession().user.id))
        mutable.value = mutable.value.copy(receipts = repo.receipts(), purchaseOrders = repo.purchaseOrders(), inventory = repo.inventory()); message("Compra recibida e inventario actualizado.")
    }
```

Controlamos cantidades y precios positivos, comparamos importes redondeados y consultamos recepciones existentes para evitar registrar otra sobre la misma orden. Esa consulta llega a receipts y después al GET compra.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 112–112.*

```kotlin
    suspend fun receipts(force: Boolean = true): List<Receipt> { requireWarehouse(); return fetch("receipt", force, { api.receipts() }, Receipt::id) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 64–64.*

```kotlin
    @GET("compra") suspend fun receipts(): List<Receipt>
```

Si pasa la validación, construimos ReceiptBody con el producto y, cuando lo encontramos, su inventarioId. Ahora sigo receivePurchase al POST y a la estructura que retorna.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 117–117.*

```kotlin
    suspend fun receivePurchase(body: ReceiptBody): ReceiptResult { requireWarehouse(); return api.receivePurchase(body) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 65–65.*

```kotlin
    @POST("compra") suspend fun receivePurchase(@Body body: ReceiptBody): ReceiptResult
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 79–79.*

```kotlin
data class ReceiptResult(val compra: Receipt, val inventario: InventoryItem, val productoCreado: Boolean, val stockAnterior: Int)
```

El contrato devuelve compra e inventario junto con indicadores de creación de producto y stock anterior. El ViewModel no usa esos valores para hacer un ajuste local: vuelve a consultar recepciones, órdenes e inventario y publica Compra recibida.

Cierro HU08 con proveedores validados, órdenes, borradores manuales, cambio de estado y recepción conectada a la recarga de inventario. La consulta de duplicados en cliente ayuda, pero no garantiza exclusión bajo concurrencia; eso necesita restricciones y transacciones del servidor. La cola local no se envía automáticamente. Ahora voy a capturar el documento que respalda una operación.

## Persona 2 · Sprint 3 · HU09: Captura de documentos, OCR y códigos

HU09 reduce la digitación de facturas y permite leer códigos QR o de barras. El recorrido comienza en Fotografiar documento o código. Creamos un destino local y pedimos a Android capturar una imagen; también podemos elegirla desde la galería.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Documents.kt`, líneas 61–64.*

```kotlin
                    val file = File(context.cacheDir, "capturas/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    captureUri = uri.toString()
                    runCatching { camera.launch(uri) }.onFailure { error = "No hay una aplicación de cámara disponible. Elige una imagen." }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Documents.kt`, líneas 50–51.*

```kotlin
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if(success && captureUri.isNotBlank()) process(Uri.parse(captureUri)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if(uri != null) process(uri) }
```

Cuando TakePicture retorna éxito, ejecutamos process con la URI. Dentro de una corrutina llamamos DocumentScanner.scan. Sigo el servicio antes de volver al formulario.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Documents.kt`, líneas 38–44.*

```kotlin
    fun process(uri: Uri) { scope.launch {
        processing = true; error = ""
        try {
            val result = DocumentScanner.scan(context, uri)
            imagePath = result.imagePath; ruc = result.invoice.ruc; number = result.invoice.number
            date = result.invoice.date.replace('-', '/').replace('.', '/')
            val parts = date.split('/')
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/DocumentScanner.kt`, líneas 20–28.*

```kotlin
    suspend fun scan(context: Context, uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "No se pudo leer la imagen. Prueba otra fotografía." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("No se pudo abrir la imagen.")
        val exif = context.contentResolver.openInputStream(uri).use { stream -> stream?.let { androidx.exifinterface.media.ExifInterface(it) } }
```

Leemos las dimensiones, rechazamos imágenes inválidas y reducimos el tamaño de decodificación cuando la dimensión mayor supera el límite usado para el muestreo. Después leemos EXIF para aplicar giro y reflejo; así el reconocimiento y la imagen guardada conservan la misma orientación.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/DocumentScanner.kt`, líneas 29–39.*

```kotlin
        val transform = android.graphics.Matrix().apply {
            if(exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        if(bitmap !== decoded) decoded.recycle()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val barcode = BarcodeScanning.getClient()
        try {
            // OCR and saved JPEG share the same orientation and bounded memory usage.
            val input = InputImage.fromBitmap(bitmap, 0)
```

Dentro de scan creamos el reconocedor de texto y el de códigos ML Kit. Procesamos el bitmap, obtenemos el texto y los valores de los códigos y comprimimos la imagen a JPEG con calidad ochenta.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/DocumentScanner.kt`, líneas 40–46.*

```kotlin
            val text = recognizer.process(input).await().text
            val codes = barcode.process(input).await().mapNotNull { it.rawValue }.joinToString("\n")
            val file = File(context.filesDir, "documentos/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            ScanResult(file.absolutePath, InvoiceParser.parse(text), codes, text)
        } finally { bitmap.recycle(); recognizer.close(); barcode.close() }
    }
```

La última línea invoca InvoiceParser.parse; sigo esa llamada antes de explicar la vuelta a la pantalla. El parser busca RUC, número, fecha e importe total dentro del texto reconocido.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 27–37.*

```kotlin
data class ExtractedInvoice(val ruc: String = "", val number: String = "", val date: String = "", val amount: String = "")
object InvoiceParser {
    fun parse(text: String): ExtractedInvoice {
        val ruc = Regex("\\b\\d{11}\\b").findAll(text).map { it.value }.firstOrNull(BusinessRules::validRuc).orEmpty()
        val number = Regex("\\b[A-Z]\\d{3}\\s*[-–]\\s*\\d{1,12}\\b", RegexOption.IGNORE_CASE).find(text)?.value.orEmpty()
        val date = Regex("\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}\\b").find(text)?.value.orEmpty()
        val rawAmount = Regex("\\b(?:TOTAL(?: A PAGAR)?|IMPORTE TOTAL)\\s*[:]?\\s*(?:S/\\.?|PEN)?\\s*([0-9]{1,3}(?:[, .][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2})(?=\\s|$)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1).orEmpty()
        val amount = if(rawAmount.length >= 4) rawAmount.dropLast(3).filter(Char::isDigit) + "." + rawAmount.takeLast(2) else ""
        return ExtractedInvoice(ruc, number, date, amount)
    }
}
```

La selección de RUC vuelve a usar validRuc. Lo que hemos mostrado es OCR de ML Kit seguido por extracción con expresiones regulares; no es un modelo generativo que interprete toda la factura. En scan, finally libera el bitmap y cierra los dos clientes.

Regresamos con ScanResult. process propone los campos y normaliza separadores de fecha, pero el usuario conserva el control: puede revisar la imagen y corregir antes de confirmar. El botón Confirmar factura entrega los datos revisados a saveDocument.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Documents.kt`, líneas 98–98.*

```kotlin
    }, confirmButton = { Column { TextButton(onClick = { submitted = true; vm.saveDocument(imagePath, ruc, number, date, BusinessRules.positiveAmount(amount) ?: 0.0, codes) }, enabled = !state.loading && BusinessRules.validRuc(ruc) && number.isNotBlank() && date.isNotBlank() && BusinessRules.positiveAmount(amount) != null) { Text("Confirmar factura") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 363–370.*

```kotlin
    fun saveDocument(path: String, ruc: String, number: String, date: String, amount: Double, code: String) = run {
        require(BusinessRules.validRuc(ruc) && number.isNotBlank()) { "Revisa RUC y número del documento." }
        require(runCatching { java.time.LocalDate.parse(date, java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.isSuccess) { "Usa una fecha válida: dd/MM/aaaa." }
        require(amount.isFinite() && amount > 0) { "Ingresa un monto válido." }
        require(java.io.File(path).exists()) { "Vuelve a capturar el documento." }
        operations.addDocument(ScannedDocument(id(), owner(), path, ruc, number, date, amount, code, System.currentTimeMillis()))
        localData(); message("Documento revisado y guardado en el dispositivo.")
    }
```

Validamos otra vez RUC y número, exigimos una fecha real con formato estricto, monto positivo y archivo existente. Después delegamos en addDocument; sigo esa inserción y su entidad Room.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 11–12.*

```kotlin
@Entity(tableName = "documents")
data class ScannedDocument(@PrimaryKey val localId: String, val owner: String, val imagePath: String, val ruc: String, val number: String, val date: String, val amount: Double, val code: String, val createdAt: Long, val storagePath: String? = null)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 25–25.*

```kotlin
    @Insert suspend fun addDocument(value: ScannedDocument)
```

Guardamos la ruta de imagen y los campos en SQLite, asociados al usuario ERP. Después localData vuelve a leerlos y la pantalla muestra el documento. Si solo encontramos un código, saveCode usa la misma entidad, sin inventar RUC ni monto de una factura.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 371–375.*

```kotlin
    fun saveCode(path: String, code: String) = run {
        require(code.isNotBlank() && java.io.File(path).exists()) { "Captura una imagen con un código legible." }
        operations.addDocument(ScannedDocument(id(), owner(), path, "", "Código escaneado", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")), 0.0, code, System.currentTimeMillis()))
        localData(); message("Código revisado y guardado en el dispositivo.")
    }
```

La subida a Firebase es una acción independiente del guardado. El botón Subir imagen a Firebase invoca uploadDocument. Sigo esa llamada desde la comprobación de propietario hasta la escritura remota.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Documents.kt`, líneas 80–80.*

```kotlin
                OutlinedButton(onClick = { vm.uploadDocument(document) }, enabled = !state.loading && state.firebaseEmail != null && document.storagePath == null, modifier = Modifier.fillMaxWidth()) { Text("Subir imagen a Firebase") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 376–383.*

```kotlin
    fun uploadDocument(document: ScannedDocument) = run {
        require(document.owner == owner()) { "Documento no válido." }
        val uid = auth?.currentUser?.uid ?: error("Inicia sesión en Firebase para subir documentos.")
        val path = "usuarios/$uid/documentos/${document.localId}.jpg"
        com.google.firebase.storage.FirebaseStorage.getInstance().reference.child(path)
            .putFile(android.net.Uri.fromFile(java.io.File(document.imagePath)), com.google.firebase.storage.StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        operations.markUploaded(document.localId, owner(), path); localData(); message("Imagen subida a Firebase Storage.")
    }
```

Usamos el uid Firebase para la ruta usuarios/uid/documentos/id.jpg y declaramos image/jpeg. Solo después de que putFile finaliza invocamos markUploaded. Sigo ese cambio para ver cómo se registra el resultado en la base local.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 26–26.*

```kotlin
    @Query("UPDATE documents SET storagePath=:path WHERE localId=:id AND owner=:owner") suspend fun markUploaded(id: String, owner: String, path: String)
```

La query actualiza storagePath únicamente para el documento y su propietario. Después recargamos los datos y el estado visual pasa de En dispositivo a En Firebase.

Para cerrar este recorrido debo mostrar una dependencia pendiente: las reglas Storage que incluye el repositorio habilitan PDFs en ventas, pero no declaran la ruta documentos. No basta con tener putFile escrito para considerar autorizada esa subida.

*En pantalla: `firebase.storage.rules`, líneas 1–12.*

```text
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /usuarios/{uid}/ventas/{fileName} {
      allow read: if request.auth != null && request.auth.uid == uid;
      allow write: if request.auth != null
        && request.auth.uid == uid
        && request.resource.size < 10 * 1024 * 1024
        && request.resource.contentType == 'application/pdf';
    }
  }
}
```

Cierro HU09 con captura por cámara o galería, OCR local, lectura de códigos, compresión, revisión humana y persistencia Room. La llamada de subida y el registro local de su resultado existen, pero con estas reglas la subida de JPEG a documentos no queda habilitada. Esa configuración y su prueba son pendientes concretos de integración; no debemos presentar el almacenamiento remoto de imágenes como una capacidad ya validada.

## Tu cierre

## Cierre de la exposición

Con este recorrido completamos las nueve historias de los tres primeros sprints. En el primero establecimos arquitectura, identidad y continuidad de consulta; en el segundo conectamos cotizaciones, pedidos, ventas y constancias; en el tercero extendimos la operación a stock, abastecimiento y captura documental.

Lo que mostramos tiene una trazabilidad concreta: el evento llega a la coordinación, la coordinación valida y delega, el dato se consulta o persiste y el resultado vuelve al estado observado por Compose. Para el equipo técnico, esa separación permite ubicar tanto una regla como una dependencia. Para los stakeholders, permite distinguir preparar una operación de confirmarla en el ERP y conocer qué resultado obtiene el usuario.

Los siguientes cierres necesarios están identificados: idempotencia para sincronizar escrituras automáticamente, validación integrada de las transacciones ERP y de la secuencia aprobación–conversión–venta, emisión tributaria si exigimos boleta o factura, registro y emisión de FCM en servidor, y reglas Storage que permitan imágenes documentales. También necesitamos ejecución y pruebas integradas en Android; este guion verifica fuentes y llamadas, no certifica una demo realizada.

Con eso termino la presentación. Podemos pasar a ejecutar estos recorridos con cuentas de prueba y contrastar las respuestas del ERP, la persistencia local y los resultados de Firebase.

### Notas para conducir la demo

Antes de comenzar, preparar cuentas ERP con los roles usados y, cuando corresponda, accesos Firebase compatibles con el flujo unificado; configurar la URL ERP y conectividad. Para probar offline, cargar primero datos en línea y después cortar conexión. Una solicitud guardada no debe confundirse con stock modificado ni un borrador con una operación ERP confirmada.

Mostrar el PDF como constancia informativa. Mostrar FCM como receptor disponible y las alertas periódicas como notificaciones locales. Para HU09, demostrar captura y guardado local; presentar la subida como pendiente si se utilizan las reglas incluidas. Ante una respuesta perdida de escritura, comprobar el listado antes de reenviar. Estas indicaciones mantienen la demo alineada con el alcance descrito.
