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

/**
 * consumable_items에 lastNotifiedMilestoneDays 컬럼만 추가하는 순수 추가형(additive) 마이그레이션.
 * 알림을 여러 시점(14/7/3/1일 전 등)에 나눠 보내기 위해, 같은 교체 주기 안에서 가장 최근에
 * 보낸 시점을 기록해 중복 알림을 막는 데 쓴다.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `consumable_items` ADD COLUMN `lastNotifiedMilestoneDays` INTEGER")
    }
}

/**
 * consumable_items에 yearlyDueMonth/yearlyDueDay 컬럼을 추가하는 순수 추가형(additive) 마이그레이션.
 * 생일/기념일처럼 "매년 이 날짜" 고정 주기를 위한 컬럼 — 둘 다 null이면 기존 방식(간격 또는 매달 고정) 그대로 동작한다.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `consumable_items` ADD COLUMN `yearlyDueMonth` INTEGER")
        db.execSQL("ALTER TABLE `consumable_items` ADD COLUMN `yearlyDueDay` INTEGER")
    }
}

/**
 * consumable_items에 isYearlyLunar 컬럼만 추가하는 순수 추가형(additive) 마이그레이션.
 * "매년 특정 날짜"를 음력 기준으로 해석할지(생일/제사 등) 나타내는 플래그 — null/0이면 기존처럼 양력.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `consumable_items` ADD COLUMN `isYearlyLunar` INTEGER")
    }
}

/**
 * repair_records에 reminderLeadDays/lastNotifiedForDate/lastNotifiedMilestoneDays 컬럼을 추가하는
 * 순수 추가형(additive) 마이그레이션. "일회성작업"에도 소모품과 동일한 방식의 알림을 지원하기 위한
 * 컬럼 — reminderLeadDays가 null이면 기존처럼 그냥 기록(알림 대상 아님)으로 동작한다.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `repair_records` ADD COLUMN `reminderLeadDays` INTEGER")
        db.execSQL("ALTER TABLE `repair_records` ADD COLUMN `lastNotifiedForDate` INTEGER")
        db.execSQL("ALTER TABLE `repair_records` ADD COLUMN `lastNotifiedMilestoneDays` INTEGER")
    }
}

/**
 * repair_records에 vendor/photoUri 컬럼을 추가하는 순수 추가형(additive) 마이그레이션.
 * 일회성작업의 완료 기록에도 주기성작업의 교체 기록처럼 구매처와 영수증 사진을 남기기 위한 컬럼 —
 * 기존 행은 둘 다 null(구매처/사진 없음)로 그대로 보존된다.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `repair_records` ADD COLUMN `vendor` TEXT")
        db.execSQL("ALTER TABLE `repair_records` ADD COLUMN `photoUri` TEXT")
    }
}
