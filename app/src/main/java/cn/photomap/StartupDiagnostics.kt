package cn.photomap

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File

/** Small private diagnostic, with no image, coordinates or database contents. */
class StartupDiagnostics(private val context: Context) {
    private val prefs=context.getSharedPreferences("startup-diagnostics",Context.MODE_PRIVATE)
    private val crashFile=File(context.filesDir,"last-java-crash.txt")
    private val version=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "unknown"
    fun installCrashRecorder() {
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler {thread,error->
            runCatching {crashFile.writeText("${System.currentTimeMillis()} ${thread.name}\n"+error.stackTraceToString().take(16000))}
            previous?.uncaughtException(thread,error)
        }
    }
    private fun lastExit(): ApplicationExitInfo? = if(Build.VERSION.SDK_INT>=30) runCatching {
        context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName,0,5).firstOrNull()
    }.getOrNull() else null
    fun previousMapStartupCrashed(): Boolean {
        if(prefs.getString("pending-version",null)!=version || Build.VERSION.SDK_INT<30) return false
        val exit=lastExit() ?: return false
        return exit.timestamp>=prefs.getLong("pending-time",Long.MAX_VALUE) &&
            exit.reason in listOf(ApplicationExitInfo.REASON_CRASH,ApplicationExitInfo.REASON_CRASH_NATIVE,ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)
    }
    fun beginMapStartup() {prefs.edit().putString("pending-version",version).putLong("pending-time",System.currentTimeMillis()).commit()}
    fun mapReady() {prefs.edit().remove("pending-version").remove("pending-time").apply()}
    fun record(error: Throwable) {runCatching {crashFile.writeText(error.stackTraceToString().take(16000))}}
    fun report(): String = buildString {
        appendLine("PhotoMap $version startup diagnostic")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Memory page bytes: ${runCatching {Os.sysconf(OsConstants._SC_PAGESIZE)}.getOrDefault(-1)}")
        appendLine("MapLibre 11.8.0; offline China overview Z0-10 / context Z0-6 / imported national streets Z0-15 and EOX 2016 urban imagery")
        if(Build.VERSION.SDK_INT>=30) lastExit()?.let {appendLine("Previous exit: reason=${it.reason}; time=${it.timestamp}; ${it.description}")}
        appendLine("Java failure:\n"+if(crashFile.isFile) crashFile.readText().take(16000) else "No recorded Java exception")
    }
}
