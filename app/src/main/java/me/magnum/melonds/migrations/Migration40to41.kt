package me.magnum.melonds.migrations

import android.content.SharedPreferences

class Migration40to41(private val sharedPreferences: SharedPreferences) : Migration {

    override val from = 40
    override val to = 41

    override fun migrate() {
        // Previously trimmed the RetroAchievements username. That integration has been removed, so there is nothing to do.
    }
}