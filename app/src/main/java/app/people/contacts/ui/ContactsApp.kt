package app.people.contacts.ui

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.util.Reach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import app.people.contacts.intents.parseRequest
import app.people.contacts.intents.ExternalRequest
import androidx.compose.runtime.produceState
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import app.people.contacts.ui.components.SearchPill
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
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

/**
 * The whole app: asks for contacts access, then shows the screen on top of the back stack.
 * [intent] is what opened the app; requests from other apps that hand something back end
 * through [finish] with the result (null when cancelled).
 */
@Composable
fun ContactsApp(intent: Intent?, finish: (Uri?) -> Unit, viewModel: ContactsViewModel = viewModel()) {
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

    // What another app asked for, read once access is granted.
    var handled by rememberSaveable { mutableStateOf(false) }
    val request by produceState<ExternalRequest?>(null, intent) {
        value = intent?.let { withContext(Dispatchers.IO) { parseRequest(context, it) } }
    }
    val external = request
    if (!handled && external != null && external.handsBack()) {
        ExternalFlow(viewModel, external, finish = { handled = true; finish(it) })
        return
    }

    val stack = rememberBackStack()
    LaunchedEffect(external) {
        if (handled || external == null) return@LaunchedEffect
        handled = true
        when (external) {
            is ExternalRequest.View -> stack.add(Screen.Contact(external.contactId))
            ExternalRequest.Settings -> stack.add(Screen.Settings)
            is ExternalRequest.ImportFile -> stack.add(Screen.ImportFile(external.uri.toString()))
            else -> Unit
        }
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Contacts) }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    when (val screen = stack.last()) {
        Screen.Home -> Home(viewModel, tab, onTabChange = { tab = it }, open = open)
        Screen.Search -> SearchScreen(viewModel, onBack = back, onOpenContact = { open(Screen.Contact(it)) })
        is Screen.Contact -> ContactScreen(
            viewModel, screen.id, onBack = back, onEdit = { open(Screen.Edit(screen.id)) },
            onMoved = { moved -> stack[stack.lastIndex] = Screen.Contact(moved) },
        )
        is Screen.Label -> LabelScreen(viewModel, screen.id, onBack = back, onOpenContact = { open(Screen.Contact(it)) })
        Screen.MergeDuplicates -> MergeScreen(viewModel, onBack = back, onOpenContact = { open(Screen.Contact(it)) })
        Screen.Settings -> SettingsScreen(viewModel, onBack = back)
        is Screen.ImportFile -> ImportFileScreen(viewModel, Uri.parse(screen.uri), onDone = back)
        is Screen.Edit -> EditContactScreen(
            viewModel, screen.id, profile = screen.profile,
            onClose = back,
            onSaved = { savedId ->
                // A new contact opens on its page; an edited one goes back to its page.
                stack.removeAt(stack.lastIndex)
                if (screen.id == null) stack.add(Screen.Contact(savedId))
            },
        )
    }
}

/** Requests that end by returning to the app that made them. */
private fun ExternalRequest.handsBack() = this is ExternalRequest.Pick || this is ExternalRequest.Insert ||
    this is ExternalRequest.InsertOrEdit || this is ExternalRequest.Edit

/**
 * The bottom tabs, as swipeable pages (as in Dialtone): swipe between them or tap the bottom
 * bar. Back from Highlights or Organize returns to Contacts. Long-pressing a contact selects it,
 * and the search bar becomes a bar of actions for the selection.
 */
@Composable
private fun Home(viewModel: ContactsViewModel, tab: Tab, onTabChange: (Tab) -> Unit, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = tab.ordinal) { Tab.entries.size }
    // targetPage, not currentPage, so the bar and button follow a swipe as soon as it's let go.
    val current = Tab.entries[pager.targetPage]
    // Remembered outside Home, so the same tab is open on coming back from a contact.
    LaunchedEffect(pager.settledPage) { onTabChange(Tab.entries[pager.settledPage]) }
    BackHandler(enabled = current != Tab.Contacts) { scope.launch { pager.animateScrollToPage(Tab.Contacts.ordinal) } }

    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    // Only contacts still in the list count (one deleted elsewhere drops out).
    val selection = contacts.orEmpty().filter { it.id in selectedIds }
    val selecting = selection.isNotEmpty() && current == Tab.Contacts
    LaunchedEffect(current) { if (current != Tab.Contacts) selectedIds = emptySet() }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickLabel by remember { mutableStateOf(false) }

    // Undo for contacts just deleted, here or on their page.
    val snackbar = remember { SnackbarHostState() }
    val pending by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val deletion = pending
        if (deletion == null) {
            snackbar.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val message = deletion.name?.takeIf { deletion.contacts.size == 1 }?.let { context.getString(R.string.deleted_one, it) }
            ?: context.resources.getQuantityString(R.plurals.deleted_count, deletion.contacts.size, deletion.contacts.size)
        val result = snackbar.showSnackbar(message, actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    Scaffold(
        // Pinned above the tabs, as in Dialtone: lists scroll behind it. The strip is opaque in
        // the background colour (status bar included) so nothing shows through around the pill.
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                if (selecting) {
                    SelectionBar(
                        count = selection.size,
                        allStarred = selection.all { it.starred },
                        onClose = { selectedIds = emptySet() },
                        onSelectAll = { selectedIds = contacts.orEmpty().map { it.id }.toSet() },
                        onStar = { viewModel.setStarred(selection.map { it.id }, !selection.all { it.starred }) },
                        onLabel = { pickLabel = true },
                        onShare = { Reach.shareAll(context, selection.map { it.lookupKey }.filter { it.isNotEmpty() }) },
                        onDelete = { confirmDelete = true },
                    )
                } else {
                    SearchPill(onClick = { open(Screen.Search) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (current == Tab.Contacts && !selecting) {
                FloatingActionButton(onClick = { open(Screen.Edit(null)) }) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.create_contact))
                }
            }
        },
        bottomBar = {
            BottomBar {
                Tab.entries.forEach { entry ->
                    val selected = entry == current
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { scope.launch { pager.animateScrollToPage(entry.ordinal) } },
                        icon = { Icon(if (selected) entry.selectedIcon else entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(), key = { Tab.entries[it] },
            // No swiping away from a selection.
            userScrollEnabled = !selecting,
        ) { page ->
            when (Tab.entries[page]) {
                Tab.Contacts -> ContactsTab(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenContact = { open(Screen.Contact(it)) },
                    onSetUpProfile = { open(Screen.Edit(null, profile = true)) },
                    selected = if (selecting) selectedIds else emptySet(),
                    onToggleSelected = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                )
                Tab.Highlights -> HighlightsTab(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenContact = { open(Screen.Contact(it)) },
                )
                Tab.Organize -> OrganizeTab(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenLabel = { open(Screen.Label(it)) },
                    onOpenMerge = { open(Screen.MergeDuplicates) },
                    onOpenSettings = { open(Screen.Settings) },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.delete_contacts_title, selection.size, selection.size)) },
            text = { Text(stringResource(R.string.delete_contacts_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(selection.map { it.id to it.lookupKey }, selection.singleOrNull()?.name)
                    selectedIds = emptySet()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickLabel) {
        val labels by viewModel.labels.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { pickLabel = false },
            title = { Text(stringResource(R.string.add_to_label)) },
            text = {
                val all = labels.orEmpty()
                if (all.isEmpty()) {
                    Text(stringResource(R.string.no_labels))
                } else {
                    Column {
                        all.forEach { label ->
                            TextButton(onClick = {
                                pickLabel = false
                                val ids = selection.map { it.id }
                                selectedIds = emptySet()
                                scope.launch {
                                    val added = viewModel.labelStore.addMembers(label, ids)
                                    val message = if (added < ids.size) context.getString(R.string.label_add_skipped, ids.size - added)
                                    else context.resources.getQuantityString(R.plurals.added_to_label, added, added, label.title)
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(label.title, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickLabel = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Replaces the search bar while contacts are selected: how many, and what to do with them. */
@Composable
private fun SelectionBar(
    count: Int,
    allStarred: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onStar: () -> Unit,
    onLabel: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // As tall as the search bar with its padding, so nothing shifts.
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_selection)) }
        Text(
            pluralStringResource(R.plurals.selected_count, count, count),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButton(onClick = onStar) {
            Icon(
                if (allStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                stringResource(if (allStarred) R.string.remove_from_favorites else R.string.add_to_favorites),
            )
        }
        IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_all)) },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                    onClick = { menu = false; onSelectAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_label)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    onClick = { menu = false; onLabel() },
                )
            }
        }
    }
}
