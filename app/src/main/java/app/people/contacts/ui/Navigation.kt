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
    /** The editor; [id] null creates a new contact, or the user's own card with [profile]. */
    data class Edit(val id: Long?, val profile: Boolean = false) : Screen
    data class Label(val id: Long) : Screen
    data object MergeDuplicates : Screen
    data object FixContacts : Screen
    data object Settings : Screen
    /** A .vcf file opened from another app, waiting to be imported. */
    data class ImportFile(val uri: String) : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Search -> "search"
    is Screen.Contact -> "contact:$id"
    is Screen.Edit -> "edit:" + (id?.toString() ?: if (profile) "profile" else "new")
    is Screen.Label -> "label:$id"
    Screen.MergeDuplicates -> "merge"
    Screen.FixContacts -> "fix"
    Screen.Settings -> "settings"
    is Screen.ImportFile -> "import:$uri"
}

private fun decode(text: String): Screen? = when {
    text == "home" -> Screen.Home
    text == "search" -> Screen.Search
    text.startsWith("contact:") -> text.removePrefix("contact:").toLongOrNull()?.let { Screen.Contact(it) }
    text == "merge" -> Screen.MergeDuplicates
    text == "fix" -> Screen.FixContacts
    text == "settings" -> Screen.Settings
    text.startsWith("import:") -> Screen.ImportFile(text.removePrefix("import:"))
    text.startsWith("label:") -> text.removePrefix("label:").toLongOrNull()?.let { Screen.Label(it) }
    text == "edit:new" -> Screen.Edit(null)
    text == "edit:profile" -> Screen.Edit(null, profile = true)
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
