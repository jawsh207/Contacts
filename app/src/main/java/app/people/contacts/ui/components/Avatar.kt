package app.people.contacts.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.people.contacts.util.initialOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val expressiveShapes by lazy {
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

/** A contact's avatar colours: container and content, picked from [seed] as Dialtone does. */
@Composable
fun avatarColors(seed: Long): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when ((seed.hashCode() and Int.MAX_VALUE) % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

/** Loads an image from a content URI off the main thread; null while loading or if it fails. */
@Composable
fun rememberPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, uri) {
        value = uri?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(it))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return photo
}

/**
 * A contact's avatar, identical to Dialtone's: the photo, or the first initial on a colour
 * picked from [seed] (ContactsContract.Contacts._ID). Circles in lists; [expressive] picks a
 * stable Material 3 Expressive shape for the contact instead (favorites).
 */
@Composable
fun Avatar(
    name: String?,
    photoUri: String?,
    seed: Long,
    size: Dp,
    modifier: Modifier = Modifier,
    expressive: Boolean = false,
) {
    val index = seed.hashCode() and Int.MAX_VALUE
    // toShape() is composable (and remembers internally) in Material 3 1.5.
    val shape: Shape =
        if (expressive) expressiveShapes[index % expressiveShapes.size].toShape() else CircleShape
    val (bg, fg) = avatarColors(seed)
    val photo = rememberPhoto(photoUri)
    val initial = initialOf(name)
    Box(modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        when {
            photo != null -> Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            initial != null -> Text(
                initial,
                color = fg,
                style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium),
            )
            else -> Icon(Icons.Rounded.Person, null, tint = fg, modifier = Modifier.size(size * 0.55f))
        }
    }
}
