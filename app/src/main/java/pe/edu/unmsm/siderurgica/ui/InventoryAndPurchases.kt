package pe.edu.unmsm.siderurgica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.edu.unmsm.siderurgica.*
import pe.edu.unmsm.siderurgica.data.*
import pe.edu.unmsm.siderurgica.domain.BusinessRules

@Composable fun InventoryScreen(vm: ErpViewModel, state: UiState) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var movement by remember { mutableStateOf<InventoryItem?>(null) }
    val items = state.inventory.filter { (search.isBlank() || it.producto?.contains(search, true) == true || it.id.toString() == search) && (filter == "Todos" || BusinessRules.stockLevel(it.stockDisponible ?: it.stock, it.stockMinimo ?: 10) == filter) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Disponibilidad de productos", "Consulta el stock antes de comprometer una venta.")
                SearchField(search, { search = it }, "Buscar producto o identificador")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Normal", "Bajo", "Agotado", "Sin dato").forEach { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) }) }
                }
                Text("Las alertas usan el stock mínimo configurado para cada producto en el ERP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${items.size} productos", style = MaterialTheme.typography.labelLarge)
            }
        }
        if(items.isEmpty()) item { EmptyState("No hay productos para mostrar", "Actualiza el inventario o ajusta la búsqueda.") }
        items(items, key = { it.id }) { item ->
            val available = item.stockDisponible ?: item.stock
            OutlinedCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(item.producto ?: "Producto #${item.id}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        StatusBadge(BusinessRules.stockLevel(available, item.stockMinimo ?: 10))
                    }
                    Text("${available ?: "—"} unidades disponibles", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    DetailLine("Stock total", item.stock?.toString() ?: "Sin dato")
                    DetailLine("Precio unitario", item.precioUnitario?.let(::money) ?: "Sin dato")
                    OutlinedButton(onClick = { movement = item }, modifier = Modifier.fillMaxWidth(), enabled = !state.loading && BusinessRules.canWarehouse(state.session?.user?.rol.orEmpty())) { Text("Registrar movimiento") }
                }
            }
        }
    }
    movement?.let { item -> MovementDialog(vm, state, item) { movement = null } }
}
@Composable private fun MovementDialog(vm: ErpViewModel, state: UiState, item: InventoryItem, dismiss: () -> Unit) {
    var kind by remember { mutableStateOf("ENTRADA") }; var quantity by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.message, state.loading) { if(submitted && !state.loading && (state.message.startsWith("Solicitud guardada") || state.message.startsWith("Movimiento registrado"))) dismiss() }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Movimiento de stock") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.producto.orEmpty())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ENTRADA", "SALIDA", "AJUSTE").forEach { s -> FilterChip(kind == s, { kind = s }, label = { Text(s.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            Field(quantity, { quantity = it.filter(Char::isDigit).take(8) }, if(kind == "AJUSTE") "Stock objetivo" else "Cantidad", true)
            Field(reason, { reason = it }, "Motivo del movimiento")
            InfoCard("Confirmar actualizará el stock real. Puedes guardar una solicitud local si no tienes conexión. El motivo se conserva en la solicitud local; la API no acepta un campo de motivo.", true)
        }
    }, confirmButton = { Column {
        val valid = (quantity.toIntOrNull()?.let { it > 0 || kind == "AJUSTE" && it == 0 } == true) && reason.isNotBlank()
        TextButton(onClick = { submitted = true; vm.saveMovement(item, kind, quantity.toIntOrNull() ?: 0, reason) }, enabled = valid && !state.loading && !state.offline) { Text("Confirmar en ERP") }
        TextButton(onClick = { submitted = true; vm.saveMovement(item, kind, quantity.toIntOrNull() ?: 0, reason, true) }, enabled = valid && !state.loading) { Text("Guardar solicitud local") }
    } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
@Composable fun MovementScreen(vm: ErpViewModel, state: UiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { InfoCard("Estas solicitudes todavía no han cambiado el stock. Actualiza el inventario y confirma cada envío; no se reenvían automáticamente.", true) }
        if(state.movements.isEmpty()) item { EmptyState("Sin solicitudes de stock", "Prepara una entrada, salida o ajuste desde Inventario.") }
        items(state.movements, key = { it.localId }) { item -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(item.kind); Text(item.product, style = MaterialTheme.typography.titleMedium)
                DetailLine(if(item.kind == "AJUSTE") "Stock objetivo" else "Cantidad", item.quantity.toString())
                Text(item.reason); Text("Guardado en el dispositivo", style = MaterialTheme.typography.labelMedium)
                ConfirmAction("Enviar movimiento", "Se cambiará el stock real. Comprueba que esta operación no se haya registrado antes.", !state.loading && !state.offline) { vm.sendMovement(item) }
            }
        } }
    }
}
@Composable fun SupplierScreen(vm: ErpViewModel, state: UiState) {
    var search by rememberSaveable { mutableStateOf("") }; var form by rememberSaveable { mutableStateOf(false) }
    val suppliers = state.suppliers.filter { search.isBlank() || it.name.contains(search, true) || it.ruc.contains(search) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Tu red de proveedores", "Organiza contactos para preparar tus compras.")
                InfoCard("Consulta proveedores del ERP y guarda nuevos registros o borradores locales.", true)
                Button(onClick = { form = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nuevo proveedor") }
                SearchField(search, { search = it }, "Buscar razón social o RUC")
            }
        }
        if(suppliers.isEmpty()) item { EmptyState("Sin proveedores para mostrar", "Registra una razón social y su RUC.", Icons.Default.Person) }
        items(suppliers, key = { it.localId }) { item -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge); DetailLine("RUC", item.ruc)
                if(item.contact.isNotBlank()) DetailLine("Contacto", item.contact)
                if(item.phone.isNotBlank()) DetailLine("Teléfono", item.phone)
                if(item.email.isNotBlank()) Text(item.email, style = MaterialTheme.typography.bodyMedium)
                StatusBadge(if(item.serverId == null) "Borrador local" else "Registrado en ERP")
                if(item.serverId == null) ConfirmAction("Enviar proveedor", "Confirma el registro de este proveedor en el ERP.", !state.loading && !state.offline) { vm.sendSupplier(item) }
            }
        } }
    }
    if(form) SupplierDialog(vm, state) { form = false }
}

@Composable private fun SupplierDialog(vm: ErpViewModel, state: UiState, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var ruc by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }; var phone by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.message, state.loading) { if(submitted && !state.loading && (state.message.startsWith("Proveedor guardado") || state.message.startsWith("Proveedor registrado"))) dismiss() }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Nuevo proveedor") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(name, { name = it }, "Razón social")
            Field(ruc, { ruc = it.filter(Char::isDigit).take(11) }, "RUC", true, ruc.isNotBlank() && !BusinessRules.validRuc(ruc))
            Field(contact, { contact = it }, "Persona de contacto (opcional)")
            Field(phone, { phone = it }, "Teléfono (opcional)")

        }
    }, confirmButton = { Column {
        val valid = name.isNotBlank() && BusinessRules.validRuc(ruc)
        TextButton(onClick = { submitted = true; vm.saveSupplier(name, ruc, contact, phone, "") }, enabled = valid && !state.loading && !state.offline) { Text("Registrar en ERP") }
        TextButton(onClick = { submitted = true; vm.saveSupplier(name, ruc, contact, phone, "", true) }, enabled = valid && !state.loading) { Text("Guardar borrador") }
    } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
@Composable fun PurchaseScreen(vm: ErpViewModel, state: UiState) {
    var form by rememberSaveable { mutableStateOf(false) }
    var receiving by remember { mutableStateOf<PurchaseOrder?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Planifica el abastecimiento", "Compra con cantidades y presupuesto claros.")
                InfoCard("Orden → Aprobación → Recepción. La recepción registra la compra y aumenta el stock; no confirma una entrega hasta recibirla.", true)
                Button(onClick = { form = true }, enabled = state.suppliers.isNotEmpty() && !state.loading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Preparar compra") }
                if(state.suppliers.isEmpty()) Text("Primero registra un proveedor desde Más → Proveedores.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            state.budget?.let { budget -> InfoCard("Presupuesto ${budget.periodo}: ${money(budget.montoDisponible)} disponibles.") }
                ?: InfoCard("Presupuesto no disponible. El servidor debe tener un presupuesto mensual configurado para registrar órdenes.", true)
        }
        items(state.purchaseOrders, key = { "erp-${it.id}" }) { order ->
            val received = state.receipts.any { it.ordenCompraId == order.id }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(order.codigo ?: "Orden #${order.id}", style = MaterialTheme.typography.labelLarge)
                    StatusBadge(if(received) "Recibida" else order.estado)
                    Text(state.suppliers.firstOrNull { it.serverId == order.proveedorId }?.name ?: "Proveedor #${order.proveedorId}", style = MaterialTheme.typography.titleMedium)
                    Text("${order.producto} · ${order.cantidad ?: "Cantidad sin dato"}")
                    DetailLine("Importe de la orden", money(order.total))
                    if(!received && order.estado == "Pendiente") {
                        ConfirmAction("Aprobar orden", "Confirma la aprobación de esta orden de compra.", !state.loading && !state.offline) { vm.purchaseState(order, "Aprobada") }
                        ConfirmAction("Rechazar orden", "El ERP liberará el importe comprometido de esta orden.", !state.loading && !state.offline) { vm.purchaseState(order, "Rechazada") }
                    }
                    if(!received && order.estado in listOf("Aprobada", "En tránsito")) Button(onClick = { receiving = order }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Registrar recepción") }
                }
            }
        }
        if(state.purchases.isEmpty() && state.purchaseOrders.isEmpty()) item { EmptyState("Sin compras preparadas", "Tus borradores estarán disponibles sin conexión.", Icons.Default.ShoppingCart) }
        items(state.purchases, key = { it.localId }) { item -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge("Borrador local"); Text(item.supplierName, style = MaterialTheme.typography.titleLarge)
                Text("${item.product} · ${item.quantity} unidades")
                DetailLine("Importe de la orden", money(item.quantity * item.unitPrice)); DetailLine("Presupuesto", money(item.budget))
                ConfirmAction("Enviar orden", "El ERP reservará presupuesto. Revisa que no exista ya esta orden antes de enviar.", !state.loading && !state.offline) { vm.sendPurchase(item) }
            }
        } }
    }
    if(form) PurchaseDialog(vm, state) { form = false }
    receiving?.let { ReceiptDialog(vm, state, it) { receiving = null } }
}
@Composable private fun PurchaseDialog(vm: ErpViewModel, state: UiState, dismiss: () -> Unit) {
    var supplier by remember { mutableStateOf(state.suppliers.first()) }; var menu by remember { mutableStateOf(false) }
    var product by rememberSaveable { mutableStateOf("") }; var qty by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }; var budget by rememberSaveable { mutableStateOf(state.budget?.montoDisponible?.toString().orEmpty()) }
    var submitted by remember { mutableStateOf(false) }
    val n = qty.toIntOrNull() ?: 0; val p = BusinessRules.positiveAmount(price) ?: 0.0; val b = BusinessRules.positiveAmount(budget) ?: 0.0
    val total = n * p
    LaunchedEffect(state.message, state.loading) { if(submitted && !state.loading && (state.message.startsWith("Borrador de compra guardado") || state.message.startsWith("Orden de compra registrada"))) dismiss() }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Preparar compra") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(supplier.name) }
                DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 240.dp)) { state.suppliers.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { supplier = s; menu = false }) } }
            }
            Field(product, { product = it }, "Producto"); Field(qty, { qty = it.filter(Char::isDigit).take(8) }, "Cantidad", true)
            Field(price, { price = it }, "Precio unitario", true); Field(budget, { budget = it }, "Límite de presupuesto", true, b > 0 && total > b)
            DetailLine("Importe de la orden", money(total))
            if(b > 0 && total > b) Text("El total supera el presupuesto.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Column {
        val valid = product.isNotBlank() && n > 0 && p > 0 && b >= total
        TextButton(onClick = { submitted = true; vm.savePurchase(supplier, product, n, p, b) }, enabled = valid && !state.loading && !state.offline && supplier.serverId != null && state.budget != null) { Text("Registrar orden ERP") }
        TextButton(onClick = { submitted = true; vm.savePurchase(supplier, product, n, p, b, true) }, enabled = valid && !state.loading) { Text("Guardar borrador") }
    } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable private fun ReceiptDialog(vm: ErpViewModel, state: UiState, order: PurchaseOrder, dismiss: () -> Unit) {
    var qty by rememberSaveable { mutableStateOf(order.cantidad.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(order.cantidad?.toIntOrNull()?.takeIf { it > 0 }?.let { "%.2f".format(java.util.Locale.US, order.total / it) }.orEmpty()) }
    var submitted by remember { mutableStateOf(false) }
    val n = qty.toIntOrNull() ?: 0; val p = BusinessRules.positiveAmount(price) ?: 0.0
    LaunchedEffect(state.message, state.loading) { if(submitted && !state.loading && state.message.startsWith("Compra recibida")) dismiss() }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Confirmar recepción") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(order.producto); Field(qty, { qty = it.filter(Char::isDigit).take(8) }, "Cantidad recibida", true)
            Field(price, { price = it }, "Precio unitario acordado", true)
            DetailLine("Importe esperado", money(order.total)); DetailLine("Importe recibido", money(n * p))
            InfoCard("Confirma solo cuando la mercadería haya llegado. La operación aumentará el stock y no se reintentará automáticamente.", true)
        }
    }, confirmButton = { TextButton(onClick = { submitted = true; vm.receivePurchase(order, n, p) }, enabled = !state.loading && n > 0 && p > 0 && kotlin.math.abs(n * p - order.total) < .005) { Text("Recibir y actualizar stock") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
