package me.magnum.melonds.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Drops all tables that were used by the RetroAchievements integration, which no longer exists. None of these tables reference, or are referenced by, any other table,
 * and their indices are removed together with them.
 */
class Migration8to9 : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ra_game",
            "ra_achievement_set",
            "ra_achievement",
            "ra_user_achievement",
            "ra_leaderboard",
            "ra_game_set_metadata",
            "ra_game_hash_library",
            "ra_pending_achievement_award",
        ).forEach { table ->
            db.execSQL("DROP TABLE IF EXISTS `$table`")
        }
    }
}
