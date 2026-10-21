package com.vayunmathur.contacts.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.MyInfo
import com.vayunmathur.library.ui.IconButton
import com.vayunmathur.library.ui.IconPerson
import com.vayunmathur.library.ui.IconShare
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.ListItemDefaults
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Surface
import com.vayunmathur.library.ui.Text
import java.io.File

/** The user's photo from [MyInfo], or their initial (or a person icon) on a tonal circle. */
@Composable
internal fun MyInfoAvatar(info: MyInfo?, size: Dp, initialsStyle: TextStyle = MaterialTheme.typography.titleLarge) {
    val bitmap = remember(info?.photo) {
        info?.photo?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initial = info?.name?.trim()?.firstOrNull()?.uppercase()
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            initial != null -> Text(initial, style = initialsStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
            else -> IconPerson(tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Google Contacts' "Your info" card at the top of the Contacts tab: photo, "Your info", name,
 * and a share button. Before anything is filled in it invites the user to add their info.
 */
@Composable
internal fun MyInfoCard(info: MyInfo?, onOpen: () -> Unit, onShare: () -> Unit) {
    val hasInfo = info != null && !info.isEmpty
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        ListItem(
            leadingContent = { MyInfoAvatar(info, 48.dp) },
            trailingContent = if (hasInfo) {
                { IconButton(onClick = onShare) { IconShare() } }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Column {
                Text(
                    stringResource(R.string.your_info),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (hasInfo) info!!.name.ifBlank { info.phone } else stringResource(R.string.your_info_add),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

/** A black-on-white QR code of [text] as a square bitmap; null if it cannot be encoded. */
internal fun qrBitmap(text: String, sizePx: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** Shares [info] as a .vcf file through the system share sheet. */
internal fun shareMyInfo(context: Context, info: MyInfo, chooserTitle: String) {
    val file = File(context.cacheDir, "my_contact.vcf")
    file.writeText(info.toVCard())
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/x-vcard"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
