package app.people.contacts.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import app.people.contacts.ui.contact.ProfileQrDialog
import app.people.contacts.ui.components.Avatar
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.ContactSummary
import app.people.contacts.data.SortOrder
import app.people.contacts.data.sortName
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ContactRow
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.segmentShape
import app.people.contacts.util.foldForSearch

/**
 * The Contacts tab: your info, then Favorites and everyone else in groups by first letter, with
 * an A–Z strip on the side to jump between letters. Long-press a contact to start selecting;
 * while [selected] has anyone, tapping adds or removes contacts instead of opening them.
 */
@Composable
fun ContactsTab(
    viewModel: ContactsViewModel,
    contentPadding: PaddingValues,
    onOpenContact: (Long) -> Unit,
    onSetUpProfile: () -> Unit,
    selected: Set<Long> = emptySet(),
    onToggleSelected: (Long) -> Unit = {},
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val list = contacts
    val sections = remember(list, order) { list?.let { byFirstLetter(it, order) }.orEmpty() }
    val favorites = remember(list) { list?.filter { it.starred }.orEmpty() }
    // Where each letter's header sits in the list, for the A–Z strip.
    val headerIndex = remember(sections, favorites) {
        var index = 1 + if (favorites.isNotEmpty()) 1 + favorites.size else 0
        sections.associate { (letter, members) -> (letter to index).also { index += 1 + members.size } }
    }
    val selecting = selected.isNotEmpty()
    val onTap: (ContactSummary) -> Unit = { if (selecting) onToggleSelected(it.id) else onOpenContact(it.id) }
    val onLongPress: (ContactSummary) -> Unit = { onToggleSelected(it.id) }
    val listState = rememberLazyListState()
    var showQr by remember { mutableStateOf(false) }
    profile?.let { p -> if (showQr) ProfileQrDialog(viewModel, p.id, onDismiss = { showQr = false }) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item(key = "your-info") {
                YourInfoCard(
                    profile,
                    onOpen = { p -> if (p != null) onOpenContact(p.id) else onSetUpProfile() },
                    onShowQr = { showQr = true },
                )
            }
            when {
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                list.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.Contacts,
                        stringResource(R.string.no_contacts_title),
                        stringResource(R.string.no_contacts_body),
                    )
                }
                else -> {
                    if (favorites.isNotEmpty()) {
                        item(key = "favorites-header") { FavoritesHeader() }
                        group("fav", favorites, selected, onTap, onLongPress)
                    }
                    sections.forEach { (letter, members) ->
                        item(key = "h-$letter") { SectionHeader(letter) }
                        group("c", members, selected, onTap, onLongPress)
                    }
                }
            }
        }
        if (sections.size > 1) {
            LetterScroller(
                letters = sections.map { it.first },
                onLetter = { letter -> headerIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } } },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}

private fun LazyListScope.group(
    prefix: String,
    members: List<ContactSummary>,
    selected: Set<Long>,
    onTap: (ContactSummary) -> Unit,
    onLongPress: (ContactSummary) -> Unit,
) {
    itemsIndexed(members, key = { _, c -> "$prefix-${c.id}" }) { index, contact ->
        ContactRow(
            contact,
            segmentShape(index, members.size),
            onClick = { onTap(contact) },
            selected = contact.id in selected,
            onLongClick = { onLongPress(contact) },
            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
        )
    }
}

/**
 * The A–Z strip: tap or drag along it to jump to a letter. While dragging, the letter shows
 * large beside it.
 */
@Composable
private fun LetterScroller(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableIntStateOf(0) }
    fun pick(y: Float) {
        if (height == 0) return
        val letter = letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != active) {
            active = letter
            onLetter(letter)
        }
    }
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        Column(
            // The letters sit in the list's right margin; the touch area reaches a little past it.
            Modifier
                .width(24.dp)
                .onSizeChanged { height = it.height }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ -> pick(change.position.y) }
                }
                .pointerInput(letters) {
                    detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); active = null })
                },
            horizontalAlignment = Alignment.End,
        ) {
            letters.forEach { letter ->
                Box(Modifier.width(16.dp).height(LETTER_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                        color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        active?.let { letter ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 56.dp).size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private val LETTER_HEIGHT = 18.dp

/** Contacts in letter groups, in the order they're already sorted; anything else under "#". */
private fun byFirstLetter(contacts: List<ContactSummary>, order: SortOrder): List<Pair<String, List<ContactSummary>>> =
    contacts.groupBy { c ->
        foldForSearch(c.sortName(order)).firstOrNull { it.isLetterOrDigit() }
            ?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    }.toList().sortedBy { (letter, _) -> if (letter == "#") "￿" else letter }

/** The user's own card at the top of the list, or an invitation to set it up. */
@Composable
private fun YourInfoCard(profile: ContactSummary?, onOpen: (ContactSummary?) -> Unit, onShowQr: () -> Unit) {
    Surface(
        onClick = { onOpen(profile) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile?.name, profile?.photoUri, profile?.id ?: 0, 56.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    profile?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.your_info_set_up),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (profile != null) R.string.your_info else R.string.your_info_set_up_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile != null) {
                IconButton(onClick = onShowQr) {
                    Icon(Icons.Rounded.QrCode2, stringResource(R.string.show_qr), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Favorites" with a star, in the theme's primary colour, as in Google Contacts. */
@Composable
private fun FavoritesHeader() {
    Row(
        Modifier.padding(start = 28.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.favorites),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Shown until contacts access is granted. */
@Composable
fun AccessScreen(onAllow: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyState(
            Icons.Rounded.Contacts,
            stringResource(R.string.access_title),
            stringResource(R.string.access_body),
            action = { Button(onClick = onAllow) { Text(stringResource(R.string.access_allow)) } },
        )
    }
}
