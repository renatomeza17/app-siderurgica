package pe.edu.unmsm.siderurgica.domain


import java.util.Locale

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
