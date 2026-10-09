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
    @SerializedName("precio_unitario") val precioUnitario: Double = 0.0,
    val subtotal: Double? = null, @SerializedName("descuento_porcentaje") val descuentoPorcentaje: Int? = null,
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
    @SerializedName("precio_unitario") val precioUnitario: Double = 0.0,
    val subtotal: Double? = null, val igv: Double? = null, val total: Double? = null,
    val estado: String? = null, val estadoStock: String? = null,
    @SerializedName("usuario_id") val usuarioId: Long? = null
)
data class Sale(
    val id: Long = 0, val codigo: String? = null, val cliente: String = "", val ruc: String? = null,
    val producto: String = "", val cantidad: Int = 0, val precioUnitario: Double = 0.0,
    val subtotal: Double = 0.0, val igv: Double = 0.0, val total: Double = 0.0,
    val estado: String? = null, val pedidoId: Long? = null
)
data class InventoryItem(val id: Long, val producto: String?, val stock: Int?, val stockDisponible: Int?, val precioUnitario: Double?)
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
}
