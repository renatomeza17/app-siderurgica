package pe.edu.unmsm.siderurgica

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.edu.unmsm.siderurgica.ui.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(androidx.compose.ui.platform.ComposeView(this).apply { setContent { SiderurgicaScreen(this@MainActivity) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SiderurgicaScreen(activity: MainActivity, vm: ErpViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
    var salesTab by rememberSaveable { mutableIntStateOf(0) }
    val settings = remember { activity.getSharedPreferences("mobile-preferences", 0) }
    var biometric by rememberSaveable { mutableStateOf(settings.getBoolean("biometric", false)) }
    var unlocked by remember { mutableStateOf(state.session == null || !biometric) }
    val snack = remember { SnackbarHostState() }
    val canBiometric = remember { BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS }
    LaunchedEffect(state.session?.user?.id) { page = "home"; salesTab = 0 }
    LaunchedEffect(state.message, state.loading) {
        if (state.message.isNotBlank() && !state.loading) { snack.showSnackbar(state.message, duration = SnackbarDuration.Long); vm.clearMessage() }
    }
    SteelTheme {
        val logged = state.session != null
        val locked = logged && !unlocked && biometric && canBiometric
        val root = page in listOf("home", "sales", "stock", "more")
        val titles = mapOf("home" to "Siderúrgica Perú", "sales" to "Gestión comercial", "stock" to "Inventario", "more" to "Más opciones",
            "quote-form" to "Nueva cotización", "order-form" to "Nuevo pedido", "suppliers" to "Proveedores", "purchases" to "Compras",
            "documents" to "Documentos", "drafts" to "Borradores", "movements" to "Solicitudes de stock")
        androidx.activity.compose.BackHandler(enabled = logged && page != "home") { page = if(root) "home" else "more" }
        Scaffold(
            topBar = { if(logged && !locked) TopAppBar(title = { Text(titles[page] ?: "Siderúrgica Perú", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { if(!root) IconButton(onClick = { page = if(page.endsWith("form")) "sales" else "more" }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = vm::refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Actualizar datos") } }) },
            bottomBar = { if(logged && !locked && root) NavigationBar {
                val pages = listOf("home", "sales", "stock", "more")
                val labels = listOf("Inicio", "Ventas", "Stock", "Más")
                val icons = listOf(Icons.Default.Home, Icons.Default.ShoppingCart, Icons.Default.List, Icons.Default.Menu)
                pages.forEachIndexed { i, value -> NavigationBarItem(selected = page == value, onClick = { page = value }, icon = { Icon(icons[i], labels[i]) }, label = { Text(labels[i]) }) }
            } },
            snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(locked) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        SectionTitle("Tu sesión está protegida", "Confirma tu identidad para continuar.")
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
                            })
                            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Abrir Siderúrgica Perú").setSubtitle("Confirma tu identidad").setNegativeButtonText("Cancelar").build())
                        }, modifier = Modifier.fillMaxWidth()) { Text("Desbloquear con biometría") }
                        TextButton(onClick = { vm.logout(); unlocked = true }) { Text("Ingresar con otra cuenta") }
                    }
                } else if(!logged) LoginScreen(vm, state)
                else {
                    if(state.offline) InfoCard("Datos guardados · El stock puede haber cambiado. Actualiza antes de confirmar.", true)
                    if(page == "sales" && !pe.edu.unmsm.siderurgica.domain.BusinessRules.canSell(state.session?.user?.rol.orEmpty())) InfoCard("El módulo comercial requiere rol VENTAS o ADMIN.")
                    else when(page) {
                        "home" -> HomeScreen(state, onNavigate = { dest, tab -> page = dest; salesTab = tab })
                        "sales" -> SalesScreen(vm, state, salesTab, { salesTab = it }, { page = if(salesTab == 0) "quote-form" else "order-form" })
                        "quote-form", "order-form" -> CommercialForm(vm, state, page == "quote-form") { page = "sales" }
                        "stock" -> InventoryScreen(vm, state)
                        "suppliers" -> SupplierScreen(vm, state)
                        "purchases" -> PurchaseScreen(vm, state)
                        "documents" -> DocumentScreen(vm, state)
                        "drafts" -> DraftScreen(vm, state)
                        "movements" -> MovementScreen(vm, state)
                        else -> MoreScreen(state, biometric, canBiometric, { enabled -> biometric = enabled; settings.edit().putBoolean("biometric", enabled).apply() },
                            { page = it }, vm::loadCache, { vm.logout(); unlocked = true }, activity)
                    }
                }
            }
        }
    }
}