package com.badmintonledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultRate = LedgerData().config.defaultRate
        setContent {
            MaterialTheme {
                Surface {
                    Text("Badminton Ledger — default rate $${centsToDollars(defaultRate.value)}/h")
                }
            }
        }
    }
}
