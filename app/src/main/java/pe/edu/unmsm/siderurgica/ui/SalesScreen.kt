@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package pe.edu.unmsm.siderurgica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.*
import pe.edu.unmsm.siderurgica.data.*
import pe.edu.unmsm.siderurgica.domain.BusinessRules

private data class Confirmation(val title: String, val body: String, val action: () -> Unit)
@Composable fun SalesScreen(vm: ErpViewModel, state: UiState, tab: Int, onTab: (Int) -> Unit, onCreate: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable(tab) { mutableStateOf("Todos") }
    var confirm by remember { mutableStateOf<Confirmation?>(null) }
    val quotes = state.quotes.filter { matches(search, it.cliente, it.codigo, it.producto) && (filter == "Todos" || it.estado == filter) }
    val orders = state.orders.filter { matches(search, it.cliente, it.codigo, it.producto) && (filter == "Todos" || it.estado == filter) }
    val sales = state.sales.filter { matches(search, it.cliente, it.codigo, it.producto) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Cotizaciones", "Pedidos", "Ventas").forEachIndexed { i, text -> Tab(selected = tab == i, onClick = { onTab(i); search = "" }, text = { Text(text) }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(listOf("Tus cotizaciones", "Tus pedidos", "Ventas registradas")[tab], listOf("Prepara propuestas y conviértelas en pedidos.", "Gestiona la aprobación y la venta.", "Consulta y comparte constancias informativas.")[tab])
                    if(tab != 2) Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !state.loading && BusinessRules.canSell(state.session?.user?.rol.orEmpty())) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if(tab == 0) "Nueva cotización" else "Nuevo pedido")
                    }
                    SearchField(search, { search = it })
                    if(tab != 2) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val states = if(tab == 0) listOf("Todos", "PENDIENTE", "APROBADA", "RECHAZADA", "CONVERTIDA") else listOf("Todos", "PENDIENTE", "APROBADO", "RECHAZADO", "FACTURADO", "ANULADO")
                        states.forEach { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s.lowercase().replaceFirstChar { it.uppercase() }) }) }
                    }
                    Text("${listOf(quotes.size, orders.size, sales.size)[tab]} resultados", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when(tab) {
                0 -> {
                    if(quotes.isEmpty()) item { EmptyState(if(search.isBlank() && filter == "Todos") "Aún no hay cotizaciones" else "Sin coincidencias", "Crea una propuesta o ajusta tus filtros.") }
                    items(quotes, key = { "q${it.id}" }) { quote ->
                        CommercialCard(quote.codigo ?: "Cotización #${quote.id}", quote.cliente, quote.producto, quote.cantidad, quote.total ?: 0.0, quote.estado,
                            details = { DetailLine("RUC", quote.ruc ?: "No registrado"); DetailLine("Precio unitario", money(quote.precioUnitario)); DetailLine("Descuento", "${quote.descuentoPorcentaje ?: 0}%") },
                            actions = {
                                if(quote.estado == "PENDIENTE") Button(onClick = { confirm = Confirmation("¿Aprobar cotización?", "${quote.cliente} · ${money(quote.total ?: 0.0)}. Después podrás crear su pedido.") { vm.approveQuote(quote.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Aprobar cotización") }
                                if(quote.estado == "APROBADA") Button(onClick = { confirm = Confirmation("¿Crear pedido?", "Se convertirá esta cotización aprobada en un pedido.") { vm.convertQuote(quote.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Crear pedido") }
                                if(quote.estado == "PENDIENTE") OutlinedButton(onClick = { confirm = Confirmation("¿Rechazar cotización?", "La propuesta pasará a estado rechazada.") { vm.rejectQuote(quote.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Rechazar cotización") }
                            })
                    }
                }
                1 -> {
                    if(orders.isEmpty()) item { EmptyState("No hay pedidos para mostrar", "Crea un pedido o aprueba una cotización.") }
                    items(orders, key = { "o${it.id}" }) { order ->
                        CommercialCard(order.codigo ?: "Pedido #${order.id}", order.cliente, order.producto, order.cantidad, order.total ?: 0.0, order.estado,
                            details = { DetailLine("RUC", order.ruc ?: "No registrado"); DetailLine("Subtotal", money(order.subtotal ?: 0.0)); DetailLine("IGV", money(order.igv ?: 0.0)); DetailLine("Stock", order.estadoStock ?: "Consultar inventario") },
                            actions = { if(order.estado == "PENDIENTE") {
                                Button(onClick = { confirm = Confirmation("¿Aprobar pedido?", "El ERP validará y descontará el stock para el pedido de ${order.cliente}. La venta se genera después.") { vm.orderState(order.id, "APROBADO") } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Aprobar pedido") }
                                OutlinedButton(onClick = { confirm = Confirmation("¿Anular pedido?", "Este pedido dejará de continuar hacia la venta.") { vm.orderState(order.id, "ANULADO") } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Anular pedido") }
                                OutlinedButton(onClick = { confirm = Confirmation("¿Rechazar pedido?", "El pedido pasará a estado rechazado.") { vm.orderState(order.id, "RECHAZADO") } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Rechazar pedido") }
                            }
                                if(order.estado == "APROBADO") Button(onClick = { confirm = Confirmation("¿Generar venta?", "Se registrará la venta y el pedido quedará facturado.") { vm.sale(order.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Generar venta") }
                            })
                    }
                }
                else -> {
                    if(sales.isEmpty()) item { EmptyState("Aún no hay ventas", "Genera una venta desde el detalle de un pedido aprobado.") }
                    items(sales, key = { "s${it.id}" }) { sale -> SaleCard(sale, state) }
                }
            }
        }
    }
    confirm?.let { value -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(value.title) }, text = { Text(value.body) }, confirmButton = { TextButton(onClick = { confirm = null; value.action() }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }) }
}
private fun matches(search: String, vararg values: String?) = search.isBlank() || values.any { it?.contains(search.trim(), true) == true }

@Composable private fun CommercialCard(code: String, client: String, product: String, quantity: Int, total: Double, status: String?, details: @Composable ColumnScope.() -> Unit, actions: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(code) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(code, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                StatusBadge(status)
            }
            Text(client, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("$product · $quantity unidades", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if(expanded) "Ocultar detalle" else "Ver detalle") }
            if(expanded) { HorizontalDivider(); details(); actions() }
        }
    }
}

@Composable private fun SaleCard(sale: Sale, state: UiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    fun pdfAction(preview: Boolean) {
        runCatching {
            val file = SalePdf.create(context, sale)
            if(!preview) SalePdf.share(context, file) else {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }.onFailure { result = "No se pudo abrir el PDF. Instala un visor o usa Compartir." }
    }
    CommercialCard(sale.codigo ?: "Venta #${sale.id}", sale.cliente, sale.producto, sale.cantidad, sale.total, sale.estado,
        details = { DetailLine("Subtotal", money(sale.subtotal)); DetailLine("IGV", money(sale.igv)); Text("Constancia informativa; no sustituye una boleta o factura tributaria.", style = MaterialTheme.typography.bodySmall) },
        actions = {
            OutlinedButton(onClick = { pdfAction(true) }, modifier = Modifier.fillMaxWidth()) { Text("Vista previa PDF") }
            Button(onClick = { pdfAction(false) }, modifier = Modifier.fillMaxWidth()) { Text("Compartir constancia") }
            OutlinedButton(onClick = { scope.launch {
                uploading = true
                try { SalePdf.upload(context, sale, SalePdf.create(context, sale)); result = "Constancia subida a Firebase." }
                catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; result = "No se pudo subir. Revisa tu sesión Firebase y la conexión." }
                finally { uploading = false }
            } }, enabled = !uploading && state.firebaseEmail != null, modifier = Modifier.fillMaxWidth()) { Text(if(uploading) "Subiendo…" else "Subir a Firebase") }
            if(result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)
        })
}

@Composable fun CommercialForm(vm: ErpViewModel, state: UiState, quote: Boolean, onDone: () -> Unit) {
    var client by rememberSaveable { mutableStateOf("") }; var ruc by rememberSaveable { mutableStateOf("") }
    var product by rememberSaveable { mutableStateOf("") }; var quantity by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }; var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var productsMenu by remember { mutableStateOf(false) }; var clientsMenu by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val n = quantity.toIntOrNull() ?: 0; val unit = BusinessRules.positiveAmount(price) ?: 0.0
    val subtotal = n * unit; val discount = if(quote) BusinessRules.discount(n) else 0
    val base = subtotal * (100 - discount) / 100; val tax = base * .18
    val selected = state.inventory.firstOrNull { it.id == selectedId }
    val available = selected?.stockDisponible ?: selected?.stock
    val valid = client.isNotBlank() && product.isNotBlank() && n > 0 && unit > 0 && (ruc.isBlank() || BusinessRules.validRuc(ruc))
    LaunchedEffect(state.message, state.loading) {
        if(submitted && !state.loading && state.message in listOf("Cotización registrada.", "Pedido registrado.", "Borrador guardado.")) onDone()
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle(if(quote) "Prepara una propuesta" else "Registra un pedido", "Completa los datos y revisa el resumen antes de confirmar.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("1. Cliente", style = MaterialTheme.typography.titleMedium)
                Field(client, { client = it }, "Nombre o razón social")
                Box {
                    TextButton(onClick = { clientsMenu = true }) { Text("Elegir un cliente de operaciones anteriores") }
                    DropdownMenu(clientsMenu, { clientsMenu = false }, Modifier.heightIn(max = 260.dp)) {
                        val known = (state.quotes.map { it.cliente to it.ruc } + state.orders.map { it.cliente to it.ruc }).distinctBy { it.first }
                        if(known.isEmpty()) DropdownMenuItem(text = { Text("Todavía no hay clientes cargados") }, onClick = { clientsMenu = false })
                        known.forEach { (name, value) -> DropdownMenuItem(text = { Text(name) }, onClick = { client = name; ruc = value.orEmpty(); clientsMenu = false }) }
                    }
                }
                Field(ruc, { ruc = it.filter(Char::isDigit).take(11) }, "RUC (opcional)", true, ruc.isNotBlank() && !BusinessRules.validRuc(ruc), "11 dígitos; revisaremos el dígito verificador.")
                HorizontalDivider(); Text("2. Producto", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(onClick = { productsMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar del inventario") }
                    DropdownMenu(productsMenu, { productsMenu = false }, Modifier.heightIn(max = 280.dp)) {
                        if(state.inventory.isEmpty()) DropdownMenuItem(text = { Text("No hay inventario cargado") }, onClick = { productsMenu = false })
                        state.inventory.forEach { item -> DropdownMenuItem(text = { Column { Text(item.producto ?: "Producto #${item.id}"); Text("Disponible: ${item.stockDisponible ?: item.stock ?: "sin dato"}", style = MaterialTheme.typography.bodySmall) } }, onClick = { selectedId = item.id; product = item.producto.orEmpty(); price = item.precioUnitario?.toString().orEmpty(); productsMenu = false }) }
                    }
                }
                Field(product, { product = it; selectedId = null }, "Producto")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { Field(quantity, { quantity = it.filter(Char::isDigit).take(8) }, "Cantidad", true) }
                    Column(Modifier.weight(1f)) { Field(price, { price = it }, "Precio unitario", true) }
                }
                if(selected != null) InfoCard("Stock consultado: ${available ?: "sin dato"} unidades.${if(available != null && n > available) " La cantidad supera el disponible." else ""}", available != null && n > available)
                Text("Un producto por operación. La disponibilidad final la valida el ERP.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("3. Resumen estimado", style = MaterialTheme.typography.titleMedium)
                    DetailLine("Subtotal", money(subtotal)); if(quote) DetailLine("Descuento por cantidad", "$discount%")
                    DetailLine("Base después del descuento", money(base)); DetailLine("IGV referencial (18%)", money(tax))
                    HorizontalDivider(); DetailLine(if(quote) "Total cotización (sin IGV)" else "Total pedido con IGV", money(if(quote) base else base + tax))
                    Text("El servidor calcula el importe definitivo. Esta vista no cambia su política tributaria ni de descuentos.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            fun submit(draft: Boolean) {
                submitted = true
                if(quote) vm.saveQuote(QuoteCreate(client.trim(), ruc.ifBlank { null }, product.trim(), n, selectedId, unit, state.session!!.user.id), draft)
                else vm.createOrder(Order(cliente = client.trim(), ruc = ruc.ifBlank { null }, producto = product.trim(), cantidad = n, inventarioId = selectedId, precioUnitario = unit, usuarioId = state.session!!.user.id), draft)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { submit(false) }, enabled = valid && !state.loading && !state.offline && (quote || available == null || n <= available), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Confirmar ${if(quote) "cotización" else "pedido"}") }
                OutlinedButton(onClick = { submit(true) }, enabled = valid && !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Guardar como borrador") }
                if(state.offline) Text("Actualiza para confirmar en línea, o guarda un borrador.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable fun DraftScreen(vm: ErpViewModel, state: UiState) {
    var selected by remember { mutableStateOf<Draft?>(null) }
    var delete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { InfoCard("Los borradores requieren confirmación. Si el servidor respondió tarde, revisa los listados antes de reenviar para evitar duplicados.", true) }
        if(state.drafts.isEmpty()) item { EmptyState("No tienes borradores", "Aquí aparecerán las cotizaciones y pedidos guardados.") }
        items(state.drafts, key = { it.localId }) { draft ->
            val content = runCatching { com.google.gson.Gson().fromJson(draft.json, Quote::class.java) }.getOrNull()
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if(draft.kind == "QUOTE") "Cotización pendiente" else "Pedido pendiente", style = MaterialTheme.typography.labelLarge)
                    Text(content?.cliente ?: "Borrador", style = MaterialTheme.typography.titleMedium)
                    Text("${content?.producto.orEmpty()} · ${content?.cantidad ?: 0} unidades")
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(draft.createdAt)), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { selected = draft; delete = false }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Revisar y enviar") }
                    TextButton(onClick = { selected = draft; delete = true }, enabled = !state.loading) { Text("Eliminar borrador") }
                }
            }
        }
    }
    selected?.let { draft -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(if(delete) "¿Eliminar borrador?" else "¿Enviar al ERP?") }, text = { Text(if(delete) "Se eliminará únicamente este borrador local." else "Confirma que no exista ya esta operación en el ERP. Este backend no ofrece reintentos automáticos seguros.") }, confirmButton = { TextButton(onClick = { selected = null; if(delete) vm.discardDraft(draft) else vm.sendDraft(draft) }) { Text(if(delete) "Eliminar" else "Confirmar envío") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancelar") } }) }
}
