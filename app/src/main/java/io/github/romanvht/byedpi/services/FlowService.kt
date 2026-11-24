package io.github.romanvht.byedpi.services

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.FlowActivity
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getSelectedApps
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

class FlowService : Service() {
    companion object {
        val state = MutableStateFlow("Отключено")
        @Volatile var active = false
            private set
        const val STOP = "stop-flow"
    }

    @Volatile private var worker: Process? = null
    @Volatile private var stopping = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopping = true
            worker?.outputStream?.runCatching { close() }
            if (!active) stopSelf()
            return START_NOT_STICKY
        }
        if (active) return START_NOT_STICKY
        active = true
        stopping = false
        val name = intent?.getStringExtra("profile") ?: "general"
        state.value = "Запуск · $name"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(NotificationChannel("flow", "Flowseal", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, "flow")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Flowseal · $name")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, FlowActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "Отключить", PendingIntent.getService(this, 1, Intent(this, FlowService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).build()
        startForeground(214, notification)
        Thread {
            var result = "Отключено"
            try {
                check(appStatus.first == AppStatus.Halted && !TestService.isRunning) { "Сначала остановите ByeDPI" }
                val error = runProfile(name)
                if (!stopping) result = "Ошибка: $error"
            } catch (e: Exception) {
                if (!stopping) result = "Ошибка: ${e.message}"
            } finally {
                worker?.outputStream?.runCatching { close() }
                worker = null
                android.os.Handler(mainLooper).post {
                    active = false
                    state.value = result
                    stopForeground(true)
                    stopSelf()
                }
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun runProfile(name: String): String {
        val dir = File(filesDir, "flowseal").apply { mkdirs() }
        val profile = assets.open("flowseal/profiles.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject(name) }
        for (file in assets.list("flowseal").orEmpty().filter { it.endsWith(".bin") || it.endsWith(".txt") }) {
            assets.open("flowseal/$file").use { input -> File(dir, file).outputStream().use { input.copyTo(it) } }
        }
        val prefs = getPreferences()
        val mode = prefs.getString("applist_type", "disable") ?: "disable"
        require(mode in setOf("whitelist", "blacklist", "disable"))
        val uids = prefs.getSelectedApps().mapNotNull {
            try { packageManager.getApplicationInfo(it, 0).uid } catch (_: PackageManager.NameNotFoundException) { null }
        }.distinct()
        check(mode != "whitelist" || uids.isNotEmpty()) { getString(R.string.missing_apps_quick) }
        val argv = mutableListOf(File(applicationInfo.nativeLibraryDir, "libflow_nfqueue.so").absolutePath,
            dir.absolutePath, profile.getString("tcp"), profile.getString("udp"), mode, uids.joinToString(" "))
        val args = profile.getJSONArray("args")
        for (i in 0 until args.length()) argv.add(args.getString(i))
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val script = assets.open("flow_session.sh").bufferedReader().use { it.readText() }
        if (stopping) return ""
        val process = ProcessBuilder("su", "-c", "set -- " + argv.joinToString(" ") { quote(it) } + "\n" + script)
            .redirectErrorStream(true).start()
        worker = process
        if (stopping) process.outputStream.close()
        var message = "Движок завершил работу"
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line == "ZMOB_READY") state.value = "Подключено · $name"
                else if (line.isNotBlank()) message = line.take(240)
            }
        }
        process.waitFor()
        return message
    }

    override fun onDestroy() {
        stopping = true
        worker?.outputStream?.runCatching { close() }
        super.onDestroy()
    }
}
