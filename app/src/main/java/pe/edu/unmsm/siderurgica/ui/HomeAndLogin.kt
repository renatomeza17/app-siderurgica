package pe.edu.unmsm.siderurgica.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pe.edu.unmsm.siderurgica.*
import pe.edu.unmsm.siderurgica.domain.BusinessRules

@Composable fun LoginScreen(vm: ErpViewModel, state: UiState) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var erpUser by rememberSaveable { mutableStateOf("") }
    var erpPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = .75f)))).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .15f)) { Text("SP", Modifier.padding(12.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary) }
                    Text("SIDERÚRGICA\nPERÚ", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Tu operación,\nsiempre a mano.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text("Ventas • Inventario • Abastecimiento", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        SectionTitle(if(register) "Crea tu acceso" else "Bienvenido de nuevo", if(register) "Registra tu cuenta Firebase. Tu acceso empresarial lo asigna el administrador." else "Ingresa para continuar con tu operación.")
        Field(identifier, { identifier = it }, if(vm.firebaseConfigured()) "Correo electrónico" else "Usuario ERP")
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Contraseña${if(vm.firebaseConfigured()) " Firebase" else ""}") }, singleLine = true,
            visualTransformation = if(visible) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if(visible) "Ocultar" else "Ver") } })
        if(vm.firebaseConfigured() && !register) {
            Text("Acceso empresarial", style = MaterialTheme.typography.titleSmall)
            Field(erpUser, { erpUser = it }, "Usuario ERP asignado")
            OutlinedTextField(erpPassword, { erpPassword = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contraseña ERP, si es distinta") },
                visualTransformation = PasswordVisualTransformation(), supportingText = { Text("Si la dejas vacía, usaremos la contraseña anterior.") })
        }
        Button(onClick = { if(register) vm.registerUnificado(identifier, password) else vm.loginUnificado(identifier, password, erpUser, erpPassword) },
            enabled = !state.loading && identifier.isNotBlank() && password.isNotBlank() && (register || !vm.firebaseConfigured() || erpUser.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) { Text(if(register) "Crear cuenta Firebase" else "Ingresar") }
        if(vm.firebaseConfigured()) {
            TextButton(onClick = { register = !register }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(if(register) "Ya tengo una cuenta" else "Crear cuenta de acceso") }
            TextButton(onClick = { vm.resetPassword(identifier) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Recuperar contraseña Firebase") }
        }
        Text("Conexión segura con tu ERP empresarial", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun HomeScreen(state: UiState, onNavigate: (String, Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionTitle("Hola, ${state.session?.user?.username.orEmpty()}", "Este es el resumen de tu operación.")
        if(BusinessRules.canSell(state.session?.user?.rol.orEmpty())) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VENTAS REGISTRADAS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                Text(money(state.sales.sumOf { it.total }), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text("${state.sales.size} ventas en los datos cargados", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if(BusinessRules.canSell(state.session?.user?.rol.orEmpty())) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Cotizaciones", state.quotes.count { it.estado == "PENDIENTE" }.toString(), "Pendientes", Modifier.weight(1f)) { onNavigate("sales", 0) }
            MetricCard("Pedidos", state.orders.count { it.estado == "PENDIENTE" }.toString(), "Por gestionar", Modifier.weight(1f)) { onNavigate("sales", 1) }
        }
        val critical = state.inventory.count { (it.stockDisponible ?: it.stock)?.let { stock -> stock <= (it.stockMinimo ?: 10) } == true }
        if(critical > 0) ActionRow("$critical productos con stock crítico", "Revisa disponibilidad antes de confirmar ventas", Icons.Default.Warning) { onNavigate("stock", 0) }
        Text("Continúa tu trabajo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if(BusinessRules.canSell(state.session?.user?.rol.orEmpty())) ActionRow("Cotizar y vender", "Cotización → Pedido → Venta", Icons.Default.ShoppingCart) { onNavigate("sales", 0) }
        if(BusinessRules.canInventory(state.session?.user?.rol.orEmpty())) ActionRow("Consultar inventario", "Disponibilidad y alertas de stock", Icons.AutoMirrored.Filled.List) { onNavigate("stock", 0) }
        if(BusinessRules.canWarehouse(state.session?.user?.rol.orEmpty())) ActionRow("Abastecimiento", "Proveedores y borradores de compras", Icons.Default.Build) { onNavigate("purchases", 0) }
        if(state.drafts.isNotEmpty()) ActionRow("${state.drafts.size} borradores por revisar", "Guardados en este dispositivo", Icons.Default.Edit) { onNavigate("drafts", 0) }
        Text(if(state.updatedAt == null) "Actualiza para consultar los últimos datos del ERP." else "Última consulta: ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(state.updatedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun MetricCard(title: String, count: String, caption: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(count, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun MoreScreen(state: UiState, biometric: Boolean, canBiometric: Boolean, onBiometric: (Boolean) -> Unit, navigate: (String) -> Unit, cache: () -> Unit, logout: () -> Unit, activity: MainActivity) {
    var confirmLogout by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        android.widget.Toast.makeText(activity, if(granted) "Alertas activadas" else "Puedes consultar las alertas dentro de Inventario", android.widget.Toast.LENGTH_LONG).show()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.session?.user?.username.orEmpty(), style = MaterialTheme.typography.titleLarge)
                StatusBadge(state.session?.user?.rol)
                Text(state.firebaseEmail ?: "Firebase sin conectar", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if(BusinessRules.canWarehouse(state.session?.user?.rol.orEmpty())) {
            Text("Abastecimiento", style = MaterialTheme.typography.titleMedium)
            ActionRow("Proveedores", "Proveedores ERP y borradores locales", Icons.Default.Person) { navigate("suppliers") }
            ActionRow("Compras", "Planifica cantidades y presupuesto", Icons.Default.ShoppingCart) { navigate("purchases") }
            ActionRow("Documentos y códigos", "Fotografía, OCR y revisión de facturas", Icons.Default.Add) { navigate("documents") }
            ActionRow("Solicitudes de stock", "Solicitudes locales pendientes de confirmación", Icons.AutoMirrored.Filled.List) { navigate("movements") }
        }
        Text("Tu dispositivo", style = MaterialTheme.typography.titleMedium)
        ActionRow("Borradores", "Revisa antes de enviar al ERP", Icons.Default.Edit) { navigate("drafts") }
        ActionRow("Consultar datos guardados", "Continúa consultando sin conexión", Icons.AutoMirrored.Filled.List, cache)
        if(canBiometric) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Proteger con biometría"); Text("Al volver a abrir la app", style = MaterialTheme.typography.bodySmall) }
            Switch(biometric, onCheckedChange = onBiometric)
        }
        OutlinedButton(onClick = { if(Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else android.widget.Toast.makeText(activity, "Las notificaciones están disponibles en los ajustes de Android", android.widget.Toast.LENGTH_LONG).show() }, modifier = Modifier.fillMaxWidth()) { Text("Activar alertas de inventario") }
        Text("Modo claro u oscuro según tu dispositivo.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión") }
    }
    if(confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("¿Cerrar sesión?") }, text = { Text("Tus borradores seguirán guardados para esta cuenta en el dispositivo.") }, confirmButton = { TextButton(onClick = { confirmLogout = false; logout() }) { Text("Cerrar sesión") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } })
}

