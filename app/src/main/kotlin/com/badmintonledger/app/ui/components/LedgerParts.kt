package com.badmintonledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PoolCard(
    poolDollars: String,
    warn: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Venue pool", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$$poolDollars",
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (warn) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
            if (warn) {
                Text(
                    "Low balance — consider a refill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun MemberBalanceRow(
    name: String,
    isGuest: Boolean,
    owes: Boolean,
    absDollars: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name + if (isGuest) " (guest)" else "")
        Text(
            if (owes) "owes $$absDollars" else "$$absDollars",
            color =
                if (owes) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
