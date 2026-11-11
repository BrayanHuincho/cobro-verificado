package pe.cobroverificado.lector.datos.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pagos", indices = [
        Index(value = ["huella"], unique = true),
        Index(value = ["billetera", "numeroOperacion"], unique = true),
        Index("estado"),
    ],
)
data class PagoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billetera: String,
    val monto: String,
    val fechaHora: String,
    val remitente: String?,
    val numeroOperacion: String?,
    val huella: String,
    val estado: String,
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val registradoEn: Long,
    val paquete: String? = null,
    val sincronizadoEn: Long? = null,
    @ColumnInfo(defaultValue = "0") val reportado: Boolean = false,
)

@Dao
interface PagoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(pago: PagoEntidad): Long

    @Query("SELECT * FROM pagos ORDER BY fechaHora DESC, id DESC")
    fun observarTodos(): Flow<List<PagoEntidad>>

    @Query("SELECT * FROM pagos WHERE estado = 'PENDIENTE' ORDER BY id LIMIT :limite")
    suspend fun pendientes(limite: Int): List<PagoEntidad>

    @Query(
        "UPDATE pagos SET estado = :estado, intentos = :intentos, ultimoError = :error, " +
            "sincronizadoEn = CASE WHEN :estado = 'ENVIADO' THEN :ahora ELSE sincronizadoEn END WHERE id = :id",
    )
    suspend fun actualizar(id: Long, estado: String, intentos: Int, error: String?, ahora: Long)

    @Query("UPDATE pagos SET reportado = :reportado WHERE id = :id")
    suspend fun marcarReportado(id: Long, reportado: Boolean)

    @Query("DELETE FROM pagos")
    suspend fun borrarTodo()
}

@Database(entities = [PagoEntidad::class], version = 2, exportSchema = true)
abstract class BaseDatos : RoomDatabase() {
    abstract fun pagos(): PagoDao
}

// un aviso reenviado trae otra huella pero el mismo número de operación; sqlite permite varios null en el índice único
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM pagos WHERE numeroOperacion IS NOT NULL AND id NOT IN " +
                "(SELECT MIN(id) FROM pagos WHERE numeroOperacion IS NOT NULL GROUP BY billetera, numeroOperacion)",
        )
        db.execSQL("ALTER TABLE pagos ADD COLUMN paquete TEXT")
        db.execSQL("ALTER TABLE pagos ADD COLUMN sincronizadoEn INTEGER")
        db.execSQL("ALTER TABLE pagos ADD COLUMN reportado INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pagos_billetera_numeroOperacion ON pagos (billetera, numeroOperacion)")
    }
}
