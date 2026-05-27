package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(private val db: AppDatabase) {
    private val logDao = db.logDao()
    private val settingDao = db.settingDao()

    val allLogs: Flow<List<LogEntry>> = logDao.getAllLogs()

    suspend fun addLog(level: String, tag: String, message: String) {
        logDao.insertLog(LogEntry(level = level, tag = tag, message = message))
    }

    suspend fun clearLogs() {
        logDao.clearLogs()
    }

    val allSettings: Flow<Map<String, String>> = settingDao.getAllSettings().map { list ->
        list.associate { it.key to it.value }
    }

    suspend fun getSetting(key: String, default: String): String {
        return settingDao.getSetting(key)?.value ?: default
    }

    suspend fun saveSetting(key: String, value: String) {
        settingDao.insertSetting(SettingEntry(key, value))
    }
}
