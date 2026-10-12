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
