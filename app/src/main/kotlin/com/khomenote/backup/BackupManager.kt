package com.khomenote.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.khomenote.data.local.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private const val SQLITE_HEADER = "SQLite format 3\u0000" // 파일 첫 16바이트

    private const val PREFS_NAME = "backup_prefs"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"

    /** 마지막으로 백업에 성공한 날짜(이 기기 기준). 한 번도 안 했으면 null. */
    fun lastBackupDate(context: Context): LocalDate? {
        val millis = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_BACKUP_AT, 0L)
        return if (millis == 0L) null else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

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
        } ?: error("백업 파일을 열 수 없습니다.")

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * [source] 파일로 현재 DB를 교체한다. 라이브 DB 파일에 바로 스트림으로 덮어쓰면 복사 도중
     * 실패했을 때(디스크 공간 부족, 읽던 중 연결 끊김) DB가 절반만 쓰인 채 손상될 수 있고,
     * 사용자가 백업 파일이 아닌 엉뚱한 파일을 골랐을 때도 걸러낼 방법이 없다. 그래서
     * 캐시 디렉터리의 임시 파일로 먼저 받아 SQLite 헤더와 무결성(PRAGMA integrity_check)을
     * 검증하고, 통과했을 때만 교체 직전 현재 DB를 `.before_restore`로 남겨 둔 뒤 실제
     * DB 파일을 교체한다. 검증에 실패하면 기존 DB는 전혀 건드리지 않은 채 예외를 던진다.
     */
    fun restore(context: Context, source: Uri) {
        val dbFile = context.getDatabasePath(DB_NAME)
        val staging = File(context.cacheDir, "restore_staging.db")

        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            } ?: error("복원할 파일을 열 수 없습니다.")

            if (!isValidSqliteFile(staging)) {
                error("올바른 백업 파일이 아닙니다.")
            }
            val ok = SQLiteDatabase.openDatabase(staging.path, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.isDatabaseIntegrityOk }
            if (!ok) {
                error("백업 파일이 손상되어 있습니다.")
            }

            // 복원이 잘못돼도 되돌릴 수 있도록 교체 직전 현재 DB를 따로 남겨 둔다.
            AppDatabase.closeInstance()
            dbFile.copyTo(File(dbFile.path + ".before_restore"), overwrite = true)
            staging.copyTo(dbFile, overwrite = true)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
        } finally {
            staging.delete()
        }
    }

    private fun isValidSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        return String(header, Charsets.ISO_8859_1) == SQLITE_HEADER
    }
}
