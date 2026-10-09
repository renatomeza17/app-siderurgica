package pe.edu.unmsm.siderurgica

import android.app.Application
import androidx.room.Room
import pe.edu.unmsm.siderurgica.data.LocalStore

class SiderurgicaApp : Application() {
    val db by lazy { Room.databaseBuilder(this, LocalStore::class.java, "siderurgica-mobile.db").build() }
}
