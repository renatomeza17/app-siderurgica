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