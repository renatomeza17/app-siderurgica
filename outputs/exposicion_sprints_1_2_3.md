# Código para exposición: Sprints 1, 2 y 3

Alcance: HU01–HU09, identificadas en PRODUCT_BACKLOG_FINAL.xlsx, hoja «Backlog actualizado», filas 4–12. El backlog se usa como referencia de requisitos; sus estados y su matriz no prueban implementación.

Este repositorio contiene Android/Kotlin/Compose con MVVM. La equivalencia para exponer es **pantalla → ViewModel (coordinación) → repositorio/servicio → DTO/API REST o entidad/DAO Room → SQLite**. Firebase Authentication y Storage son servicios externos. No contiene los controladores, servicios, entidades ni SQL del backend ERP: los flujos remotos terminan aquí en el contrato Retrofit. No se inventa código del servidor.

Los bloques siguientes son copias literales de líneas contiguas del repositorio, sin pseudocódigo ni puntos suspensivos añadidos. Los rangos figuran fuera del código para facilitar copiarlo. Son fragmentos para un guion, no un único archivo compilable; los imports y dependencias permanecen en sus archivos originales. Las piezas compartidas se incluyen al final y se referencian en cada funcionalidad.

## Sprint 1 · HU01 — Arquitectura MVVM y Material Design 3

**Flujo:** MainActivity/SiderurgicaScreen → ErpViewModel → ErpRepository → LocalStore/ErpApi

**Guion sugerido:** «La pantalla observa un StateFlow del ViewModel. Application proporciona el repositorio y la base Room; los datos remotos se consumen con Retrofit.»

**Alcance comprobado:** Es una historia transversal, sin operación de negocio propia. La provisión de dependencias es manual mediante Application. Ver anexo de estado, corrutinas, Retrofit y Room.

### Entrada, observación de estado y navegación

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 1–126.

```kotlin
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
    /*val canBiometric = remember { BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS }*/
    var canBiometric by remember { mutableStateOf(false) }

    val lifecycleOwner =
        androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, activity) {
        fun checkBiometric() {
            canBiometric = BiometricManager.from(activity)
                .canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                ) == BiometricManager.BIOMETRIC_SUCCESS
        }

        checkBiometric()

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checkBiometric()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
```

### Tema Material 3

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Theme.kt`, líneas 1–29.

```kotlin
package pe.edu.unmsm.siderurgica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF174C61), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EAF0), onPrimaryContainer = Color(0xFF103544),
    secondary = Color(0xFF795400), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2A3), onSecondaryContainer = Color(0xFF392800),
    background = Color(0xFFF5F7F9), onBackground = Color(0xFF172B36),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF172B36),
    surfaceVariant = Color(0xFFE7EDF0), onSurfaceVariant = Color(0xFF465C68),
    outline = Color(0xFF718590)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9CCDDD), onPrimary = Color(0xFF073544),
    primaryContainer = Color(0xFF174C61), onPrimaryContainer = Color(0xFFD6EAF0),
    secondary = Color(0xFFFFD077), onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF5C4100), onSecondaryContainer = Color(0xFFFFE2A3),
    background = Color(0xFF0F1C24), onBackground = Color(0xFFE1EAF0),
    surface = Color(0xFF162630), onSurface = Color(0xFFE1EAF0),
    surfaceVariant = Color(0xFF263C47), onSurfaceVariant = Color(0xFFB7C9D2)
)
@Composable fun SteelTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
```

### Provisión de dependencias y migración SQLite

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/SiderurgicaApp.kt`, líneas 1–24.

```kotlin
package pe.edu.unmsm.siderurgica

import android.app.Application
import androidx.room.Room
import pe.edu.unmsm.siderurgica.data.LocalStore
import pe.edu.unmsm.siderurgica.data.ErpRepository
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class SiderurgicaApp : Application() {
    val repository by lazy { ErpRepository(this) }
    val db by lazy { Room.databaseBuilder(this, LocalStore::class.java, "siderurgica-mobile.db").addMigrations(OperationsMigration).build() }
}

private object OperationsMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS suppliers (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL, ruc TEXT NOT NULL, contact TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, serverId INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_owner_ruc ON suppliers(owner, ruc)")
        db.execSQL("CREATE TABLE IF NOT EXISTS purchases (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, supplierId TEXT NOT NULL, supplierName TEXT NOT NULL, product TEXT NOT NULL, quantity INTEGER NOT NULL, unitPrice REAL NOT NULL, budget REAL NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS movements (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, inventoryId INTEGER NOT NULL, product TEXT NOT NULL, kind TEXT NOT NULL, quantity INTEGER NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS documents (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, imagePath TEXT NOT NULL, ruc TEXT NOT NULL, number TEXT NOT NULL, date TEXT NOT NULL, amount REAL NOT NULL, code TEXT NOT NULL, createdAt INTEGER NOT NULL, storagePath TEXT)")
    }
}

```

## Sprint 1 · HU02 — Registro, inicio, recuperación, cierre y biometría

**Flujo:** LoginScreen → ErpViewModel → FirebaseAuth + ErpRepository.login → ErpApi.login → SessionVault

**Guion sugerido:** «El usuario inicia con correo y contraseña; el prefijo del correo se usa como usuario ERP. Se verifica el ERP y Firebase, y el token ERP se guarda cifrado.»

**Alcance comprobado:** El registro Firebase exige primero una cuenta ERP existente. Firebase no crea usuarios ERP. La recuperación modifica el acceso Firebase; no hay endpoint de recuperación ERP. La biometría y su preferencia están en MainActivity (HU01) y MoreScreen. Los permisos del servidor no pueden verificarse desde este repositorio.

### Formulario de acceso

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/HomeAndLogin.kt`, líneas 29–85.

```kotlin
@Composable fun LoginScreen(vm: ErpViewModel, state: UiState) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    /*var erpUser by rememberSaveable { mutableStateOf("") }
    var erpPassword by remember { mutableStateOf("") }*/
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
        /*if(vm.firebaseConfigured() && !register) {
            Text("Acceso empresarial", style = MaterialTheme.typography.titleSmall)
            Field(erpUser, { erpUser = it }, "Usuario ERP asignado")
            OutlinedTextField(erpPassword, { erpPassword = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contraseña ERP, si es distinta") },
                visualTransformation = PasswordVisualTransformation(), supportingText = { Text("Si la dejas vacía, usaremos la contraseña anterior.") })
        }*/

        Button(
            onClick = {
                if (register) {
                    vm.registerUnificado(identifier, password)
                } else {
                    vm.loginUnificado(identifier, password)
                }
            },
            enabled = !state.loading &&
                    identifier.isNotBlank() &&
                    password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (register) "Crear cuenta Firebase" else "Ingresar")
        }
        if(vm.firebaseConfigured()) {
            TextButton(onClick = { register = !register }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(if(register) "Ya tengo una cuenta" else "Crear cuenta de acceso") }
            TextButton(onClick = { vm.resetPassword(identifier) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Recuperar contraseña Firebase") }
        }
        Text("Conexión segura con tu ERP empresarial", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

```

### Preferencia biométrica y cierre

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/HomeAndLogin.kt`, líneas 119–190.

```kotlin
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
        val biometricStatus = androidx.biometric.BiometricManager
            .from(activity)
            .canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

        val biometricReason = when (biometricStatus) {
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Android no informa una huella o biometría registrada."

            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Sensor temporalmente no disponible. Desbloquea el teléfono y vuelve a intentar."

            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "Android no informa un sensor biométrico compatible."

            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "Android requiere una actualización de seguridad para usar biometría."

            else ->
                "Biometría no disponible (estado $biometricStatus)."
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Proteger con biometría")
                Text(
                    if (canBiometric) "Al volver a abrir la app"
                    else biometricReason,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = biometric,
                onCheckedChange = onBiometric,
                enabled = canBiometric
            )
        }
        OutlinedButton(onClick = { if(Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else android.widget.Toast.makeText(activity, "Las notificaciones están disponibles en los ajustes de Android", android.widget.Toast.LENGTH_LONG).show() }, modifier = Modifier.fillMaxWidth()) { Text("Activar alertas de inventario") }
        Text("Modo claro u oscuro según tu dispositivo.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión") }
    }
    if(confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("¿Cerrar sesión?") }, text = { Text("Tus borradores seguirán guardados para esta cuenta en el dispositivo.") }, confirmButton = { TextButton(onClick = { confirmLogout = false; logout() }) { Text("Cerrar sesión") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } })
}

```

### Autenticación y cierre en ViewModel

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 68–174.

```kotlin
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
```

### Sesión cifrada y login ERP

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 24–88.

```kotlin
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
```

### DTO de autenticación

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 8–10.

```kotlin
data class ErpUser(val id: Long, val username: String, val rol: String, val estado: String?)
data class LoginBody(val username: String, val password: String)
data class LoginResult(val success: Boolean, val mensaje: String?, val usuario: ErpUser?, val accessToken: String?)
```

### Endpoint de autenticación

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 44–45.

```kotlin
interface ErpApi {
    @POST("login") suspend fun login(@Body body: LoginBody): LoginResult
```

## Sprint 1 · HU03 — Trabajo offline y sincronización

**Flujo:** loadCache/guardar borrador → repositorio.fetch/saveDraft → LocalDao → Room; CacheSyncWorker → repositorio → API → caché

**Guion sugerido:** «Las consultas remotas se guardan por usuario y tipo. Si falla la red, se recupera la caché. WorkManager refresca lecturas y los borradores se envían con confirmación.»

**Alcance comprobado:** No hay sincronización automática de escrituras ni garantía de idempotencia. No existen tablas independientes de usuarios o productos: hay caché JSON y sesión cifrada. El worker puede completar usando caché; Result.success no demuestra que hubo actualización remota.

### Programación periódica y estado

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 21–44.

```kotlin
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
```

### Carga local y refresco por roles

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 175–273.

```kotlin
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

```

### Consulta de caché y envío manual de borradores

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 275–307.

```kotlin
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
```

### Pantalla de borradores

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 206–227.

```kotlin
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
```

### Consulta remota y fallback local

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 89–108.

```kotlin
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
```

### Persistencia de borradores y worker

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 151–180.

```kotlin
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


```

### Entidades, consultas y base Room

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/LocalStore.kt`, líneas 1–31.

```kotlin
package pe.edu.unmsm.siderurgica.data

import androidx.room.*

@Entity(tableName = "cache", primaryKeys = ["owner", "kind", "serverId"])
data class CachedRecord(val owner: String, val kind: String, val serverId: Long, val json: String, val cachedAt: Long)

@Entity(tableName = "drafts")
data class Draft(
    @PrimaryKey val localId: String, val owner: String, val kind: String, val json: String,
    val createdAt: Long, val status: String = "PENDIENTE"
)

@Dao interface LocalDao {
    @Query("SELECT * FROM cache WHERE owner = :owner AND kind = :kind ORDER BY serverId DESC")
    suspend fun cached(owner: String, kind: String): List<CachedRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCache(rows: List<CachedRecord>)
    @Query("DELETE FROM cache WHERE owner = :owner AND kind = :kind") suspend fun clearKind(owner: String, kind: String)
    @Transaction suspend fun replace(owner: String, kind: String, rows: List<CachedRecord>) {
        clearKind(owner, kind); putCache(rows)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(draft: Draft)
    @Query("SELECT * FROM drafts WHERE owner = :owner AND status = 'PENDIENTE' ORDER BY createdAt")
    suspend fun drafts(owner: String): List<Draft>
    @Query("DELETE FROM drafts WHERE localId = :id AND owner = :owner") suspend fun deleteDraft(id: String, owner: String)
}

@Database(entities = [CachedRecord::class, Draft::class, Supplier::class, Purchase::class, StockMovement::class, ScannedDocument::class], version = 2, exportSchema = false)
abstract class LocalStore : RoomDatabase() { abstract fun dao(): LocalDao
    abstract fun operations(): OperationsDao
}
```

## Sprint 2 · HU04 — Registro y consulta de cotizaciones

**Flujo:** SalesScreen/CommercialForm → saveQuote/approveQuote/convertQuote/rejectQuote → ErpRepository → ErpApi → ERP; consultas → caché Room

**Guion sugerido:** «El formulario prepara una cotización de un producto y muestra importes estimados. El ViewModel valida y el repositorio envía el DTO; la respuesta y los listados se obtienen del ERP.»

**Alcance comprobado:** La interfaz actual muestra aprobar y, después, convertir en pedido como acciones separadas. El README describe conversión automática al aprobar: no se puede resolver esa diferencia sin el backend. Ver anexo de validación, permisos y caché.

### Listado, filtros y acciones comerciales compartidas HU04/HU05

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 23–104.

```kotlin
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

```

### Formulario compartido HU04/HU05, cálculo y envío

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 134–205.

```kotlin
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
```

### Alta de cotización

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 282–286.

```kotlin
    fun saveQuote(body: QuoteCreate, asDraft: Boolean = false) = run {
        repo.requireSales(); validate(body.cliente, body.producto, body.cantidad, body.precioUnitario, body.ruc)
        if (asDraft) { repo.saveDraft("QUOTE", repo.gson().toJson(body)); localData(); message("Borrador guardado.") }
        else { repo.createQuote(body); message("Cotización registrada."); mutable.value = mutable.value.copy(quotes = repo.quotes()) }
    }
```

### Aprobación, conversión y rechazo

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 308–318.

```kotlin
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
```

### Consulta, alta y estados en repositorio

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 90–90.

```kotlin
    suspend fun quotes(force: Boolean = true): List<Quote> { requireSales(); return fetch("quote", force, { api.quotes() }, Quote::id) }
```

### Escrituras comerciales compartidas HU04/HU05

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 128–150.

```kotlin
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
```

### Modelos de cotización

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 12–25.

```kotlin
data class Quote(
    val id: Long = 0, val codigo: String? = null, val cliente: String = "", val ruc: String? = null,
    val producto: String = "", val cantidad: Int = 0, val inventarioId: Long? = null,
    @SerializedName(value = "precio_unitario", alternate = ["precioUnitario"]) val precioUnitario: Double = 0.0,
    val subtotal: Double? = null, @SerializedName(value = "descuento_porcentaje", alternate = ["descuentoPorcentaje"]) val descuentoPorcentaje: Int? = null,
    val total: Double? = null, val estado: String? = null,
    @SerializedName("usuario_id") val usuarioId: Long? = null
)
data class QuoteCreate(
    val cliente: String, val ruc: String?, val producto: String, val cantidad: Int,
    val inventarioId: Long?, @SerializedName("precio_unitario") val precioUnitario: Double,
    @SerializedName("usuario_id") val usuarioId: Long
)
data class QuoteCreated(val success: Boolean, val mensaje: String?, val cotizacion: Quote?)
```

### Endpoints de cotizaciones

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 46–49.

```kotlin
    @GET("cotizaciones") suspend fun quotes(): List<Quote>
    @POST("cotizaciones") suspend fun createQuote(@Body body: QuoteCreate): QuoteCreated
    @PATCH("cotizaciones/{id}/estado") suspend fun quoteState(@Path("id") id: Long, @Body body: StateBody): Response<ResponseBody>
    @POST("cotizaciones/{id}/convertir-pedido") suspend fun convertQuote(@Path("id") id: Long): ConvertedOrder
```

## Sprint 2 · HU05 — Pedidos y ventas

**Flujo:** SalesScreen/CommercialForm → createOrder/orderState/sale → ErpRepository → ErpApi → ERP → refresco de listados/caché

**Guion sugerido:** «El pedido se registra, se aprueba, rechaza o anula, y una acción sobre el pedido aprobado genera la venta. Los cambios llegan al ERP mediante Retrofit.»

**Alcance comprobado:** Usa las pantallas y métodos compartidos de HU04. El descuento/reserva de stock y las transacciones son del backend no incluido. orderState refresca inventario; sale solo refresca pedidos y ventas. El README afirma venta automática al aprobar, pero la UI actual ofrece generar venta después. No hay reintentos automáticos seguros para POST.

### Alta y validación del pedido

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 287–296.

```kotlin
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
```

### Cambios de estado y venta

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 308–318.

```kotlin
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
```

### Lecturas de pedidos y ventas

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 91–92.

```kotlin
    suspend fun orders(force: Boolean = true): List<Order> { requireSales(); return fetch("order", force, { api.orders() }, Order::id) }
    suspend fun sales(force: Boolean = true): List<Sale> { requireSales(); return fetch("sale", force, { api.sales() }, Sale::id) }
```

### Modelos de pedido, venta y estado

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 26–42.

```kotlin
data class Order(
    val id: Long = 0, val codigo: String? = null, val cliente: String = "", val ruc: String? = null,
    val producto: String = "", val cantidad: Int = 0, val inventarioId: Long? = null,
    @SerializedName(value = "precioUnitario", alternate = ["precio_unitario"]) val precioUnitario: Double = 0.0,
    val subtotal: Double? = null, val igv: Double? = null, val total: Double? = null,
    val estado: String? = null, val estadoStock: String? = null,
    @SerializedName(value = "usuarioId", alternate = ["usuario_id"]) val usuarioId: Long? = null
)
data class Sale(
    val id: Long = 0, val codigo: String? = null, val cliente: String = "", val ruc: String? = null,
    val producto: String = "", val cantidad: Int = 0, val precioUnitario: Double = 0.0,
    val subtotal: Double = 0.0, val igv: Double = 0.0, val total: Double = 0.0,
    val estado: String? = null, val pedidoId: Long? = null
)
data class InventoryItem(val id: Long, val producto: String?, val stock: Int?, val stockDisponible: Int?, val precioUnitario: Double?, val stockMinimo: Int? = null, val categoria: String? = null, val unidad: String? = null, val stockObjetivo: Int? = null, val stockReservado: Int? = null)
data class StateBody(val estado: String)
data class ConvertedOrder(val pedido: Order)
```

### Endpoints de pedidos y ventas

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 50–54.

```kotlin
    @GET("pedidos") suspend fun orders(): List<Order>
    @POST("pedidos") suspend fun createOrder(@Body body: Order): Order
    @PATCH("pedidos/{id}/estado") suspend fun orderState(@Path("id") id: Long, @Body body: StateBody): Response<ResponseBody>
    @GET("ventas") suspend fun sales(): List<Sale>
    @POST("ventas/desde-pedido/{id}") suspend fun saleFromOrder(@Path("id") id: Long): Sale
```

## Sprint 2 · HU06 — Generación y compartición de PDF

**Flujo:** SaleCard → SalePdf.create/share/upload → archivo local / FileProvider / Firebase Storage

**Guion sugerido:** «A partir del modelo Sale se dibuja un PDF con cliente, producto e importes. Se puede abrir, compartir o subir al espacio Firebase del usuario.»

**Alcance comprobado:** No es factura o boleta tributaria; el propio PDF lo indica. No pasa por un método ViewModel: la pantalla llama al servicio SalePdf con una corrutina. El modelo Sale está en HU05; consultar ventas pasa por repositorio/API/caché.

### Acciones de PDF desde la pantalla

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 105–133.

```kotlin
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

```

### Servicio de PDF, compartir y subir

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/SalePdf.kt`, líneas 1–63.

```kotlin
package pe.edu.unmsm.siderurgica

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import pe.edu.unmsm.siderurgica.data.Sale
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/** Constancia de venta; no sustituye una boleta o factura fiscal emitida por SUNAT. */
object SalePdf {
    fun create(context: Context, sale: Sale): File {
        val file = File(context.cacheDir, "comprobantes/venta-${sale.id}.pdf")
        file.parentFile?.mkdirs()
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = android.graphics.Color.BLACK }
            val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-PE"))
            fun line(y: Float, value: String) { canvas.drawText(value.take(72), 48f, y, paint) }
            paint.textSize = 22f; paint.isFakeBoldText = true
            line(65f, "Siderúrgica Perú")
            paint.textSize = 14f; paint.isFakeBoldText = false
            line(100f, "Constancia de venta ${sale.codigo ?: sale.id}")
            line(135f, "Cliente: ${sale.cliente}")
            line(160f, "RUC: ${sale.ruc ?: "No registrado"}")
            line(195f, "Producto: ${sale.producto}")
            line(220f, "Cantidad: ${sale.cantidad}")
            line(245f, "Precio unitario: ${money.format(sale.precioUnitario)}")
            line(285f, "Subtotal: ${money.format(sale.subtotal)}")
            line(310f, "IGV: ${money.format(sale.igv)}")
            paint.isFakeBoldText = true; line(345f, "Total: ${money.format(sale.total)}")
            paint.isFakeBoldText = false; paint.textSize = 10f
            line(390f, "Documento informativo. No es comprobante de pago tributario.")
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartir constancia"))
    }
    suspend fun upload(context: Context, sale: Sale, file: File) {
        require(FirebaseApp.getApps(context).isNotEmpty()) { "Configura Firebase primero" }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Inicia sesión en Firebase")
        val uri = android.net.Uri.fromFile(file)
        val metadata = StorageMetadata.Builder().setContentType("application/pdf").build()
        FirebaseStorage.getInstance().reference.child("usuarios/$uid/ventas/${sale.id}.pdf").putFile(uri, metadata).await()
    }
}
```

### Proveedor de archivos Android

Origen: `app/src/main/AndroidManifest.xml`, líneas 1–17.

```text
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-feature android:name="android.hardware.camera" android:required="false"/>
    <application android:name=".SiderurgicaApp" android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="Siderúrgica Perú" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher" android:usesCleartextTraffic="false" android:allowBackup="false">
        <service android:name=".StockMessagingService" android:exported="false">
            <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT"/></intent-filter>
        </service>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
        </activity>
        <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.files" android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
        </provider>
    </application>
</manifest>
```

### Rutas locales compartibles

Origen: `app/src/main/res/xml/file_paths.xml`, líneas 1–1.

```text
<paths xmlns:android="http://schemas.android.com/apk/res/android"><cache-path name="comprobantes" path="comprobantes/"/><cache-path name="capturas" path="capturas/"/></paths>
```

### Reglas Storage disponibles

Origen: `firebase.storage.rules`, líneas 1–12.

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

## Sprint 3 · HU07 — Inventario, movimientos y alertas

**Flujo:** InventoryScreen/MovementDialog → saveMovement/sendMovement → repo.changeStock → ErpApi; worker → StockNotifications; FCM → StockMessagingService

**Guion sugerido:** «El inventario distingue normal, bajo y agotado. Se registran entradas, salidas y ajustes mediante el ERP, y se guardan solicitudes locales para enviarlas después.»

**Alcance comprobado:** El ajuste usa PUT tras leer el producto; entrada/salida usan PATCH. Las alertas periódicas son notificaciones locales. El receptor FCM existe, pero falta registro del token en el ERP y emisor del servidor. No se conserva localmente un historial de movimientos online; el motivo no se envía en StockBody.

### Consulta, indicadores y formulario de movimientos

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 20–89.

```kotlin
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
```

### Guardar movimiento online u offline

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 351–362.

```kotlin
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
```

### Enviar movimiento local

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 398–402.

```kotlin
    fun sendMovement(movement: StockMovement) = run {
        require(movement.owner == owner()) { "Solicitud no válida." }; repo.requireWarehouse()
        repo.changeStock(movement.inventoryId, movement.kind, movement.quantity); operations.deleteMovement(movement.localId, owner()); localData()
        mutable.value = mutable.value.copy(inventory = repo.inventory()); message("Movimiento enviado al ERP.")
    }
```

### Consulta y mutación de inventario

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 93–93.

```kotlin
    suspend fun inventory(force: Boolean = true): List<InventoryItem> { requireInventory(); return fetch("inventory", force, { api.inventory() }, InventoryItem::id) }
```

### Servicio de stock

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 118–126.

```kotlin
    suspend fun changeStock(id: Long, kind: String, quantity: Int) {
        requireWarehouse()
        val response = if(kind == "AJUSTE") {
            val current = api.inventoryItem(id)
            require(quantity >= (current.stockReservado ?: 0)) { "El ajuste no puede reducir el stock reservado." }
            api.adjustStock(id, InventoryUpdate(current.producto.orEmpty(), current.categoria, quantity, current.stockMinimo, current.precioUnitario, current.unidad, current.stockObjetivo))
        } else api.changeStock(id, StockBody(kind, quantity))
        require(response.success) { response.mensaje ?: "No se actualizó el stock." }
    }
```

### DTO inventario

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 40–40.

```kotlin
data class InventoryItem(val id: Long, val producto: String?, val stock: Int?, val stockDisponible: Int?, val precioUnitario: Double?, val stockMinimo: Int? = null, val categoria: String? = null, val unidad: String? = null, val stockObjetivo: Int? = null, val stockReservado: Int? = null)
```

### Endpoints stock

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 55–58.

```kotlin
    @GET("inventario") suspend fun inventory(): List<InventoryItem>
    @GET("inventario/{id}") suspend fun inventoryItem(@Path("id") id: Long): InventoryItem
    @PATCH("inventario/{id}/stock") suspend fun changeStock(@Path("id") id: Long, @Body body: StockBody): StockResult
    @PUT("inventario/{id}") suspend fun adjustStock(@Path("id") id: Long, @Body body: InventoryUpdate): StockResult
```

### Modelos de mutación

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 70–72.

```kotlin
data class StockBody(val tipo: String, val cantidad: Int)
data class StockResult(val success: Boolean, val mensaje: String?, val inventario: InventoryItem?)
data class InventoryUpdate(val producto: String, val categoria: String?, val stock: Int, val stockMinimo: Int?, val precioUnitario: Double?, val unidad: String?, val stockObjetivo: Int?)
```

### Notificaciones locales y receptor FCM

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/StockNotifications.kt`, líneas 1–52.

```kotlin
package pe.edu.unmsm.siderurgica

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import pe.edu.unmsm.siderurgica.data.InventoryItem
import pe.edu.unmsm.siderurgica.domain.BusinessRules

object StockNotifications {
    private const val CHANNEL = "stock-alerts"
    fun show(context: Context, title: String, body: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alertas de inventario", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = android.content.Intent(context, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(context, id, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).build())
    }
    fun check(context: Context, inventory: List<InventoryItem>, owner: String) {
        val prefs = context.getSharedPreferences("stock-alerts-$owner", Context.MODE_PRIVATE)
        for (item in inventory) {
            val available = item.stockDisponible ?: item.stock
            val level = BusinessRules.stockLevel(available, item.stockMinimo ?: 10)
            val previous = prefs.getString(item.id.toString(), "Normal")
            if (level in listOf("Bajo", "Agotado") && level != previous) {
                show(context, "Stock ${level.lowercase()}", "${item.producto ?: "Producto #${item.id}"}: $available unidades según la última consulta.", (owner + item.id).hashCode())
            }
            prefs.edit().putString(item.id.toString(), level).apply()
        }
    }
}
/** Receives messages only. Registering tokens and sending real alerts requires the ERP backend. */
class StockMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Private device storage only: main has no authenticated token-registration endpoint.
        getSharedPreferences("fcm-device", MODE_PRIVATE).edit().putString("registration-token", token).apply()
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val session = (application as SiderurgicaApp).repository.vault.read() ?: return
        if (!BusinessRules.canInventory(session.user.rol)) return
        // Do not trust arbitrary quantities received from push as authoritative inventory.
        StockNotifications.show(this, "Siderúrgica Perú", "Hay una actualización de inventario. Abre la app y consulta el stock actualizado.", 1001)
    }
}
```

### Modelo/DAO de movimientos y operaciones locales

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 1–27.

```kotlin
package pe.edu.unmsm.siderurgica.data

import androidx.room.*

@Entity(tableName = "suppliers", indices = [Index(value = ["owner", "ruc"], unique = true)])
data class Supplier(@PrimaryKey val localId: String, val owner: String, val name: String, val ruc: String, val contact: String, val phone: String, val email: String, val serverId: Long? = null)
@Entity(tableName = "purchases")
data class Purchase(@PrimaryKey val localId: String, val owner: String, val supplierId: String, val supplierName: String, val product: String, val quantity: Int, val unitPrice: Double, val budget: Double, val createdAt: Long)
@Entity(tableName = "movements")
data class StockMovement(@PrimaryKey val localId: String, val owner: String, val inventoryId: Long, val product: String, val kind: String, val quantity: Int, val reason: String, val createdAt: Long)
@Entity(tableName = "documents")
data class ScannedDocument(@PrimaryKey val localId: String, val owner: String, val imagePath: String, val ruc: String, val number: String, val date: String, val amount: Double, val code: String, val createdAt: Long, val storagePath: String? = null)

@Dao interface OperationsDao {
    @Query("SELECT * FROM suppliers WHERE owner=:owner ORDER BY name COLLATE NOCASE") suspend fun suppliers(owner: String): List<Supplier>
    @Query("DELETE FROM suppliers WHERE localId=:id AND owner=:owner") suspend fun deleteSupplier(id: String, owner: String)
    @Query("DELETE FROM purchases WHERE localId=:id AND owner=:owner") suspend fun deletePurchase(id: String, owner: String)
    @Query("DELETE FROM movements WHERE localId=:id AND owner=:owner") suspend fun deleteMovement(id: String, owner: String)
    @Insert suspend fun addSupplier(value: Supplier)
    @Query("SELECT * FROM purchases WHERE owner=:owner ORDER BY createdAt DESC") suspend fun purchases(owner: String): List<Purchase>
    @Insert suspend fun addPurchase(value: Purchase)
    @Query("SELECT * FROM movements WHERE owner=:owner ORDER BY createdAt DESC") suspend fun movements(owner: String): List<StockMovement>
    @Insert suspend fun addMovement(value: StockMovement)
    @Query("SELECT * FROM documents WHERE owner=:owner ORDER BY createdAt DESC") suspend fun documents(owner: String): List<ScannedDocument>
    @Insert suspend fun addDocument(value: ScannedDocument)
    @Query("UPDATE documents SET storagePath=:path WHERE localId=:id AND owner=:owner") suspend fun markUploaded(id: String, owner: String, path: String)
}
```

## Sprint 3 · HU08 — Proveedores, órdenes y recepción de compra

**Flujo:** SupplierScreen/PurchaseScreen → saveSupplier/savePurchase/receivePurchase → repositorio → API ERP; borradores → OperationsDao → SQLite

**Guion sugerido:** «Se valida RUC y duplicados, se prepara la orden con presupuesto y se registra su recepción. El ERP devuelve compra e inventario; el ViewModel refresca ambos.»

**Alcance comprobado:** La cola local requiere envío manual. El presupuesto ingresado se valida en el cliente; la validación transaccional del presupuesto ERP no puede verificarse. La recepción comprueba importe y duplicados antes de enviar, pero la garantía ante concurrencia corresponde al servidor. Entidades/DAO locales en HU07 y migración en HU01.

### Pantallas de proveedores, compras y recepción

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 90–222.

```kotlin
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
```

### Registrar proveedor y orden

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 321–350.

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
```

### Enviar borradores de proveedores y compras

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 384–397.

```kotlin
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
```

### Estados de compra y recepción

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 403–414.

```kotlin
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
```

### Repositorio de abastecimiento

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 110–117.

```kotlin
    suspend fun suppliers(force: Boolean = true): List<RemoteSupplier> { requireWarehouse(); return fetch("supplier", force, { api.suppliers() }, RemoteSupplier::id) }
    suspend fun purchaseOrders(force: Boolean = true): List<PurchaseOrder> { requireWarehouse(); return fetch("purchase", force, { api.purchaseOrders() }, PurchaseOrder::id) }
    suspend fun receipts(force: Boolean = true): List<Receipt> { requireWarehouse(); return fetch("receipt", force, { api.receipts() }, Receipt::id) }
    suspend fun budget(): Budget { requireWarehouse(); return api.budget() }
    suspend fun createSupplier(body: SupplierBody): RemoteSupplier { requireWarehouse(); return api.createSupplier(body) }
    suspend fun createPurchaseOrder(body: PurchaseOrderBody): PurchaseOrder { requireWarehouse(); return api.createPurchaseOrder(body) }
    suspend fun purchaseState(id: Long, status: String): PurchaseOrder { requireWarehouse(); return api.purchaseState(id, StateBody(status)) }
    suspend fun receivePurchase(body: ReceiptBody): ReceiptResult { requireWarehouse(); return api.receivePurchase(body) }
```

### Endpoints abastecimiento

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 59–66.

```kotlin
    @GET("proveedor") suspend fun suppliers(): List<RemoteSupplier>
    @POST("proveedor") suspend fun createSupplier(@Body body: SupplierBody): RemoteSupplier
    @GET("orden-compra") suspend fun purchaseOrders(): List<PurchaseOrder>
    @POST("orden-compra") suspend fun createPurchaseOrder(@Body body: PurchaseOrderBody): PurchaseOrder
    @PATCH("orden-compra/{id}/estado") suspend fun purchaseState(@Path("id") id: Long, @Body body: StateBody): PurchaseOrder
    @GET("compra") suspend fun receipts(): List<Receipt>
    @POST("compra") suspend fun receivePurchase(@Body body: ReceiptBody): ReceiptResult
    @GET("presupuesto") suspend fun budget(): Budget
```

### Modelos abastecimiento

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 73–80.

```kotlin
data class RemoteSupplier(val id: Long, val nombre: String, val ruc: String, val contacto: String?, val telefono: String?, val estado: String?)
data class SupplierBody(val nombre: String, val ruc: String, val contacto: String, val telefono: String, val estado: String = "Activo")
data class PurchaseOrder(val id: Long, val codigo: String?, val proveedorId: Long, val producto: String, val cantidad: String?, val total: Double, val estado: String?)
data class PurchaseOrderBody(val proveedorId: Long, val producto: String, val cantidad: String, val total: Double, val usuarioId: Long)
data class Receipt(val id: Long, val ordenCompraId: Long?, val proveedorId: Long, val inventarioId: Long?, val producto: String, val cantidad: Int, val precioUnitario: Double, val total: Double)
data class ReceiptBody(val ordenCompraId: Long, val proveedorId: Long, val inventarioId: Long?, val producto: String, val cantidad: Int, val precioUnitario: Double, val total: Double, val usuarioId: Long)
data class ReceiptResult(val compra: Receipt, val inventario: InventoryItem, val productoCreado: Boolean, val stockAnterior: Int)
data class Budget(val periodo: String, val montoTotal: Double, val montoDisponible: Double)
```

## Sprint 3 · HU09 — Captura, OCR y lectura de códigos

**Flujo:** DocumentScreen → DocumentScanner.scan → ML Kit/InvoiceParser → revisión → saveDocument/saveCode → OperationsDao → SQLite; uploadDocument → Firebase Storage → markUploaded

**Guion sugerido:** «La cámara o galería entrega una imagen. ML Kit reconoce texto y códigos; el parser propone campos, el usuario los corrige y el ViewModel guarda el documento localmente. La subida es una acción separada.»

**Alcance comprobado:** El OCR se procesa en el dispositivo y el JPEG se comprime al 80 %. No existe endpoint ERP de documentos. Las reglas incluidas solo permiten usuarios/{uid}/ventas/{fileName}; no autorizan usuarios/{uid}/documentos/*.jpg. Con esas reglas, la subida de imágenes no queda habilitada. Entidad y DAO están en HU07; FileProvider en HU06.

### Captura, revisión y guardado desde UI

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Documents.kt`, líneas 1–101.

```kotlin
package pe.edu.unmsm.siderurgica.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import pe.edu.unmsm.siderurgica.*
import pe.edu.unmsm.siderurgica.data.*
import pe.edu.unmsm.siderurgica.domain.BusinessRules
import java.io.File

@Composable fun DocumentScreen(vm: ErpViewModel, state: UiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var captureUri by rememberSaveable { mutableStateOf("") }
    var imagePath by rememberSaveable { mutableStateOf("") }
    var ruc by rememberSaveable { mutableStateOf("") }; var number by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }
    var codes by rememberSaveable { mutableStateOf("") }; var rawText by rememberSaveable { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }; var error by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    fun process(uri: Uri) { scope.launch {
        processing = true; error = ""
        try {
            val result = DocumentScanner.scan(context, uri)
            imagePath = result.imagePath; ruc = result.invoice.ruc; number = result.invoice.number
            date = result.invoice.date.replace('-', '/').replace('.', '/')
            val parts = date.split('/')
            if(parts.size == 3) date = "${parts[0].padStart(2, '0')}/${parts[1].padStart(2, '0')}/${parts[2]}"
            amount = result.invoice.amount; codes = result.codes; rawText = result.rawText.take(8000)
        } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; error = "No se pudo procesar la imagen. Prueba con buena luz y todo el documento visible." }
        finally { processing = false }
    } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if(success && captureUri.isNotBlank()) process(Uri.parse(captureUri)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if(uri != null) process(uri) }
    LaunchedEffect(state.message, state.loading) {
        if(submitted && !state.loading && (state.message.startsWith("Documento revisado") || state.message.startsWith("Código revisado"))) { imagePath = ""; rawText = ""; submitted = false }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Captura, revisa y guarda", "Lee datos de facturas y códigos QR o de barras desde una imagen.")
                InfoCard("El reconocimiento se procesa en el dispositivo. Revisa los campos: una lectura automática puede contener errores.")
                Button(onClick = {
                    val file = File(context.cacheDir, "capturas/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    captureUri = uri.toString()
                    runCatching { camera.launch(uri) }.onFailure { error = "No hay una aplicación de cámara disponible. Elige una imagen." }
                }, enabled = !processing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Fotografiar documento o código") }
                OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !processing, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen del dispositivo") }
                if(processing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Leyendo texto y códigos…") }
                if(error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        item { Text("Documentos revisados (${state.documents.size})", style = MaterialTheme.typography.titleMedium) }
        if(state.documents.isEmpty()) item { EmptyState("Sin documentos guardados", "Captura una factura, corrige sus datos y confirma.") }
        items(state.documents, key = { it.localId }) { document -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(document.number, style = MaterialTheme.typography.titleLarge)
                if(document.ruc.isNotBlank()) DetailLine("RUC", document.ruc)
                DetailLine("Fecha", document.date); if(document.amount > 0) DetailLine("Monto", money(document.amount))
                if(document.code.isNotBlank()) Text("Código: ${document.code}", style = MaterialTheme.typography.bodySmall)
                StatusBadge(if(document.storagePath == null) "En dispositivo" else "En Firebase")
                OutlinedButton(onClick = { vm.uploadDocument(document) }, enabled = !state.loading && state.firebaseEmail != null && document.storagePath == null, modifier = Modifier.fillMaxWidth()) { Text("Subir imagen a Firebase") }
            }
        } }
    }
    if(imagePath.isNotBlank()) AlertDialog(onDismissRequest = { if(!state.loading) { File(imagePath).delete(); imagePath = "" } }, title = { Text("Revisar documento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val bitmap = remember(imagePath) { android.graphics.BitmapFactory.decodeFile(imagePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }
            if(bitmap != null) Image(bitmap.asImageBitmap(), "Imagen del documento", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit)
            Field(ruc, { ruc = it.filter(Char::isDigit).take(11) }, "RUC", true, ruc.isNotBlank() && !BusinessRules.validRuc(ruc))
            Field(number, { number = it }, "Número de documento")
            Field(date, { date = it }, "Fecha (dd/mm/aaaa)")
            Field(amount, { amount = it }, "Monto total", true)
            if(codes.isNotBlank()) { Text("Código detectado", style = MaterialTheme.typography.labelLarge); Text(codes) }
            TextButton(onClick = { showText = !showText }) { Text(if(showText) "Ocultar texto reconocido" else "Ver texto reconocido") }
            if(showText) Text(rawText.ifBlank { "No se detectó texto." }, style = MaterialTheme.typography.bodySmall)
            if(rawText.isBlank()) Text("No detectamos texto. Puedes corregir los campos manualmente.", color = MaterialTheme.colorScheme.error)
            InfoCard("Guardar conserva la imagen y los datos en este dispositivo. La subida a Firebase requiere tu confirmación por separado.")
        }
    }, confirmButton = { Column { TextButton(onClick = { submitted = true; vm.saveDocument(imagePath, ruc, number, date, BusinessRules.positiveAmount(amount) ?: 0.0, codes) }, enabled = !state.loading && BusinessRules.validRuc(ruc) && number.isNotBlank() && date.isNotBlank() && BusinessRules.positiveAmount(amount) != null) { Text("Confirmar factura") }
        if(codes.isNotBlank()) TextButton(onClick = { submitted = true; vm.saveCode(imagePath, codes) }, enabled = !state.loading) { Text("Guardar solo código") }
    } }, dismissButton = { TextButton(onClick = { File(imagePath).delete(); imagePath = "" }, enabled = !state.loading) { Text("Descartar") } })
}
```

### Servicio OCR, códigos y compresión

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/DocumentScanner.kt`, líneas 1–47.

```kotlin
package pe.edu.unmsm.siderurgica.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pe.edu.unmsm.siderurgica.domain.InvoiceParser
import pe.edu.unmsm.siderurgica.domain.ExtractedInvoice
import java.io.File

data class ScanResult(val imagePath: String, val invoice: ExtractedInvoice, val codes: String, val rawText: String)
object DocumentScanner {
    suspend fun scan(context: Context, uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "No se pudo leer la imagen. Prueba otra fotografía." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("No se pudo abrir la imagen.")
        val exif = context.contentResolver.openInputStream(uri).use { stream -> stream?.let { androidx.exifinterface.media.ExifInterface(it) } }
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
            val text = recognizer.process(input).await().text
            val codes = barcode.process(input).await().mapNotNull { it.rawValue }.joinToString("\n")
            val file = File(context.filesDir, "documentos/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            ScanResult(file.absolutePath, InvoiceParser.parse(text), codes, text)
        } finally { bitmap.recycle(); recognizer.close(); barcode.close() }
    }
}
```

### Modelo extraído y parser

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 27–37.

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

### Guardado local y subida a Storage

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 363–383.

```kotlin
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
```

## Anexo — piezas compartidas del flujo

Estas piezas completan la coordinación, validación, conexión y persistencia utilizadas por las historias anteriores. Room/SQLite está reproducido en HU03; OperationsDao en HU07; migración SQL en HU01. Los DTO Retrofit no son entidades de la base ERP.

### Estado, dependencias, corrutinas y tratamiento de errores

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 21–67.

```kotlin
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

```

### Carga compartida local/remota

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 175–273.

```kotlin
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

```

### Funciones auxiliares del ViewModel

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 319–320.

```kotlin
    private fun owner() = repo.requireSession().user.id.toString()
    private fun message(value: String) { mutable.value = mutable.value.copy(message = value) }
```

### Funciones auxiliares de documentos y montos

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 414–416.

```kotlin
    private fun amount(value: Double) = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    fun firebaseConfigured() = auth != null
    private fun id() = java.util.UUID.randomUUID().toString()
```

### Reglas de dominio, RUC, correo, descuentos y roles

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 6–25.

```kotlin
object BusinessRules {
    fun validRuc(value: String): Boolean {
        if (!value.matches(Regex("\\d{11}")) || value.take(2) !in setOf("10", "15", "16", "17", "20")) return false
        val weights = listOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
        val digit = (11 - weights.indices.sumOf { (value[it] - '0') * weights[it] } % 11) % 10
        return digit == value.last() - '0'
    }
    fun validEmail(value: String) = value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    fun positiveAmount(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    fun discount(quantity: Int) = when { quantity >= 100 -> 10; quantity >= 50 -> 7; quantity >= 10 -> 5; else -> 0 }
    fun canSell(role: String) = role.uppercase(Locale.ROOT) in setOf("ADMIN", "VENTAS")
    fun canWarehouse(role: String) = role.uppercase(Locale.ROOT) in setOf("ADMIN", "ALMACEN")
    fun canInventory(role: String) = canWarehouse(role) || canSell(role)
    fun stockLevel(available: Int?, threshold: Int = 10): String = when {
        available == null -> "Sin dato"
        available <= 0 -> "Agotado"
        available <= threshold -> "Bajo"
        else -> "Normal"
    }
}
```

### Configuración Retrofit, JWT y reintentos

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 59–72.

```kotlin
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
```

### Caché genérica por usuario

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 89–108.

```kotlin
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
```

### Componentes usados por las pantallas

Origen: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Components.kt`, líneas 1–81.

```kotlin
package pe.edu.unmsm.siderurgica.ui


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.edu.unmsm.siderurgica.domain.BusinessRules
import java.text.NumberFormat
import java.util.Locale

fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-PE")).format(value)
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun InfoCard(text: String, warning: Boolean = false) {
    Surface(color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (warning) Icons.Default.Warning else Icons.Default.Info, null, Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun EmptyState(title: String, description: String, icon: ImageVector = Icons.Default.List) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Icon(icon, null, Modifier.padding(20.dp).size(32.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable fun StatusBadge(status: String?) {
    val text = status?.replace('_', ' ') ?: "Sin estado"
    val warning = text.uppercase() in setOf("PENDIENTE", "BAJO", "AGOTADO", "ANULADO")
    Surface(shape = RoundedCornerShape(8.dp), color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
        Text(text.lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}
@Composable fun SearchField(value: String, onChange: (String) -> Unit, label: String = "Buscar cliente, código o producto") {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(16.dp), placeholder = { Text(label) }, leadingIcon = { Icon(Icons.Default.Search, "Buscar") },
        trailingIcon = { if (value.isNotBlank()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Limpiar búsqueda") } })
}
@Composable fun ActionRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, null)
        }
    }
}
@Composable fun Field(value: String, onChange: (String) -> Unit, label: String, numeric: Boolean = false, error: Boolean = false, hint: String? = null) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(12.dp), isError = error,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if(numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),
        supportingText = if(hint != null) ({ Text(hint) }) else null)
}
@Composable fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable fun ConfirmAction(label: String, description: String, enabled: Boolean = true, onConfirm: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(label) }, text = { Text(description) }, confirmButton = { TextButton(onClick = { confirm = false; onConfirm() }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}
```

## Verificación de extracción

Se verificó que cada bloque coincide con su rango del archivo fuente. No se modificó el código de la aplicación ni se ejecutaron pruebas Android: este entregable documenta el código presente y no certifica el funcionamiento del backend o de Firebase.
