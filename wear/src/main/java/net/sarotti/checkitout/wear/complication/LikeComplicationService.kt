package net.sarotti.checkitout.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import net.sarotti.checkitout.wear.MainActivity
import net.sarotti.checkitout.wear.R

/** Watch-face complication; tapping it opens [MainActivity], which sends the like. */
class LikeComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, tapIntent())

    private fun build(type: ComplicationType, tap: PendingIntent?): ComplicationData? {
        val description = PlainComplicationText.Builder(getString(R.string.like)).build()
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_like)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(getString(R.string.like)).build(),
                    description,
                ).setMonochromaticImage(image).setTapAction(tap).build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(image, description)
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
