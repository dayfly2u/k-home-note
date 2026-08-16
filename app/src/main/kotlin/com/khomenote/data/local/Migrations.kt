package com.khomenote.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * repair_records 테이블만 새로 추가하는 순수 추가형(additive) 마이그레이션.
 * 기존 assets/consumable_items/replacement_records/categories 테이블은 전혀 건드리지 않으므로
 * 기기에 이미 쌓인 데이터가 안전하게 보존된다.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `repair_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `assetId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `price` INTEGER,
                `memo` TEXT,
                FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_repair_records_assetId` ON `repair_records` (`assetId`)"
        )
    }
}

/**
 * consumable_items에 monthlyDueDay 컬럼만 추가하는 순수 추가형(additive) 마이그레이션.
 * "매달 특정 날짜" 고정 주기를 위한 컬럼 — null이면 기존 cycleDays 기반 간격 방식 그대로 동작한다.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `consumable_items` ADD COLUMN `monthlyDueDay` INTEGER")
    }
}
