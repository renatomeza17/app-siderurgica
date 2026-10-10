package pe.edu.unmsm.siderurgica

import android.app.Application
import androidx.room.Room
import pe.edu.unmsm.siderurgica.data.LocalStore
import pe.edu.unmsm.siderurgica.data.ErpRepository
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class SiderurgicaApp : Application() {
    val repository by lazy { ErpRepository(this) }
    val db by lazy { Room.databaseBuilder(this, LocalStore::class.java, "siderurgica-mobile.db").addMigrations(OperationsMigration).build() }
}

private object OperationsMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS suppliers (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL, ruc TEXT NOT NULL, contact TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, serverId INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_owner_ruc ON suppliers(owner, ruc)")
        db.execSQL("CREATE TABLE IF NOT EXISTS purchases (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, supplierId TEXT NOT NULL, supplierName TEXT NOT NULL, product TEXT NOT NULL, quantity INTEGER NOT NULL, unitPrice REAL NOT NULL, budget REAL NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS movements (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, inventoryId INTEGER NOT NULL, product TEXT NOT NULL, kind TEXT NOT NULL, quantity INTEGER NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS documents (localId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, imagePath TEXT NOT NULL, ruc TEXT NOT NULL, number TEXT NOT NULL, date TEXT NOT NULL, amount REAL NOT NULL, code TEXT NOT NULL, createdAt INTEGER NOT NULL, storagePath TEXT)")
    }
}

