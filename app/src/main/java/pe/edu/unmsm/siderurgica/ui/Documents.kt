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
