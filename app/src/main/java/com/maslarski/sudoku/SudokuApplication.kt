package com.maslarski.sudoku

import android.app.Application
import com.maslarski.sudoku.data.remote.FirebaseInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SudokuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.initialize(this)
    }
}
