package app.people.contacts.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.ContactSummary
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ContactRow
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.segmentShape
import app.people.contacts.util.foldForSearch
import app.people.contacts.util.formatNumber

/** Search by name (in either order), phone number or email, as you type. */
@Composable
fun SearchScreen(viewModel: ContactsViewModel, onBack: () -> Unit, onOpenContact: (Long) -> Unit) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(contacts, query) { search(contacts.orEmpty(), query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item(key = "field") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onBack = onBack,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                        .focusRequester(focus),
                )
            }
            if (query.isNotBlank() && results.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        Icons.Rounded.SearchOff,
                        stringResource(R.string.search_no_results_title),
                        stringResource(R.string.search_no_results_body, query.trim()),
                    )
                }
            }
            itemsIndexed(results, key = { _, r -> r.contact.id }) { index, result ->
                ContactRow(
                    contact = result.contact,
                    shape = segmentShape(index, results.size),
                    onClick = { onOpenContact(result.contact.id) },
                    supporting = result.matchedDetail,
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
            item(key = "bottom") { androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.fillMaxWidth().height(56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_contacts)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                }
            }
        }
    }
}

/** A search hit, with the number or email that matched when it wasn't the name. */
private data class SearchResult(val contact: ContactSummary, val matchedDetail: String?)

private fun search(contacts: List<ContactSummary>, rawQuery: String): List<SearchResult> {
    val query = foldForSearch(rawQuery.trim())
    if (query.isEmpty()) return emptyList()
    val digits = rawQuery.filter { it.isDigit() }
    // A query that's mostly digits is a phone number search.
    val numberSearch = digits.length >= 2 && digits.length * 2 >= rawQuery.count { !it.isWhitespace() }
    return contacts.mapNotNull { c ->
        when {
            foldForSearch(c.name).contains(query) || foldForSearch(c.nameAlternative).contains(query) ->
                SearchResult(c, null)
            numberSearch -> c.numbers.firstOrNull { it.filter(Char::isDigit).contains(digits) }
                ?.let { SearchResult(c, formatNumber(it)) }
            else -> c.emails.firstOrNull { foldForSearch(it).contains(query) }?.let { SearchResult(c, it) }
        }
    }
}
