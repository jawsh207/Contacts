package app.people.contacts.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.people.contacts.R
import app.people.contacts.ui.components.BottomBar

/** The three bottom tabs, in order. Contacts is home. */
enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Contacts(R.string.tab_contacts, Icons.Outlined.Person, Icons.Filled.Person),
    Highlights(R.string.tab_highlights, Icons.Outlined.Assistant, Icons.Filled.Assistant),
    Organize(R.string.tab_organize, Icons.Outlined.Book, Icons.Filled.Book),
}

/** The app's main screen: the current tab above the bottom bar. */
@Composable
fun ContactsApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Contacts) }
    // Back from Highlights or Organize returns to Contacts; Back from Contacts leaves the app.
    BackHandler(enabled = tab != Tab.Contacts) { tab = Tab.Contacts }

    Scaffold(
        bottomBar = {
            BottomBar {
                Tab.entries.forEach { entry ->
                    val selected = entry == tab
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { tab = entry },
                        icon = { Icon(if (selected) entry.selectedIcon else entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
