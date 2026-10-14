package com.itmda.swiftq.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite through Room. Version 2 (adds tickets.email_enc); the schema JSON is exported to app/schemas for Chapter 4.5.1.
 * No destructive migrations: when the version changes, a real Migration must be added.
 */
@Database(
    entities = [
        ClinicEntity::class,
        ServicePointEntity::class,
        TicketEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SwiftQDatabase : RoomDatabase() {
    abstract fun clinicDao(): ClinicDao
    abstract fun servicePointDao(): ServicePointDao
    abstract fun ticketDao(): TicketDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "swiftq.db"

        /** v1 -> v2: the alert email chosen on Check and join, kept encrypted beside phone_enc. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tickets ADD COLUMN email_enc TEXT")
            }
        }

        fun create(context: Context): SwiftQDatabase =
            Room.databaseBuilder(context, SwiftQDatabase::class.java, NAME).addMigrations(MIGRATION_1_2).build()
    }
}

/** Runs [block] so that every write inside it commits together or not at all. */
interface Transactor {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactor(private val db: SwiftQDatabase) : Transactor {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
