package cn.photomap

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeOnlineImportMemoriesTest {
    @Test fun batchExifImportManualReviewAndSwipeAnniversary() = runBlocking<Unit> {
        assumeTrue(BuildConfig.ONLINE_MAPS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PhotoMapApplication
        val fixtures = File(app.cacheDir, "anniversary-${UUID.randomUUID()}").apply { mkdirs() }
        val anchor = LocalDate.now().minusYears(1)
        val dates = listOf(anchor.minusDays(3), anchor, anchor.plusDays(4), anchor)
        val names = dates.indices.map { "${fixtures.name}-$it.jpg" }
        val label = java.time.format.DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
        fun makePhoto(index: Int): Uri {
            val file = File(fixtures, names[index])
            val bitmap = Bitmap.createBitmap(128, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(listOf(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN)[index])
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 91, it) }
            bitmap.recycle()
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dates[index].toString().replace('-', ':') + " 12:30:00")
                setAttribute(ExifInterface.TAG_MODEL, "LiuYing QA")
                if (index < 3) setLatLong(30.65 + index * 0.002, 104.07)
                saveAttributes()
            }
            return Uri.fromFile(file)
        }
        fun find(node: AccessibilityNodeInfo?, text: String, exact: Boolean): AccessibilityNodeInfo? {
            if (node == null) return null
            fun matches(value: CharSequence?) = if (exact) value?.toString() == text else value?.contains(text) == true
            if ((matches(node.text) || matches(node.contentDescription)) &&
                node.isVisibleToUser && node.isEnabled) return node
            for (index in 0 until node.childCount) {
                find(node.getChild(index), text, exact)?.let { return it }
            }
            return null
        }
        fun waitFor(text: String, exact: Boolean = false): AccessibilityNodeInfo {
            repeat(120) {
                find(instrumentation.uiAutomation.rootInActiveWindow, text, exact)?.let { return it }
                SystemClock.sleep(100)
            }
            throw AssertionError("Visible text not found: $text")
        }
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { output -> output.readBytes() }
            }
        }
        fun tap(text: String, exact: Boolean = false) {
            val bounds = android.graphics.Rect()
            SystemClock.sleep(500)
            waitFor(text, exact).getBoundsInScreen(bounds)
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
        }
        fun capture(name: String) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(app.getExternalFilesDir(null), name).outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
        val width = app.resources.displayMetrics.widthPixels
        val height = app.resources.displayMetrics.heightPixels
        fun revealBelow(text: String, start: Int, end: Int, safeBottom: Int = Int.MAX_VALUE) {
            repeat(5) {
                val node = find(instrumentation.uiAutomation.rootInActiveWindow, text, false)
                val bounds = android.graphics.Rect()
                node?.getBoundsInScreen(bounds)
                if (node != null && bounds.bottom <= safeBottom) return
                shell("input swipe ${width / 2} $start ${width / 2} $end 320")
                SystemClock.sleep(250)
            }
            waitFor(text)
        }
        fun assertAboveNavigation(text: String) {
            val bounds = android.graphics.Rect()
            waitFor(text).getBoundsInScreen(bounds)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                val safeBottom = screenshot.height - (24 * app.resources.displayMetrics.density).toInt()
                assertTrue("$text overlaps the navigation area: $bounds", bounds.bottom <= safeBottom)
            } finally {
                screenshot.recycle()
            }
        }
        try {
            shell("svc wifi enable")
            shell("svc data enable")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val uris = dates.indices.map(::makePhoto)
                waitFor("导入照片")
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredMethod("importDirectPhotos", List::class.java)
                        .apply { isAccessible = true }.invoke(activity, uris)
                }
                waitFor("补填导入信息")
                assertAboveNavigation("放弃待补填")
                val imported = app.library.photoDays().flatMap { it.photos }.filter { it.photo.filename in names }
                assertEquals(3, imported.size)
                assertTrue(imported.all { it.photo.exif.contains("LiuYing QA") })
                assertTrue(imported.any { it.photo.date == anchor.toString() })
                revealBelow("地图选点", height * 7 / 10, height / 3)
                capture("online053-review.png")
                tap("地图选点")
                waitFor("当前导入的照片")
                tap("搜索地名或经纬度")
                shell("input text 48.8566,2.3522")
                tap("搜索", exact = true)
                waitFor(", 2.352")
                capture("online05-location-picker.png")
                tap("确定位置")
                assertAboveNavigation("保存 1 张")
                tap("保存 1 张")
                waitFor("忆往昔")
                val complete = app.library.photoDays().flatMap { it.photos }.filter { it.photo.filename in names }
                assertEquals(4, complete.size)
                tap("忆往昔")
                waitFor("回到 ${anchor.format(label)} 前后")
                capture("online04-memories.png")
                waitFor("去年今天 · 2 张")
                val swipeTop = height * 19 / 100
                val swipeBottom = height * 85 / 100
                shell("input swipe ${width / 2} $swipeTop ${width / 2} $swipeBottom 360")
                waitFor(anchor.minusDays(3).format(label))
                instrumentation.waitForIdleSync()
                shell("input swipe ${width / 2} $swipeBottom ${width / 2} $swipeTop 360")
                waitFor("去年今天 · 2 张")
                instrumentation.waitForIdleSync()
                shell("input swipe ${width / 2} $swipeBottom ${width / 2} $swipeTop 360")
                waitFor(anchor.plusDays(4).format(label))
                tap(names[2], exact = true)
                tap("信息", exact = true)
                revealBelow("GPS纬度", height * 9 / 10, height * 6 / 10)
                revealBelow("删除应用内副本", height * 9 / 10, height * 6 / 10,
                    safeBottom = height - (24 * app.resources.displayMetrics.density).toInt())
                waitFor("编辑文字与参数")
                assertAboveNavigation("删除应用内副本")
                capture("online053-photo-details.png")
                tap("返回")
                waitFor("忆往昔")
                tap("返回")
                complete.filter { it.photo.date == anchor.toString() }.forEach { app.library.removePhoto(it.photo.id) }
                tap("忆往昔")
                waitFor("最接近去年今天 · 1 张")
                waitFor(anchor.minusDays(3).format(label))
            }
        } finally {
            app.library.photoDays().flatMap { it.photos }.filter { it.photo.filename in names }
                .forEach { app.library.removePhoto(it.photo.id) }
            fixtures.deleteRecursively()
        }
    }
}
