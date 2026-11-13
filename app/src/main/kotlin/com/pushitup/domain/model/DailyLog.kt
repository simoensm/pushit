package com.pushitup.domain.model

import java.time.LocalDate

data class DailyLog(
    val uid: String,
    val date: LocalDate,
    val totalReps: Int,
    val sessionIds: List<String>,
)
