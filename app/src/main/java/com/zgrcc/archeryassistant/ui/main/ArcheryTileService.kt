package com.zgrcc.archeryassistant

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 磁貼（v1.11）：顯示今天已射的箭數，以及「開始偵測」「開始比賽」兩個按鈕。
 * 按鈕只是打開應用並帶一個啟動參數，真正開始與否由應用端依「磁貼快捷啟動」設定決定。
 */
class ArcheryTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = applicationContext.getSharedPreferences("archery", Context.MODE_PRIVATE)
        val settings = AppSettings(prefs)
        I18n.english = I18n.resolveEnglish(settings.language.value, java.util.Locale.getDefault().language)

        val done = try {
            val dir = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
            todayDone(SessionStore(dir).listSessions(), System.currentTimeMillis(), excludeId = null)
        } catch (e: Exception) {
            0
        }
        val line = TileInfo.todayLine(done, settings.dailyGoal.value)

        val layout = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(label(line, 16f, bold = true))
            .addContent(spacer(8f))
            .addContent(button(tr("開始偵測", "Start detection"), TileInfo.LAUNCH_DETECT, 0xFF2E7D32.toInt()))
            .addContent(spacer(6f))
            .addContent(button(tr("開始比賽", "Start match"), TileInfo.LAUNCH_MATCH, 0xFF37474F.toInt()))
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .setFreshnessIntervalMillis(60_000L)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun label(text: String, size: Float, bold: Boolean): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(size))
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

    private fun spacer(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp)).build()

    private fun button(text: String, launch: String, color: Int): LayoutElementBuilders.LayoutElement {
        val action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(TileInfo.EXTRA_LAUNCH, ActionBuilders.stringExtra(launch))
                    .build()
            )
            .build()
        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("launch_$launch")
                    .setOnClick(action)
                    .build()
            )
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(color))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(20f)).build())
                    .build()
            )
            .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(10f)).build())
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(150f))
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(modifiers)
            .addContent(label(text, 15f, bold = false))
            .build()
    }

    private companion object {
        const val RES_VERSION = "1"
    }
}
