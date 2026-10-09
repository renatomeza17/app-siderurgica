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
