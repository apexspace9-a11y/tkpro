package vn.tietkiem.pro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        DebtEntity::class,
        RecurringEntity::class,
        PayeeEntity::class,
        TagEntity::class,
        TransactionMetaEntity::class,
        TransactionTagEntity::class,
        TransactionSplitEntity::class,
        BudgetConfigEntity::class,
        GoalContributionEntity::class,
        GoalLinkEntity::class,
        DebtPaymentEntity::class,
        AccountSnapshotEntity::class,
        AiChatMessageEntity::class,
        PremiumPaymentEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `payees` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `defaultCategoryId` INTEGER, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payees_name` ON `payees` (`name`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_meta` (`transactionId` INTEGER NOT NULL, `payeeId` INTEGER, `attachmentUri` TEXT NOT NULL, `merchantText` TEXT NOT NULL, `isSubscription` INTEGER NOT NULL, PRIMARY KEY(`transactionId`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payeeId`) REFERENCES `payees`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_meta_payeeId` ON `transaction_meta` (`payeeId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_tags` (`transactionId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`transactionId`, `tagId`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tags_tagId` ON `transaction_tags` (`tagId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `categoryId` INTEGER, `amount` INTEGER NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_transactionId` ON `transaction_splits` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_categoryId` ON `transaction_splits` (`categoryId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `budget_configs` (`categoryId` INTEGER NOT NULL, `rolloverEnabled` INTEGER NOT NULL, `carryAmount` INTEGER NOT NULL, `envelopeTarget` INTEGER NOT NULL, PRIMARY KEY(`categoryId`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_occurredAt` ON `goal_contributions` (`occurredAt`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_links` (`goalId` INTEGER NOT NULL, `accountId` INTEGER, PRIMARY KEY(`goalId`), FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_links_accountId` ON `goal_links` (`accountId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `debt_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `debtId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `interestAmount` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_debtId` ON `debt_payments` (`debtId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_occurredAt` ON `debt_payments` (`occurredAt`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `account_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `balance` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_snapshots_accountId` ON `account_snapshots` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_snapshots_capturedAt` ON `account_snapshots` (`capturedAt`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_createdAt` ON `ai_chat_messages` (`createdAt`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `premium_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plan` TEXT NOT NULL, `amount` INTEGER NOT NULL, `transferCode` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_premium_payments_createdAt` ON `premium_payments` (`createdAt`)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tiet_kiem_pro.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
