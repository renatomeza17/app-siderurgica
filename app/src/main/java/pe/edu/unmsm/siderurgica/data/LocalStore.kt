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
