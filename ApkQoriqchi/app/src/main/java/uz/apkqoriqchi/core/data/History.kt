package uz.apkqoriqchi.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType

@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val target: String,
    val sha256: String?,
    val riskLevel: String,
    val summary: String,
    val detailsJson: String,
    val createdAt: Long,
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    fun observe(id: Long): Flow<ScanEntity?>

    @Insert
    suspend fun insert(entity: ScanEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}

@Singleton
class HistoryRepository @Inject constructor(private val dao: ScanDao) {
    fun observeAll(): Flow<List<ScanRecord>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    fun observe(id: Long): Flow<ScanRecord?> = dao.observe(id).map { it?.toModel() }
    suspend fun save(record: ScanRecord): Long = dao.insert(record.toEntity())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
}

private fun ScanEntity.toModel() = ScanRecord(
    id = id,
    type = ScanType.valueOf(type),
    target = target,
    sha256 = sha256,
    riskLevel = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.UNKNOWN),
    summary = summary,
    detailsJson = detailsJson,
    createdAt = createdAt,
)

private fun ScanRecord.toEntity() = ScanEntity(
    id = id, type = type.name, target = target, sha256 = sha256,
    riskLevel = riskLevel.name, summary = summary, detailsJson = detailsJson, createdAt = createdAt,
)
