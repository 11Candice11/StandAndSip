package com.standandsip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {

    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("""
        SELECT COUNT(*) FROM log_entries
        WHERE type = :type
          AND date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')
    """)
    suspend fun todayCount(type: String): Int

    @Query("""
        DELETE FROM log_entries
        WHERE id IN (
          SELECT id FROM log_entries
          WHERE type = :type
            AND date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')
          ORDER BY timestamp DESC
          LIMIT 1
        )
    """)
    suspend fun deleteLatestToday(type: String): Int

    @Query("SELECT * FROM log_entries ORDER BY timestamp ASC")
    suspend fun getAll(): List<LogEntry>
}