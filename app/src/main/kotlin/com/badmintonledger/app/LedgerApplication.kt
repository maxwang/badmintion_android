package com.badmintonledger.app

import android.app.Application
import com.badmintonledger.app.storage.LedgerStore

class LedgerApplication : Application() {
    val store: LedgerStore by lazy { LedgerStore(this) }
}
