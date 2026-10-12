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


## Tu intervención inicial

### Apertura

Buenos días. Hoy voy a recorrer los avances de nuestros tres primeros sprints siguiendo la operación real de la aplicación. Primero veremos cómo se inicia y protege la sesión; después, cómo una cotización llega a pedido y venta; y finalmente, cómo gestionamos inventario, abastecimiento y captura de documentos.

En cada recorrido voy a partir de una acción del usuario y seguir la llamada hasta el componente que la resuelve. Después regresaré con la respuesta a la pantalla. Así podremos relacionar cada historia con una necesidad de negocio y con evidencia concreta en el código.

Nuestra aplicación es Android nativo con Kotlin y Jetpack Compose. Aquí el punto de entrada habitual es un evento de interfaz. El ViewModel coordina, el repositorio consume el ERP y Room conserva información en SQLite. Cuando lleguemos a Retrofit, veremos el contrato que usa la aplicación: el código del servidor ERP y sus transacciones no están en este repositorio. Por eso voy a separar lo que podemos demostrar en el cliente de lo que necesitamos validar en integración.

## Persona 1 · Sprint 1 · HU01: Base MVVM, Clean Code y Material Design 3

Comienzo por HU01. La necesidad es tener una base consistente para que ventas, almacén y administración utilicen los mismos componentes y para que la lógica de una operación no quede mezclada con el dibujo de la pantalla. Voy a mostrarlo desde el arranque de Android, antes de entrar a una operación de negocio.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 21–31.*

```kotlin
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
```

Cuando Android crea la actividad, yo instalo el contenido Compose y entro a SiderurgicaScreen. No consulto una base de datos desde onCreate. Paso al componente que recibe el ViewModel y observa su estado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 30–32.*

```kotlin
@Composable fun SiderurgicaScreen(activity: MainActivity, vm: ErpViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
```

Aquí recojo el StateFlow respetando el ciclo de vida. Para entender qué está observando la pantalla, salto inmediatamente al ViewModel: allí se resuelve el repositorio y se construye el estado inicial con la sesión guardada.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 32–38.*

```kotlin

class ErpViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as SiderurgicaApp).repository
    private val operations = (app as SiderurgicaApp).db.operations()
    private val auth = if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val mutable = MutableStateFlow(UiState(session = repo.vault.read(), firebaseEmail = auth?.currentUser?.email))
    val state = mutable.asStateFlow()
```

El repositorio y la base no se crean en cada recomposición. Los proporciona nuestra clase Application mediante propiedades lazy. Sigo esa referencia para ver cómo se instancian las dependencias.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/SiderurgicaApp.kt`, líneas 10–13.*

```kotlin
class SiderurgicaApp : Application() {
    val repository by lazy { ErpRepository(this) }
    val db by lazy { Room.databaseBuilder(this, LocalStore::class.java, "siderurgica-mobile.db").addMigrations(OperationsMigration).build() }
}
```

La base se llama siderurgica-mobile.db y Room administra su acceso. Esta es provisión manual de dependencias; no estamos mostrando un contenedor de inyección. Del lado remoto, el repositorio prepara Retrofit, añade el token Bearer si hay sesión y define un tiempo máximo de llamada.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 65–72.*

```kotlin
    private val dao = (context.applicationContext as SiderurgicaApp).db.dao()
    private val api: ErpApi = Retrofit.Builder().baseUrl(BuildConfig.ERP_API_URL)
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(75, java.util.concurrent.TimeUnit.SECONDS).addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            vault.read()?.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }.build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ErpApi::class.java)
```

Vuelvo ahora al dibujo de la interfaz. El componente raíz envuelve la navegación con SteelTheme; sigo ese nombre hasta el tema que aplica Material 3 y selecciona la paleta según el modo del sistema.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 71–71.*

```kotlin
    SteelTheme {
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/Theme.kt`, líneas 27–29.*

```kotlin
@Composable fun SteelTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
```

Con esto cierro HU01: tenemos una entrada Compose, estado observable, coordinación en ViewModel, acceso a datos mediante repositorio y base local Room. También tenemos tema claro y oscuro con Material 3. Esta historia establece una separación verificable de responsabilidades; no implica que ya tengamos todas las pruebas de accesibilidad o una arquitectura Clean completa certificada. Ahora voy a usar esta misma estructura para seguir la primera acción del usuario: ingresar.

## Persona 1 · Sprint 1 · HU02: Autenticación, sesión y biometría

HU02 resuelve el acceso seguro. Necesitamos identificar al usuario, recuperar su sesión y limitar sus operaciones por rol. El recorrido comienza cuando presiono Ingresar; el mismo botón cambia a registro cuando activo Crear cuenta.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/HomeAndLogin.kt`, líneas 62–66.*

```kotlin
                if (register) {
                    vm.registerUnificado(identifier, password)
                } else {
                    vm.loginUnificado(identifier, password)
                }
```

En el caso de ingreso llamamos a loginUnificado. Antes de ejecutar su cuerpo, la función entra al helper run. Sigo ese helper: evitamos lanzar otra operación mientras existe una carga activa y usamos viewModelScope para ejecutar la corrutina.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 46–50.*

```kotlin
    private fun run(action: suspend () -> Unit) {
        if (mutable.value.loading) return
        active = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, message = "")
            try { action() }
```

Ahora vuelvo al cuerpo del login. Recibimos el identificador y la contraseña, quitamos espacios del correo y exigimos ambos campos. Si Firebase está configurado, validamos que el identificador tenga formato de correo.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 68–80.*

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
```

La validación invoca BusinessRules.validEmail, así que sigo esa llamada antes de avanzar. Aquí se verifica el formato; no estamos comprobando todavía la existencia de la cuenta.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 13–13.*

```kotlin
    fun validEmail(value: String) = value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
```

Superado ese control, extraigo el prefijo del correo como nombre de usuario ERP. Por ejemplo, admin@siderurgica.com se convierte en admin. En este flujo se usa la misma contraseña para el ERP y Firebase. La primera llamada de autenticación es al ERP.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 82–88.*

```kotlin
        try {
            val session = repo.login(username, pass)

            auth?.signInWithEmailAndPassword(email, pass)?.await()

            mutable.value = mutable.value.copy(
                session = session,
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 74–80.*

```kotlin
    suspend fun login(username: String, password: String): ErpSession {
        val result = api.login(LoginBody(username.trim(), password))
        require(result.success && result.usuario != null && !result.accessToken.isNullOrBlank()) {
            result.mensaje ?: "No se pudo acceder al ERP"
        }
        return ErpSession(result.usuario, result.accessToken).also(vault::save)
    }
```

Aquí enviamos LoginBody y comprobamos tres condiciones de la respuesta: éxito, usuario presente y token no vacío. Para ver adónde va la solicitud salto al contrato Retrofit.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 9–10.*

```kotlin
data class LoginBody(val username: String, val password: String)
data class LoginResult(val success: Boolean, val mensaje: String?, val usuario: ErpUser?, val accessToken: String?)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 45–45.*

```kotlin
    @POST("login") suspend fun login(@Body body: LoginBody): LoginResult
```

Este POST termina en el servidor ERP. Sus consultas de usuarios y comprobación de contraseña son externas a este código. Si recibimos una respuesta válida, also(vault::save) persiste la sesión; por eso sigo inmediatamente al almacén cifrado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 41–45.*

```kotlin
    fun save(session: ErpSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(gson.toJson(session).toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("data", Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)).apply()
    }
```

Convertimos la sesión a JSON y la ciframos con AES-GCM. La llave la obtenemos desde Android Keystore; no guardamos la contraseña ERP. Sigo key para mostrar dónde se crea o recupera esa llave.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 31–40.*

```kotlin
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
```

Después del ERP regresamos al ViewModel y autenticamos Firebase. Solo entonces actualizamos el estado visible con la sesión y el correo Firebase.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 86–90.*

```kotlin

            mutable.value = mutable.value.copy(
                session = session,
                firebaseEmail = auth?.currentUser?.email
            )
```

Si Firebase o el ERP fallan durante esa secuencia, deshacemos la sesión guardada y cerramos Firebase para no dejar una identidad parcial en la interfaz.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 91–100.*

```kotlin
        } catch (e: Exception) {
            repo.vault.clear()
            auth?.signOut()

            mutable.value = mutable.value.copy(
                session = null,
                firebaseEmail = null
            )

            when (e) {
```

En registro hay una condición de negocio que debemos dejar clara: primero verificamos que la cuenta ya exista en el ERP y después creamos su acceso Firebase. No estamos creando una cuenta empresarial desde el móvil.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 143–159.*

```kotlin
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
```

Para recuperar el acceso, el evento de pantalla invoca resetPassword. Sigo esa llamada: validamos el correo y solicitamos a Firebase el envío de recuperación. El cierre cancela la operación activa, borra el token local y cierra Firebase.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/HomeAndLogin.kt`, líneas 80–80.*

```kotlin
            TextButton(onClick = { vm.resetPassword(identifier) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Recuperar contraseña Firebase") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 168–173.*

```kotlin
    fun resetPassword(email: String) = run {
        require(BusinessRules.validEmail(email)) { "Ingresa tu correo para recuperar el acceso." }
        (auth ?: error("La recuperación Firebase no está disponible.")).sendPasswordResetEmail(email.trim()).await()
        mutable.value = mutable.value.copy(message = "Revisa tu correo para recuperar la contraseña.")
    }
    fun logout() { active?.cancel(); repo.vault.clear(); auth?.signOut(); mutable.value = UiState(message = "Sesión cerrada.") }
```

La recuperación corresponde a Firebase; no hay una recuperación ERP en este contrato. Al reabrir, SessionVault.read descifra la sesión para restaurar el estado. Si falla la lectura, limpia el almacén y devuelve null.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 46–56.*

```kotlin
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
```

Si el usuario habilitó biometría y el dispositivo la soporta, la pantalla bloqueada ofrece desbloquear. La confirmación del sensor cambia unlocked y deja continuar con la sesión previamente guardada.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 98–101.*

```kotlin
                            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
                            })
                            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Abrir Siderúrgica Perú").setSubtitle("Confirma tu identidad").setNegativeButtonText("Cancelar").build())
```

Para las operaciones comerciales aplicamos además una barrera de rol en el repositorio. Sigo requireSales hasta su regla de dominio.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 81–85.*

```kotlin
    fun requireSales(): ErpSession {
        val s = vault.read() ?: error("Vincula una cuenta del ERP")
        require(BusinessRules.canSell(s.user.rol)) { "Tu rol no permite operar ventas" }
        return s
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 16–18.*

```kotlin
    fun canSell(role: String) = role.uppercase(Locale.ROOT) in setOf("ADMIN", "VENTAS")
    fun canWarehouse(role: String) = role.uppercase(Locale.ROOT) in setOf("ADMIN", "ALMACEN")
    fun canInventory(role: String) = canWarehouse(role) || canSell(role)
```

Cierro HU02 con ingreso, registro condicionado a una cuenta ERP, recuperación Firebase, cierre, sesión cifrada, roles y desbloqueo biométrico opcional. La interfaz controla acceso local, pero la autorización definitiva debe seguir existiendo en el servidor. La persistencia de sesión tampoco garantiza que un JWT siga vigente: un 401 debe tratarse como vencimiento. Con una identidad estable, paso a cómo mantenemos información útil cuando falla la conexión.

**Persona 1 — pase de palabra:**

Con la sesión y los permisos explicados, le paso la palabra a mi compañero para mostrar cómo conservamos información y preparamos operaciones cuando se pierde la conexión.

*Recibes la palabra después de HU03. Continúa con el recorrido siguiente.*

## Persona 1 · Sprint 2 · HU04: Cotizaciones desde el móvil

En HU04 el vendedor necesita preparar una propuesta, consultar sus importes y encontrarla por cliente, producto o estado. El punto de entrada es Confirmar cotización en CommercialForm. Antes de presionar, calculamos el resumen visible.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 140–142.*

```kotlin
    val n = quantity.toIntOrNull() ?: 0; val unit = BusinessRules.positiveAmount(price) ?: 0.0
    val subtotal = n * unit; val discount = if(quote) BusinessRules.discount(n) else 0
    val base = subtotal * (100 - discount) / 100; val tax = base * .18
```

La cantidad y el precio producen el subtotal. Si es cotización, llamamos a discount; salto a esa regla para ver la escala aplicada.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 15–15.*

```kotlin
    fun discount(quantity: Int) = when { quantity >= 100 -> 10; quantity >= 50 -> 7; quantity >= 10 -> 5; else -> 0 }
```

Aplicamos 5 %, 7 % o 10 % desde los umbrales de diez, cincuenta y cien unidades. La pantalla calcula también IGV referencial al 18 %, pero presenta la cotización sin IGV y el pedido con IGV. El importe definitivo lo devuelve el ERP.

Al confirmar, submit prepara QuoteCreate con cliente, RUC, producto, cantidad, inventario seleccionado, precio y usuario. Sigo inmediatamente la llamada saveQuote.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 195–195.*

```kotlin
                if(quote) vm.saveQuote(QuoteCreate(client.trim(), ruc.ifBlank { null }, product.trim(), n, selectedId, unit, state.session!!.user.id), draft)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 282–286.*

```kotlin
    fun saveQuote(body: QuoteCreate, asDraft: Boolean = false) = run {
        repo.requireSales(); validate(body.cliente, body.producto, body.cantidad, body.precioUnitario, body.ruc)
        if (asDraft) { repo.saveDraft("QUOTE", repo.gson().toJson(body)); localData(); message("Borrador guardado.") }
        else { repo.createQuote(body); message("Cotización registrada."); mutable.value = mutable.value.copy(quotes = repo.quotes()) }
    }
```

Primero exigimos rol comercial y validamos los campos. requireSales ya vimos que permite ADMIN o VENTAS; ahora sigo validate y su verificador de RUC.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 292–296.*

```kotlin
    private fun validate(client: String, product: String, quantity: Int, price: Double, ruc: String?) {
        require(client.isNotBlank() && product.isNotBlank()) { "Completa el cliente y el producto." }
        require(quantity > 0 && price.isFinite() && price > 0 && (quantity * price).isFinite()) { "Cantidad y precio deben ser mayores a cero." }
        require(ruc.isNullOrBlank() || BusinessRules.validRuc(ruc)) { "Revisa el RUC: debe tener 11 dígitos y ser válido." }
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 7–12.*

```kotlin
    fun validRuc(value: String): Boolean {
        if (!value.matches(Regex("\\d{11}")) || value.take(2) !in setOf("10", "15", "16", "17", "20")) return false
        val weights = listOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
        val digit = (11 - weights.indices.sumOf { (value[it] - '0') * weights[it] } % 11) % 10
        return digit == value.last() - '0'
    }
```

Aquí no basta con once dígitos: comprobamos el prefijo y el dígito de control del RUC. También rechazamos cantidades o precios no positivos y resultados no finitos. Con esto aprobado, regreso a createQuote en el repositorio.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 128–134.*

```kotlin
    suspend fun createQuote(body: QuoteCreate): Quote {
        val session = requireSales()
        require(body.usuarioId == session.user.id) { "Usuario no válido" }
        val response = api.createQuote(body)
        require(response.success) { response.mensaje ?: "No se creó la cotización" }
        return response.cotizacion ?: error(response.mensaje ?: "No se creó la cotización")
    }
```

Verificamos que el usuario del payload coincida con la sesión, llamamos a la API y exigimos una respuesta exitosa con cotización presente. Sigo el DTO y la ruta concreta de esa llamada.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 20–25.*

```kotlin
data class QuoteCreate(
    val cliente: String, val ruc: String?, val producto: String, val cantidad: Int,
    val inventarioId: Long?, @SerializedName("precio_unitario") val precioUnitario: Double,
    @SerializedName("usuario_id") val usuarioId: Long
)
data class QuoteCreated(val success: Boolean, val mensaje: String?, val cotizacion: Quote?)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 47–47.*

```kotlin
    @POST("cotizaciones") suspend fun createQuote(@Body body: QuoteCreate): QuoteCreated
```

Los SerializedName adaptan los campos al contrato JSON. Retrofit ejecuta POST cotizaciones y devuelve QuoteCreated. La inserción en la base ERP ocurre fuera del repositorio móvil; aquí no tenemos su SQL.

Cuando retorna, el ViewModel publica el mensaje y vuelve a consultar cotizaciones. Sigo esa lectura hasta el GET y después el reemplazo local que ya recorrimos en HU03.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 90–90.*

```kotlin
    suspend fun quotes(force: Boolean = true): List<Quote> { requireSales(); return fetch("quote", force, { api.quotes() }, Quote::id) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 46–46.*

```kotlin
    @GET("cotizaciones") suspend fun quotes(): List<Quote>
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 28–28.*

```kotlin
    val quotes = state.quotes.filter { matches(search, it.cliente, it.codigo, it.producto) && (filter == "Todos" || it.estado == filter) }
```

El listado actualizado entra al StateFlow y la pantalla filtra por texto y estado. Así cerramos el circuito: formulario, validación, POST, GET, caché y listado.

Cuando presiono Aprobar cotización, no hago una inserción directa de pedido desde la pantalla. Invoco approveQuote, que delega el cambio de estado y refresca cotizaciones y pedidos.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 57–57.*

```kotlin
                                if(quote.estado == "PENDIENTE") Button(onClick = { confirm = Confirmation("¿Aprobar cotización?", "${quote.cliente} · ${money(quote.total ?: 0.0)}. Después podrás crear su pedido.") { vm.approveQuote(quote.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Aprobar cotización") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 308–311.*

```kotlin
    fun approveQuote(id: Long) = run {
        repo.quoteState(id, "APROBADA"); message("Cotización aprobada.")
        mutable.value = mutable.value.copy(quotes = repo.quotes(), orders = repo.orders())
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 145–149.*

```kotlin
    suspend fun quoteState(id: Long, state: String) {
        requireSales(); val response = api.quoteState(id, StateBody(state))
        if (!response.isSuccessful) error("No se cambió el estado (${response.code()}). Actualiza el listado antes de volver a confirmar.")
        response.body()?.close()
    }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 48–48.*

```kotlin
    @PATCH("cotizaciones/{id}/estado") suspend fun quoteState(@Path("id") id: Long, @Body body: StateBody): Response<ResponseBody>
```

Luego, para una cotización APROBADA, la UI ofrece Crear pedido. Sigo esa acción hasta convertQuote y su endpoint específico.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 58–58.*

```kotlin
                                if(quote.estado == "APROBADA") Button(onClick = { confirm = Confirmation("¿Crear pedido?", "Se convertirá esta cotización aprobada en un pedido.") { vm.convertQuote(quote.id) } }, enabled = !state.loading && !state.offline, modifier = Modifier.fillMaxWidth()) { Text("Crear pedido") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 316–317.*

```kotlin
    fun convertQuote(id: Long) = run { repo.convertQuote(id); message("Pedido creado desde cotización."); mutable.value = mutable.value.copy(quotes = repo.quotes(), orders = repo.orders()) }
    fun rejectQuote(id: Long) = run { repo.quoteState(id, "RECHAZADA"); message("Cotización rechazada."); mutable.value = mutable.value.copy(quotes = repo.quotes()) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 139–139.*

```kotlin
    suspend fun convertQuote(id: Long): Order { requireSales(); return api.convertQuote(id).pedido }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 49–49.*

```kotlin
    @POST("cotizaciones/{id}/convertir-pedido") suspend fun convertQuote(@Path("id") id: Long): ConvertedOrder
```

Para rechazar, rejectQuote reutiliza quoteState con RECHAZADA. Concluyo HU04 con alta, consulta, búsqueda, filtros, descuento y transición comercial presentes. Trabajamos con un producto por operación, no con un carrito de líneas. También debo señalar que el README anterior habla de conversión automática al aprobar, mientras el código actual presenta aprobación y conversión separadas. En la demo sigo el código y dejo ese comportamiento del servidor como verificación de integración.

**Persona 1 — pase de palabra:**

Con la cotización registrada y su conversión recorrida, mi compañero continuará con los estados del pedido y la generación de la venta.

*Recibes la palabra después de HU05. Continúa con el recorrido siguiente.*

## Persona 1 · Sprint 2 · HU06: PDF, vista previa, compartir y Storage

HU06 busca que el usuario tenga una constancia digital de la venta. El recorrido parte de la tarjeta de venta y su botón Vista previa PDF o Compartir constancia. Aquí hay una particularidad de arquitectura: la pantalla llama directamente al servicio SalePdf; no pasa por un método del ViewModel.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 122–123.*

```kotlin
            OutlinedButton(onClick = { pdfAction(true) }, modifier = Modifier.fillMaxWidth()) { Text("Vista previa PDF") }
            Button(onClick = { pdfAction(false) }, modifier = Modifier.fillMaxWidth()) { Text("Compartir constancia") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 110–118.*

```kotlin
    fun pdfAction(preview: Boolean) {
        runCatching {
            val file = SalePdf.create(context, sale)
            if(!preview) SalePdf.share(context, file) else {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }.onFailure { result = "No se pudo abrir el PDF. Instala un visor o usa Compartir." }
    }
```

Ambas acciones entran a pdfAction y crean el archivo. Sigo SalePdf.create: ubicamos el PDF en el directorio de caché y abrimos un documento de una página.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/SalePdf.kt`, líneas 20–27.*

```kotlin
    fun create(context: Context, sale: Sale): File {
        val file = File(context.cacheDir, "comprobantes/venta-${sale.id}.pdf")
        file.parentFile?.mkdirs()
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = android.graphics.Color.BLACK }
```

Con el modelo Sale ya obtenido del ERP dibujamos cliente, RUC, producto, cantidad e importes. El PDF no vuelve a calcular el total; representa los valores de la venta.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/SalePdf.kt`, líneas 35–47.*

```kotlin
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
```

Al finalizar escribimos el documento al archivo y lo devolvemos al llamador. Si la acción era vista previa, pdfAction entrega una URI FileProvider a ACTION_VIEW. Para compartir sigo share, que usa ACTION_SEND y permite leer temporalmente la URI.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/SalePdf.kt`, líneas 49–55.*

```kotlin
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartir constancia"))
    }
```

Así dejamos que Android muestre sus destinos nativos de compartición. Para almacenar el PDF en Firebase, presiono Subir a Firebase. El evento lanza una corrutina y llama upload con el archivo recién creado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/SalesScreen.kt`, líneas 126–126.*

```kotlin
                try { SalePdf.upload(context, sale, SalePdf.create(context, sale)); result = "Constancia subida a Firebase." }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/SalePdf.kt`, líneas 56–63.*

```kotlin
    suspend fun upload(context: Context, sale: Sale, file: File) {
        require(FirebaseApp.getApps(context).isNotEmpty()) { "Configura Firebase primero" }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Inicia sesión en Firebase")
        val uri = android.net.Uri.fromFile(file)
        val metadata = StorageMetadata.Builder().setContentType("application/pdf").build()
        FirebaseStorage.getInstance().reference.child("usuarios/$uid/ventas/${sale.id}.pdf").putFile(uri, metadata).await()
    }
}
```

Tomamos el uid de Firebase, definimos application/pdf y subimos a usuarios/uid/ventas/id.pdf. La autorización de ese destino se ve en las reglas incluidas; sigo inmediatamente ese control.

*En pantalla: `firebase.storage.rules`, líneas 4–10.*

```text
    match /usuarios/{uid}/ventas/{fileName} {
      allow read: if request.auth != null && request.auth.uid == uid;
      allow write: if request.auth != null
        && request.auth.uid == uid
        && request.resource.size < 10 * 1024 * 1024
        && request.resource.contentType == 'application/pdf';
    }
```

Solo el usuario autenticado correspondiente puede leer o escribir en esa ruta, y la escritura exige tamaño menor a diez megabytes y tipo PDF. La pantalla comunica el resultado y sale del estado Subiendo en finally.

Cierro HU06 con generación, apertura mediante visor externo, compartir y subida de una constancia informativa. El criterio de boleta o factura tributaria todavía no queda satisfecho: el propio documento dice que no es comprobante de pago tributario. No hay numeración fiscal ni integración de emisión en este código. Con las operaciones comerciales recorridas, paso al inventario que las sostiene.

## Persona 1 · Sprint 3 · HU07: Inventario, movimientos y alertas de stock

HU07 atiende una pregunta operativa: qué podemos vender y cuándo necesitamos reponer. Entramos al módulo Stock; el refresco pide inventario al repositorio y la pantalla usa el disponible o, si falta, el total.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/MainActivity.kt`, líneas 113–113.*

```kotlin
                        "stock" -> InventoryScreen(vm, state)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 231–233.*

```kotlin
            load("Inventario") {
                mutable.value = mutable.value.copy(inventory = repo.inventory())
            }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 93–93.*

```kotlin
    suspend fun inventory(force: Boolean = true): List<InventoryItem> { requireInventory(); return fetch("inventory", force, { api.inventory() }, InventoryItem::id) }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 55–55.*

```kotlin
    @GET("inventario") suspend fun inventory(): List<InventoryItem>
```

Esta lectura pasa por fetch: al retornar del ERP reemplaza la caché por usuario. Con los datos ya publicados, la tarjeta invoca stockLevel y la regla clasifica normal, bajo, agotado o sin dato.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 44–44.*

```kotlin
                        StatusBadge(BusinessRules.stockLevel(available, item.stockMinimo ?: 10))
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/BusinessRules.kt`, líneas 19–24.*

```kotlin
    fun stockLevel(available: Int?, threshold: Int = 10): String = when {
        available == null -> "Sin dato"
        available <= 0 -> "Agotado"
        available <= threshold -> "Bajo"
        else -> "Normal"
    }
```

El umbral viene del stock mínimo del producto; usamos diez como respaldo cuando falta. Para modificar existencias, el botón Registrar movimiento abre un diálogo. Allí selecciono entrada, salida o ajuste y confirmo.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ui/InventoryAndPurchases.kt`, líneas 72–72.*

```kotlin
        TextButton(onClick = { submitted = true; vm.saveMovement(item, kind, quantity.toIntOrNull() ?: 0, reason) }, enabled = valid && !state.loading && !state.offline) { Text("Confirmar en ERP") }
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 351–362.*

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

Solo ALMACEN o ADMIN pueden operar. Validamos el tipo, cantidad y motivo y comparamos una salida con el último disponible conocido. Si guardo localmente, inserto StockMovement; sigo esa entidad y el DAO.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 9–10.*

```kotlin
@Entity(tableName = "movements")
data class StockMovement(@PrimaryKey val localId: String, val owner: String, val inventoryId: Long, val product: String, val kind: String, val quantity: Int, val reason: String, val createdAt: Long)
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/Operations.kt`, líneas 23–23.*

```kotlin
    @Insert suspend fun addMovement(value: StockMovement)
```

Esta inserción deja una solicitud pendiente en SQLite; no altera el stock ERP. Si confirmo en línea, seguimos changeStock. Un ajuste establece un valor final; una entrada o salida expresa una cantidad de movimiento.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 118–126.*

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

Para AJUSTE consultamos primero el producto remoto y evitamos bajar el total por debajo del stock reservado conocido. Después hacemos PUT. Para entrada o salida hacemos PATCH con StockBody; sigo ambos contratos.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 56–58.*

```kotlin
    @GET("inventario/{id}") suspend fun inventoryItem(@Path("id") id: Long): InventoryItem
    @PATCH("inventario/{id}/stock") suspend fun changeStock(@Path("id") id: Long, @Body body: StockBody): StockResult
    @PUT("inventario/{id}") suspend fun adjustStock(@Path("id") id: Long, @Body body: InventoryUpdate): StockResult
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpApi.kt`, líneas 70–72.*

```kotlin
data class StockBody(val tipo: String, val cantidad: Int)
data class StockResult(val success: Boolean, val mensaje: String?, val inventario: InventoryItem?)
data class InventoryUpdate(val producto: String, val categoria: String?, val stock: Int, val stockMinimo: Int?, val precioUnitario: Double?, val unidad: String?, val stockObjetivo: Int?)
```

Comprobamos success y al retornar volvemos a consultar inventario. El motivo queda en el borrador local, pero no existe un campo de motivo en StockBody, de modo que no se envía al ERP.

Para enviar una solicitud local, seguimos sendMovement: ejecutamos el mismo servicio, eliminamos el borrador tras la respuesta y actualizamos el listado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/ErpViewModel.kt`, líneas 398–402.*

```kotlin
    fun sendMovement(movement: StockMovement) = run {
        require(movement.owner == owner()) { "Solicitud no válida." }; repo.requireWarehouse()
        repo.changeStock(movement.inventoryId, movement.kind, movement.quantity); operations.deleteMovement(movement.localId, owner()); localData()
        mutable.value = mutable.value.copy(inventory = repo.inventory()); message("Movimiento enviado al ERP.")
    }
```

La otra rama de HU07 es la alerta. El CacheSyncWorker que mostramos en HU03 obtiene stock y, si no utilizó caché, llama StockNotifications.check. Sigo esa llamada: guardamos el nivel anterior y notificamos cuando el producto pasa a Bajo o Agotado.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/data/ErpRepository.kt`, líneas 171–171.*

```kotlin
            if (!repo.usedCache) pe.edu.unmsm.siderurgica.StockNotifications.check(applicationContext, stock, repo.vault.read()!!.user.id.toString())
```

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/StockNotifications.kt`, líneas 27–37.*

```kotlin
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
```

check invoca show, que comprueba el permiso de notificaciones en Android 13 o posterior, crea el canal y publica la notificación local.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/StockNotifications.kt`, líneas 18–26.*

```kotlin
    fun show(context: Context, title: String, body: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alertas de inventario", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = android.content.Intent(context, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(context, id, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).build())
    }
```

También existe un receptor FCM. Si llega un mensaje, verifica la sesión y el rol y pide abrir la app para consultar el inventario vigente.

*En pantalla: `app/src/main/java/pe/edu/unmsm/siderurgica/domain/StockNotifications.kt`, líneas 41–52.*

```kotlin
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

Concluyo HU07 con consulta, clasificación visual, movimientos, solicitudes locales y alertas periódicas en el dispositivo. La recepción FCM está implementada, pero el token solo se conserva localmente: no hay endpoint para registrarlo en el ERP ni emisor de alertas del servidor. Por tanto, el circuito push completo queda pendiente. Desde esta necesidad de reposición entramos a proveedores y órdenes de compra.

**Persona 1 — pase de palabra:**

Acabamos de recorrer el stock y sus alertas. Mi compañero continuará con la reposición: proveedor, orden de compra y recepción; después mostrará cómo capturamos y revisamos los documentos.

### Notas para conducir la demo

Antes de comenzar, preparar cuentas ERP con los roles usados y, cuando corresponda, accesos Firebase compatibles con el flujo unificado; configurar la URL ERP y conectividad. Para probar offline, cargar primero datos en línea y después cortar conexión. Una solicitud guardada no debe confundirse con stock modificado ni un borrador con una operación ERP confirmada.

Mostrar el PDF como constancia informativa. Mostrar FCM como receptor disponible y las alertas periódicas como notificaciones locales. Para HU09, demostrar captura y guardado local; presentar la subida como pendiente si se utilizan las reglas incluidas. Ante una respuesta perdida de escritura, comprobar el listado antes de reenviar. Estas indicaciones mantienen la demo alineada con el alcance descrito.
