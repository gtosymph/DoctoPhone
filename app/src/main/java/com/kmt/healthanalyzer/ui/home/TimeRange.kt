package com.kmt.healthanalyzer.ui.home

/**
 * Fenêtre d'observation proposée sur les écrans qui montrent une période glissante.
 *
 * Utilisée par [com.kmt.healthanalyzer.ui.report.ReportScreen] ; elle reste dans ce
 * paquet pour ne pas casser son import.
 *
 * [shortLabel] tient sur un sélecteur à quatre segments sans jamais passer à la ligne ;
 * [label], plus explicite, sert partout ailleurs (dialogues, messages d'état).
 */
enum class TimeRange(val label: String, val shortLabel: String, val days: Long) {
    WEEK("7 jours", "7 j", 7),
    MONTH("30 jours", "30 j", 30),
    QUARTER("90 jours", "90 j", 90),
    YEAR("1 an", "1 an", 365),
}
