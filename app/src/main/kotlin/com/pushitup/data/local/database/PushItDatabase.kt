package com.pushitup.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pushitup.data.local.dao.DailyLogDao
import com.pushitup.data.local.dao.SessionDao
import com.pushitup.data.local.entity.DailyLogEntity
import com.pushitup.data.local.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, DailyLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PushItDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun dailyLogDao(): DailyLogDao
}
