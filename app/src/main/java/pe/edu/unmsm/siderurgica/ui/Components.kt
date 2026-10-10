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
