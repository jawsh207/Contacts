package app.people.contacts.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.people.contacts.R

/** Ways to reach a contact through other apps. Each says so if no app can handle it. */
object Reach {
    fun dial(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** Opens the dialer for a video call; the phone app decides whether it can make one. */
    fun videoCall(context: Context, number: String) = start(
        context,
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", VIDEO_STATE_BIDIRECTIONAL),
    )

    fun message(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    fun email(context: Context, address: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))

    fun map(context: Context, address: String) =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))

    fun web(context: Context, url: String) {
        val full = if (url.contains("://")) url else "https://$url"
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(full)))
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    /** VideoProfile.STATE_BIDIRECTIONAL. */
    private const val VIDEO_STATE_BIDIRECTIONAL = 3
}
