package app.people.contacts.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.people.contacts.R
import app.people.contacts.ui.components.BottomBar
import app.people.contacts.ui.contact.ContactScreen
import app.people.contacts.ui.contacts.AccessScreen
import app.people.contacts.ui.contacts.ContactsTab
import app.people.contacts.ui.edit.EditContactScreen
import app.people.contacts.ui.highlights.HighlightsTab
import app.people.contacts.ui.organize.LabelScreen
import app.people.contacts.ui.organize.MergeScreen
import app.people.contacts.ui.organize.OrganizeTab
import app.people.contacts.ui.settings.SettingsScreen
import app.people.contacts.ui.search.SearchScreen
import app.people.contacts.util.hasPermission

/** The three bottom tabs, in order. Contacts is home. */
enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Contacts(R.string.tab_contacts, Icons.Outlined.Person, Icons.Filled.Person),
    Highlights(R.string.tab_highlights, Icons.Outlined.Assistant, Icons.Filled.Assistant),
    Organize(R.string.tab_organize, Icons.Outlined.Book, Icons.Filled.Book),
}

private val CONTACTS_PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

/** The whole app: asks for contacts access, then shows the screen on top of the back stack. */
@Composable
fun ContactsApp(viewModel: ContactsViewModel = viewModel()) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(context.hasPermission(Manifest.permission.READ_CONTACTS)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = context.hasPermission(Manifest.permission.READ_CONTACTS)
        viewModel.onAccessChanged()
    }
    LaunchedEffect(Unit) { if (!hasAccess) launcher.launch(CONTACTS_PERMISSIONS) }
    if (!hasAccess) {
        AccessScreen(onAllow = { launcher.launch(CONTACTS_PERMISSIONS) })
        return
    }

    val stack = rememberBackStack()
    var tab by rememberSaveable { mutableStateOf(Tab.Contacts) }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    when (val screen = stack.last()) {
        Screen.Home -> Home(viewModel, tab, onTabChange = { tab = it }, open = open)
        Screen.Search -> SearchScreen(viewModel, onBack = back, onOpenContact = { open(Screen.Contact(it)) })
        is Screen.Contact -> ContactScreen(viewModel, screen.id, onBack = back, onEdit = { open(Screen.Edit(screen.id)) })
        is Screen.Label -> LabelScreen(viewModel, screen.id, onBack = back, onOpenContact = { open(Screen.Contact(it)) })
        Screen.MergeDuplicates -> MergeScreen(viewModel, onBack = back, onOpenContact = { open(Screen.Contact(it)) })
        Screen.Settings -> SettingsScreen(viewModel, onBack = back)
        is Screen.Edit -> EditContactScreen(
            viewModel, screen.id,
            onClose = back,
            onSaved = { savedId ->
                // A new contact opens on its page; an edited one goes back to its page.
                stack.removeAt(stack.lastIndex)
                if (screen.id == null) stack.add(Screen.Contact(savedId))
            },
        )
    }
}

/** The bottom tabs. Back from Highlights or Organize returns to Contacts. */
@Composable
private fun Home(viewModel: ContactsViewModel, tab: Tab, onTabChange: (Tab) -> Unit, open: (Screen) -> Unit) {
    BackHandler(enabled = tab != Tab.Contacts) { onTabChange(Tab.Contacts) }
    Scaffold(
        floatingActionButton = {
            if (tab == Tab.Contacts) {
                FloatingActionButton(onClick = { open(Screen.Edit(null)) }) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.create_contact))
                }
            }
        },
        bottomBar = {
            BottomBar {
                Tab.entries.forEach { entry ->
                    val selected = entry == tab
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { onTabChange(entry) },
                        icon = { Icon(if (selected) entry.selectedIcon else entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.Contacts -> ContactsTab(
                viewModel = viewModel,
                contentPadding = padding,
                onSearch = { open(Screen.Search) },
                onOpenContact = { open(Screen.Contact(it)) },
            )
            Tab.Highlights -> HighlightsTab(
                viewModel = viewModel,
                contentPadding = padding,
                onSearch = { open(Screen.Search) },
                onOpenContact = { open(Screen.Contact(it)) },
            )
            Tab.Organize -> OrganizeTab(
                viewModel = viewModel,
                contentPadding = padding,
                onSearch = { open(Screen.Search) },
                onOpenLabel = { open(Screen.Label(it)) },
                onOpenMerge = { open(Screen.MergeDuplicates) },
                onOpenSettings = { open(Screen.Settings) },
            )
        }
    }
}
