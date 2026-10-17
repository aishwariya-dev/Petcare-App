package com.petcare.app.ui.screens.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One-time per account. Agreeing is what unlocks the system SMS request. */
@Composable
fun SmsConsentDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SMS reminders") },
        text = {
            Column {
                Text(
                    "PetCare can send a task reminder by SMS from this phone at the scheduled time.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Before turning it on, please confirm:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                CONSENT_POINTS.forEach { point ->
                    Text("•  $point", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("I agree") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

private val CONSENT_POINTS = listOf(
    "Recipients have agreed to receive these messages.",
    "Carrier charges apply: up to three SMS parts per reminder.",
    "Messages go from this phone's default SIM at the task time.",
    "Delivery is not guaranteed."
)
