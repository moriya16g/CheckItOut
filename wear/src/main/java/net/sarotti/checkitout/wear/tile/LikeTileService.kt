package net.sarotti.checkitout.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.sarotti.checkitout.wear.MainActivity
import net.sarotti.checkitout.wear.R

/** Tile with a single button that opens [MainActivity], which sends the like. */
class LikeTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(CLICK_ID)
            .setOnClick(launch)
            .build()
        val button = Button.Builder(this, clickable)
            .setIconContent(ICON_ID)
            .setContentDescription(getString(R.string.like))
            .setSize(ButtonDefaults.LARGE_SIZE)
            .build()
        val layout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
            .setPrimaryLabelTextContent(
                Text.Builder(this, getString(R.string.app_name))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .build()
            )
            .setContent(button)
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val icon = ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(R.drawable.ic_like)
                    .build()
            )
            .build()
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(ICON_ID, icon)
                .build()
        )
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val ICON_ID = "ic_like"
        const val CLICK_ID = "like"
    }
}
