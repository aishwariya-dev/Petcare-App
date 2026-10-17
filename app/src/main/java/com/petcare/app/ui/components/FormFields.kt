package com.petcare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Outlined text field with label, required marker, and inline error message. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (required) "$label *" else label) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = when {
            error != null -> { { Text(error) } }
            supportingText != null -> { { Text(supportingText) } }
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        enabled = enabled,
        shape = MaterialTheme.shapes.small
    )
}

/** Exposed dropdown for enum-like choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    value: T?,
    options: List<T>,
    onSelect: (T) -> Unit,
    label: String,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    placeholder: String = "Select"
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(if (required) "$label *" else label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            enabled = enabled,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Read-only field that opens a Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }

    PickerField(
        text = value?.let { DateTimeFormats.fullDate(it) } ?: "",
        label = label,
        icon = Icons.Outlined.CalendarMonth,
        required = required,
        error = error,
        placeholder = "Select date",
        onClick = { open = true },
        enabled = enabled,
        modifier = modifier
    )

    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Read-only field that opens a Material time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    value: LocalTime?,
    onSelect: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }

    PickerField(
        text = value?.let { DateTimeFormats.time(it) } ?: "",
        label = label,
        icon = Icons.Outlined.Schedule,
        required = required,
        error = error,
        placeholder = "Select time",
        onClick = { open = true },
        enabled = enabled,
        modifier = modifier
    )

    if (open) {
        val initial = value ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(Spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(Spacing.medium))
                TimePicker(state = state)
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        onSelect(LocalTime.of(state.hour, state.minute))
                        open = false
                    }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun PickerField(
    text: String,
    label: String,
    icon: ImageVector,
    required: Boolean,
    error: String?,
    placeholder: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(if (required) "$label *" else label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(icon, contentDescription = null) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            // Keep the disabled field looking enabled; the overlay handles taps.
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledSupportingTextColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClickLabel = "Open $label picker", onClick = onClick)
            )
        }
    }
}
