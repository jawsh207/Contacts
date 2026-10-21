package com.vayunmathur.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.ui.IconBack
import com.vayunmathur.library.ui.IconButton
import com.vayunmathur.library.ui.MaterialTheme

/**
 * A top-bar icon drawn in a light circular container, as on Google Contacts' contact page,
 * so it stays legible over the blurred photo behind the bar.
 */
@Composable
internal fun CircleIcon(
    container: Color = MaterialTheme.colorScheme.surfaceBright,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The contact page's back button: a back arrow in a [CircleIcon]. */
@Composable
internal fun CircleBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        CircleIcon { IconBack() }
    }
}
