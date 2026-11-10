package app.people.contacts.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.EditField
import app.people.contacts.data.newFieldKey
import app.people.contacts.util.formatEventDate
import app.people.contacts.util.parseEventDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Space between one field (or group of fields) and the next in the editor. */
val FIELD_GAP = 12.dp

/** The types a repeating field can have, the label for each, and the "custom" type. */
class FieldKind(
    val icon: ImageVector,
    val hint: Int,
    val addLabel: Int,
    val types: List<Int>,
    val customType: Int,
    val label: (type: Int, custom: String?) -> String,
    val keyboard: KeyboardType = KeyboardType.Text,
    val multiLine: Boolean = false,
    val isDate: Boolean = false,
)

/**
 * A repeating field (phones, emails, …): each value with its type under it and a remove button,
 * then an "Add" button. An empty list shows just the button.
 */
@Composable
fun RepeatingFields(kind: FieldKind, fields: List<EditField>, onChange: (List<EditField>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
        fields.forEachIndexed { index, field ->
            fun update(new: EditField) = onChange(fields.toMutableList().also { it[index] = new })
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (kind.isDate) {
                        DateField(kind, field, onPick = { update(field.copy(value = it)) })
                    } else {
                        OutlinedTextField(
                            value = field.value,
                            onValueChange = { update(field.copy(value = it)) },
                            label = { Text(stringResource(kind.hint)) },
                            leadingIcon = { Icon(kind.icon, null) },
                            singleLine = !kind.multiLine,
                            keyboardOptions = KeyboardOptions(keyboardType = kind.keyboard),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (kind.types.isNotEmpty()) {
                        TypePicker(kind, field, onPick = { type, label -> update(field.copy(type = type, label = label)) })
                    }
                }
                IconButton(
                    onClick = { onChange(fields.toMutableList().also { it.removeAt(index) }) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = {
            onChange(fields + EditField(key = newFieldKey(), type = kind.types.firstOrNull() ?: kind.customType))
        }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(kind.addLabel))
        }
    }
}

/** The field's type ("Mobile ▾") as a small button under it, with a menu and a custom label option. */
@Composable
private fun TypePicker(kind: FieldKind, field: EditField, onPick: (Int, String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 40.dp)) {
        TextButton(onClick = { menu = true }) {
            Text(kind.label(field.type, field.label))
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            kind.types.forEach { type ->
                DropdownMenuItem(text = { Text(kind.label(type, null)) }, onClick = { menu = false; onPick(type, null) })
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.custom_label)) },
                onClick = { menu = false; customDialog = true },
            )
        }
    }
    if (customDialog) {
        var text by remember { mutableStateOf(if (field.type == kind.customType) field.label.orEmpty() else "") }
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text(stringResource(R.string.custom_label)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    onClick = { customDialog = false; onPick(kind.customType, text.trim()) },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { customDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A date: shows the date, and opens a date picker when tapped. Stored as "yyyy-MM-dd". */
@Composable
private fun DateField(kind: FieldKind, field: EditField, onPick: (String) -> Unit) {
    var picking by remember { mutableStateOf(field.value.isEmpty()) }
    val context = LocalContext.current
    OutlinedTextField(
        value = if (field.value.isEmpty()) "" else formatEventDate(field.value),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(kind.hint)) },
        leadingIcon = { Icon(kind.icon, null) },
        trailingIcon = {
            IconButton(onClick = { picking = true }) { Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.pick_date)) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        val parsed = parseEventDate(field.value)
        val initial = parsed?.let {
            LocalDate.of(it.year ?: LocalDate.now().year, it.month, it.day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                }) { Text(context.getString(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}
