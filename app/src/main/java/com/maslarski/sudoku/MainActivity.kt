package com.maslarski.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maslarski.sudoku.data.billing.PlayBillingRepository
import com.maslarski.sudoku.ui.navigation.SudokuNavHost
import com.maslarski.sudoku.ui.theme.SudokuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var billing: PlayBillingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SudokuTheme {
                SudokuNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        billing.attachActivity(this)
    }

    override fun onStop() {
        billing.detachActivity(this)
        super.onStop()
    }
}
