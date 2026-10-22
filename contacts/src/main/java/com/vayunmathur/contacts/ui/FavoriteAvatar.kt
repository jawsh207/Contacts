package com.vayunmathur.contacts.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.library.ui.MaterialShapes
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Size of a favorite's avatar on the Highlights tab, as in the Dialtone dialer. */
internal val FAVORITE_AVATAR_SIZE = 68.dp

/** The initial's size, as a fraction of the avatar's. */
private const val INITIAL_FRACTION = 0.42f

/** Shapes a favorite's avatar can take, in the dialer's order. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val FAVORITE_SHAPES by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Pentagon,
        MaterialShapes.Arch,
        MaterialShapes.Gem,
        MaterialShapes.Puffy,
    )
}

/**
 * A favorite's avatar on the Highlights tab, matching the Dialtone dialer: a Material 3
 * Expressive shape and container colour, both picked from the contact's
 * ContactsContract.Contacts._ID so a person looks the same in both apps. The photo is cropped
 * to fill the shape; without one, the initial sits on the colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FavoriteAvatar(
    contact: Contact,
    decodePhoto: (String) -> Bitmap?,
    size: Dp = FAVORITE_AVATAR_SIZE,
) {
    // The aggregate id is what the dialer sees; fall back to this app's id if it isn't known.
    val id = contact.aggregateId.takeIf { it != 0L } ?: contact.id
    val index = (id.hashCode() and Int.MAX_VALUE) % FAVORITE_SHAPES.size
    val shape = FAVORITE_SHAPES[index].toShape()
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (index % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val photoBase64 = contact.photo?.photo
    val photo by produceState<Bitmap?>(initialValue = null, key1 = photoBase64) {
        value = if (photoBase64 != null) withContext(Dispatchers.IO) { decodePhoto(photoBase64) } else null
    }
    val initialSize = with(LocalDensity.current) { (size * INITIAL_FRACTION).toSp() }

    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        val bmp = photo
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.contact_photo_description, contact.name.value),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(container), contentAlignment = Alignment.Center) {
                Text(
                    text = contact.name.value.firstOrNull()?.uppercase() ?: "",
                    color = content,
                    fontSize = initialSize,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
