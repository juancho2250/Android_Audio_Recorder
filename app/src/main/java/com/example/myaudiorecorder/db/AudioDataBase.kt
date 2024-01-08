package com.example.myaudiorecorder.db

import androidx.room.Database
import androidx.room.RoomDatabase


@Database(entities = arrayOf(AudioRecord::class), version = 1)
abstract class AudioDataBase: RoomDatabase() {
    abstract fun audioRecordDao(): AudioRecordDao
}