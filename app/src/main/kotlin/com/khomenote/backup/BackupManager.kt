package com.khomenote.backup

import android.content.Context
import android.net.Uri
import com.khomenote.data.local.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DB 파일을 통째로 복사해서 백업/복원한다. 테이블별로 JSON을 직렬화하는 대신
 * SQLite 파일을 그대로 다루므로, 스키마가 바뀌어도 별도 백업 포맷 관리가 필요 없다 —
 * 항상 실제 스키마와 100% 일치한다.
 *
 * 사진 파일(구매이력 첨부 사진)은 이 백업에 포함되지 않는다. DB의 photoUri는 경로만
 * 가리키므로, 기기를 바꾸거나 앱 데이터를 지운 뒤 복원하면 사진은 사라질 수 있다.
 */
object BackupManager {
    private const val DB_NAME = "k_home_note.db"

    fun backupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "k_home_note_backup_$timestamp.db"
    }

    /** DB를 [destination]에 복사한다. 아직 WAL에만 남아있는 최근 변경분까지 파일에 반영되도록 먼저 체크포인트한다. */
    fun backup(context: Context, destination: Uri) {
        val db = AppDatabase.getInstance(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

        val dbFile = context.getDatabasePath(DB_NAME)
        context.contentResolver.openOutputStream(destination)?.use { output ->
            dbFile.inputStream().use { input -> input.copyTo(output) }
        }
    }

    /**
     * [source] 파일로 현재 DB를 덮어쓴다. 열려 있던 Room 연결을 먼저 닫아야 안전하게
     * 파일을 교체할 수 있고, 남아있는 -wal/-shm 파일도 지워서 옛 DB의 잔여 변경분이
     * 새로 복원한 파일 위에 다시 적용되는 걸 막는다. 복원 후에는 반드시 앱을 재시작해야
     * 기존 DAO/ViewModel이 새 DB를 다시 읽는다.
     */
    fun restore(context: Context, source: Uri) {
        AppDatabase.closeInstance()

        val dbFile = context.getDatabasePath(DB_NAME)
        context.contentResolver.openInputStream(source)?.use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }
}
