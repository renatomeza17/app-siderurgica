package pe.edu.unmsm.siderurgica

import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.data.*



class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(androidx.compose.ui.platform.ComposeView(this).apply {
            setContent { SiderurgicaScreen(this@MainActivity) }
        })
    }
}

private val Steel = androidx.compose.ui.graphics.Color(0xFF16334A)
private val Copper = androidx.compose.ui.graphics.Color(0xFFB4643F)

@Composable
fun SiderurgicaScreen(activity: MainActivity, vm: ErpViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var unlocked by remember { mutableStateOf(state.session == null) }
    val canBiometric = remember {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Steel, secondary = Copper)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Siderúrgica Perú", style = MaterialTheme.typography.headlineMedium, color = Steel)
            Text("Gestión móvil", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))

            if (state.session != null && !unlocked && canBiometric) {
                Button(onClick = {
                    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                unlocked = true
                            }
                        })
                    prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Abrir Siderúrgica")
                        .setSubtitle("Confirma tu identidad")
                        .setNegativeButtonText("Cancelar").build())
                }) { Text("Abrir con biometría") }
                TextButton(onClick = { vm.logout(); unlocked = true }) { Text("Usar otra cuenta") }
            } else if (state.session == null) {
                // Formulario de login unificado (sin duplicidad)
                LoginPanel(vm, state)
            } else {
                Row { Text("ERP: ${state.session?.user?.username} · ${state.session?.user?.rol}") }
                Text("Firebase: ${state.firebaseEmail ?: "sin conectar"}", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = vm::refresh) { Text("Actualizar") }
                    TextButton(onClick = vm::loadCache) { Text("Datos guardados") }
                    TextButton(onClick = { vm.logout(); unlocked = true }) { Text("Salir") }
                }
                if (state.offline) Text("Vista de datos guardados: verifica cambios antes de operar", color = Copper)

                TabRow(selectedTabIndex = tab) {
                    listOf("Cotizaciones", "Pedidos", "Ventas", "Borradores").forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1) })
                    }
                }
                when (tab) {
                    0 -> QuotePanel(vm, state, Modifier.weight(1f))
                    1 -> OrderPanel(vm, state, Modifier.weight(1f))
                    2 -> LazyColumn(Modifier.weight(1f)) {
                        items(state.sales, key = { it.id }) { sale ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${sale.codigo ?: "Venta #${sale.id}"} · ${sale.cliente}")
                                    Text("${sale.producto} · ${sale.cantidad} · S/ ${"%.2f".format(sale.total)}")
                                    Text("Estado: ${sale.estado ?: "-"}")
                                    Row {
                                        TextButton(onClick = { SalePdf.share(context, SalePdf.create(context, sale)) }) { Text("Compartir PDF") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                try {
                                                    SalePdf.upload(context, sale, SalePdf.create(context, sale))
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }) { Text("Subir a Firebase") }
                                    }
                                }
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.weight(1f)) {
                        item { Text("Los borradores se envían solo tras tu confirmación. Si el servidor respondió tarde, revisa antes de reenviar.") }
                        items(state.drafts, key = { it.localId }) { draft ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${draft.kind} · ${draft.localId.take(8)}")
                                    TextButton(onClick = { vm.sendDraft(draft) }) { Text("Confirmar envío") }
                                }
                            }
                        }
                    }
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.message.isNotBlank()) Text(state.message, color = Copper)
        }
    }
}

@Composable
private fun LoginPanel(vm: ErpViewModel, state: UiState) {
    var emailOrUser by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column {
        Text("Inicio de Sesión", style = MaterialTheme.typography.titleMedium)
        if (!vm.firebaseConfigured()) {
            Text("Para activar Firebase, coloca app/google-services.json.", color = Copper)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = emailOrUser,
            onValueChange = { emailOrUser = it },
            label = { Text("Correo o Usuario") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.loginUnificado(emailOrUser, password) },
            enabled = !state.loading && emailOrUser.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar Sesión")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.registerUnificado(emailOrUser, password) }) {
                Text("Registrarme")
            }
            TextButton(onClick = { vm.resetPassword(emailOrUser) }) {
                Text("Recuperar contraseña")
            }
        }
    }
}

@Composable
private fun QuotePanel(vm: ErpViewModel, state: UiState, modifier: Modifier) {
    var client by remember { mutableStateOf("") }
    var ruc by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var inventoryId by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    val n = qty.toIntOrNull() ?: 0
    val unit = price.toDoubleOrNull() ?: 0.0
    val discount = when { n >= 100 -> 10; n >= 50 -> 7; n >= 10 -> 5; else -> 0 }

    LazyColumn(modifier) {
        item {
            Text("Nueva cotización", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Cliente" to client,
                "RUC" to ruc,
                "Producto" to product,
                "Cantidad" to qty,
                "Precio unitario" to price,
                "ID inventario (opcional)" to inventoryId
            ).forEach { (label, value) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        when (label) {
                            "Cliente" -> client = it
                            "RUC" -> ruc = it
                            "Producto" -> product = it
                            "Cantidad" -> qty = it
                            "Precio unitario" -> price = it
                            else -> inventoryId = it
                        }
                    },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("Subtotal: S/ ${"%.2f".format(n * unit)} · Descuento: $discount % · Total: S/ ${"%.2f".format(n * unit * (100 - discount) / 100)}")
            Text("El ERP admite un producto por cotización y calcula el total definitivo.", style = MaterialTheme.typography.bodySmall)
            Row {
                val body = QuoteCreate(client, ruc.ifBlank { null }, product, n, inventoryId.toLongOrNull(), unit, state.session!!.user.id)
                Button(onClick = { vm.saveQuote(body) }) { Text("Registrar") }
                TextButton(onClick = { vm.saveQuote(body, true) }) { Text("Guardar borrador") }
            }
            OutlinedTextField(filter, { filter = it }, label = { Text("Buscar cliente, código o estado") }, modifier = Modifier.fillMaxWidth())
        }
        items(state.quotes.filter { filter.isBlank() || listOf(it.cliente, it.codigo, it.estado).any { s -> s?.contains(filter, true) == true } }, key = { it.id }) { q ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${q.codigo ?: "#${q.id}"} · ${q.cliente}")
                    Text("${q.producto} · ${q.cantidad} · S/ ${"%.2f".format(q.total ?: 0.0)} · ${q.estado}")
                    if (q.estado == "PENDIENTE") TextButton(onClick = { vm.approveQuote(q.id) }) { Text("Aprobar y crear pedido") }
                }
            }
        }
    }
}

@Composable
private fun OrderPanel(vm: ErpViewModel, state: UiState, modifier: Modifier) {
    var client by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    val n = qty.toIntOrNull() ?: 0
    val unit = price.toDoubleOrNull() ?: 0.0

    LazyColumn(modifier) {
        item {
            Text("Nuevo pedido directo", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(client, { client = it }, label = { Text("Cliente") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(product, { product = it }, label = { Text("Producto") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price, { price = it }, label = { Text("Precio unitario") }, modifier = Modifier.fillMaxWidth())
            Text("Subtotal S/ ${"%.2f".format(n * unit)} · IGV S/ ${"%.2f".format(n * unit * .18)} · Total S/ ${"%.2f".format(n * unit * 1.18)}")
            Row {
                val order = Order(cliente = client, producto = product, cantidad = n, precioUnitario = unit, usuarioId = state.session!!.user.id)
                Button(onClick = { vm.createOrder(order) }) { Text("Registrar") }
                TextButton(onClick = { vm.createOrder(order, true) }) { Text("Borrador") }
            }
        }
        items(state.orders, key = { it.id }) { p ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.codigo ?: "#${p.id}"} · ${p.cliente} · ${p.estado}")
                    Text("${p.producto} · ${p.cantidad} · S/ ${"%.2f".format(p.total ?: 0.0)}")
                    Row {
                        if (p.estado == "PENDIENTE") {
                            TextButton(onClick = { vm.orderState(p.id, "APROBADO") }) { Text("Aprobar") }
                            TextButton(onClick = { vm.orderState(p.id, "ANULADO") }) { Text("Anular") }
                        }
                    }
                }
            }
        }
    }
}