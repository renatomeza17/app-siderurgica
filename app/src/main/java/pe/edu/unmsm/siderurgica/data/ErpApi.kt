package pe.edu.unmsm.siderurgica.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.*

data class ErpUser(val id: Long, val username: String, val rol: String, val estado: String?)
data class LoginBody(val username: String, val password: String)
data class LoginResult(val success: Boolean, val mensaje: String?, val usuario: ErpUser?, val accessToken: String?)

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

interface ErpApi {
    @POST("login") suspend fun login(@Body body: LoginBody): LoginResult
    @GET("cotizaciones") suspend fun quotes(): List<Quote>
    @POST("cotizaciones") suspend fun createQuote(@Body body: QuoteCreate): QuoteCreated
    @PATCH("cotizaciones/{id}/estado") suspend fun quoteState(@Path("id") id: Long, @Body body: StateBody): Response<ResponseBody>
    @POST("cotizaciones/{id}/convertir-pedido") suspend fun convertQuote(@Path("id") id: Long): ConvertedOrder
    @GET("pedidos") suspend fun orders(): List<Order>
    @POST("pedidos") suspend fun createOrder(@Body body: Order): Order
    @PATCH("pedidos/{id}/estado") suspend fun orderState(@Path("id") id: Long, @Body body: StateBody): Response<ResponseBody>
    @GET("ventas") suspend fun sales(): List<Sale>
    @POST("ventas/desde-pedido/{id}") suspend fun saleFromOrder(@Path("id") id: Long): Sale
    @GET("inventario") suspend fun inventory(): List<InventoryItem>
    @GET("inventario/{id}") suspend fun inventoryItem(@Path("id") id: Long): InventoryItem
    @PATCH("inventario/{id}/stock") suspend fun changeStock(@Path("id") id: Long, @Body body: StockBody): StockResult
    @PUT("inventario/{id}") suspend fun adjustStock(@Path("id") id: Long, @Body body: InventoryUpdate): StockResult
    @GET("proveedor") suspend fun suppliers(): List<RemoteSupplier>
    @POST("proveedor") suspend fun createSupplier(@Body body: SupplierBody): RemoteSupplier
    @GET("orden-compra") suspend fun purchaseOrders(): List<PurchaseOrder>
    @POST("orden-compra") suspend fun createPurchaseOrder(@Body body: PurchaseOrderBody): PurchaseOrder
    @PATCH("orden-compra/{id}/estado") suspend fun purchaseState(@Path("id") id: Long, @Body body: StateBody): PurchaseOrder
    @GET("compra") suspend fun receipts(): List<Receipt>
    @POST("compra") suspend fun receivePurchase(@Body body: ReceiptBody): ReceiptResult
    @GET("presupuesto") suspend fun budget(): Budget
}


data class StockBody(val tipo: String, val cantidad: Int)
data class StockResult(val success: Boolean, val mensaje: String?, val inventario: InventoryItem?)
data class InventoryUpdate(val producto: String, val categoria: String?, val stock: Int, val stockMinimo: Int?, val precioUnitario: Double?, val unidad: String?, val stockObjetivo: Int?)
data class RemoteSupplier(val id: Long, val nombre: String, val ruc: String, val contacto: String?, val telefono: String?, val estado: String?)
data class SupplierBody(val nombre: String, val ruc: String, val contacto: String, val telefono: String, val estado: String = "Activo")
data class PurchaseOrder(val id: Long, val codigo: String?, val proveedorId: Long, val producto: String, val cantidad: String?, val total: Double, val estado: String?)
data class PurchaseOrderBody(val proveedorId: Long, val producto: String, val cantidad: String, val total: Double, val usuarioId: Long)
data class Receipt(val id: Long, val ordenCompraId: Long?, val proveedorId: Long, val inventarioId: Long?, val producto: String, val cantidad: Int, val precioUnitario: Double, val total: Double)
data class ReceiptBody(val ordenCompraId: Long, val proveedorId: Long, val inventarioId: Long?, val producto: String, val cantidad: Int, val precioUnitario: Double, val total: Double, val usuarioId: Long)
data class ReceiptResult(val compra: Receipt, val inventario: InventoryItem, val productoCreado: Boolean, val stockAnterior: Int)
data class Budget(val periodo: String, val montoTotal: Double, val montoDisponible: Double)