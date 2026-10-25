package com.habitgame.ui

import androidx.annotation.DrawableRes
import com.habitgame.R

/**
 * Ranks shown next to the level: 10 tiers from Wood to Master, 12 steps each,
 * so every level up to 120 gets a new shield. Higher levels keep the top shield.
 */
data class Rank(val tier: String, val step: Int, @DrawableRes val icon: Int) {
    /** e.g. "Wood III" */
    val label: String get() = "$tier ${ROMAN[step - 1]}"

    companion object {
        val TIERS = listOf("Wood", "Bronze", "Silver", "Gold", "Platinum", "Sapphire", "Ruby", "Amethyst", "Onyx", "Master")
        private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
        const val STEPS = 12

        fun forLevel(level: Int): Rank {
            val index = (level - 1).coerceIn(0, TIERS.size * STEPS - 1)
            return Rank(TIERS[index / STEPS], index % STEPS + 1, ICONS[index])
        }

        private val ICONS = intArrayOf(
            R.drawable.rank_wood_1, R.drawable.rank_wood_2, R.drawable.rank_wood_3, R.drawable.rank_wood_4, R.drawable.rank_wood_5, R.drawable.rank_wood_6, R.drawable.rank_wood_7, R.drawable.rank_wood_8, R.drawable.rank_wood_9, R.drawable.rank_wood_10, R.drawable.rank_wood_11, R.drawable.rank_wood_12,
            R.drawable.rank_bronze_1, R.drawable.rank_bronze_2, R.drawable.rank_bronze_3, R.drawable.rank_bronze_4, R.drawable.rank_bronze_5, R.drawable.rank_bronze_6, R.drawable.rank_bronze_7, R.drawable.rank_bronze_8, R.drawable.rank_bronze_9, R.drawable.rank_bronze_10, R.drawable.rank_bronze_11, R.drawable.rank_bronze_12,
            R.drawable.rank_silver_1, R.drawable.rank_silver_2, R.drawable.rank_silver_3, R.drawable.rank_silver_4, R.drawable.rank_silver_5, R.drawable.rank_silver_6, R.drawable.rank_silver_7, R.drawable.rank_silver_8, R.drawable.rank_silver_9, R.drawable.rank_silver_10, R.drawable.rank_silver_11, R.drawable.rank_silver_12,
            R.drawable.rank_gold_1, R.drawable.rank_gold_2, R.drawable.rank_gold_3, R.drawable.rank_gold_4, R.drawable.rank_gold_5, R.drawable.rank_gold_6, R.drawable.rank_gold_7, R.drawable.rank_gold_8, R.drawable.rank_gold_9, R.drawable.rank_gold_10, R.drawable.rank_gold_11, R.drawable.rank_gold_12,
            R.drawable.rank_platinum_1, R.drawable.rank_platinum_2, R.drawable.rank_platinum_3, R.drawable.rank_platinum_4, R.drawable.rank_platinum_5, R.drawable.rank_platinum_6, R.drawable.rank_platinum_7, R.drawable.rank_platinum_8, R.drawable.rank_platinum_9, R.drawable.rank_platinum_10, R.drawable.rank_platinum_11, R.drawable.rank_platinum_12,
            R.drawable.rank_sapphire_1, R.drawable.rank_sapphire_2, R.drawable.rank_sapphire_3, R.drawable.rank_sapphire_4, R.drawable.rank_sapphire_5, R.drawable.rank_sapphire_6, R.drawable.rank_sapphire_7, R.drawable.rank_sapphire_8, R.drawable.rank_sapphire_9, R.drawable.rank_sapphire_10, R.drawable.rank_sapphire_11, R.drawable.rank_sapphire_12,
            R.drawable.rank_ruby_1, R.drawable.rank_ruby_2, R.drawable.rank_ruby_3, R.drawable.rank_ruby_4, R.drawable.rank_ruby_5, R.drawable.rank_ruby_6, R.drawable.rank_ruby_7, R.drawable.rank_ruby_8, R.drawable.rank_ruby_9, R.drawable.rank_ruby_10, R.drawable.rank_ruby_11, R.drawable.rank_ruby_12,
            R.drawable.rank_amethyst_1, R.drawable.rank_amethyst_2, R.drawable.rank_amethyst_3, R.drawable.rank_amethyst_4, R.drawable.rank_amethyst_5, R.drawable.rank_amethyst_6, R.drawable.rank_amethyst_7, R.drawable.rank_amethyst_8, R.drawable.rank_amethyst_9, R.drawable.rank_amethyst_10, R.drawable.rank_amethyst_11, R.drawable.rank_amethyst_12,
            R.drawable.rank_onyx_1, R.drawable.rank_onyx_2, R.drawable.rank_onyx_3, R.drawable.rank_onyx_4, R.drawable.rank_onyx_5, R.drawable.rank_onyx_6, R.drawable.rank_onyx_7, R.drawable.rank_onyx_8, R.drawable.rank_onyx_9, R.drawable.rank_onyx_10, R.drawable.rank_onyx_11, R.drawable.rank_onyx_12,
            R.drawable.rank_master_1, R.drawable.rank_master_2, R.drawable.rank_master_3, R.drawable.rank_master_4, R.drawable.rank_master_5, R.drawable.rank_master_6, R.drawable.rank_master_7, R.drawable.rank_master_8, R.drawable.rank_master_9, R.drawable.rank_master_10, R.drawable.rank_master_11, R.drawable.rank_master_12
        )
    }
}
