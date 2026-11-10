package app.people.contacts.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** The app's screens. Home holds the bottom tabs; the others cover them. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Contact(val id: Long) : Screen
    /** The editor; [id] null creates a new contact. */
    data class Edit(val id: Long?) : Screen
    data class Label(val id: Long) : Screen
    data object MergeDuplicates : Screen
    data object Settings : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Search -> "search"
    is Screen.Contact -> "contact:$id"
    is Screen.Edit -> "edit:${id ?: "new"}"
    is Screen.Label -> "label:$id"
    Screen.MergeDuplicates -> "merge"
    Screen.Settings -> "settings"
}

private fun decode(text: String): Screen? = when {
    text == "home" -> Screen.Home
    text == "search" -> Screen.Search
    text.startsWith("contact:") -> text.removePrefix("contact:").toLongOrNull()?.let { Screen.Contact(it) }
    text == "merge" -> Screen.MergeDuplicates
    text == "settings" -> Screen.Settings
    text.startsWith("label:") -> text.removePrefix("label:").toLongOrNull()?.let { Screen.Label(it) }
    text == "edit:new" -> Screen.Edit(null)
    text.startsWith("edit:") -> text.removePrefix("edit:").toLongOrNull()?.let { Screen.Edit(it) }
    else -> null
}

/** The back stack, kept across rotation and process death. Always starts with Home. */
@Composable
fun rememberBackStack(): SnapshotStateList<Screen> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { it.encode() } },
        restore = { saved -> saved.mapNotNull(::decode).ifEmpty { listOf(Screen.Home) }.toMutableStateList() },
    ),
) { listOf<Screen>(Screen.Home).toMutableStateList() }
