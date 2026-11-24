package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.romanvht.byedpi.services.FlowService
import io.github.romanvht.byedpi.utility.getPreferences
import kotlinx.coroutines.launch
import org.json.JSONObject

class FlowActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profiles = assets.open("flowseal/profiles.json").bufferedReader().use {
            JSONObject(it.readText()).keys().asSequence().sorted().toList()
        }
        val prefs = getPreferences()
        var selected = prefs.getString("flow_profile", "general")?.takeIf { it in profiles } ?: "general"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val margin = (24 * resources.displayMetrics.density).toInt()
            setPadding(margin, margin, margin, margin)
        }
        val heading = TextView(this).apply { text = "Flowseal · root"; textSize = 26f }
        val help = TextView(this).apply { text = "Используется список приложений с главного экрана. Требуются root и NFQUEUE." }
        val status = TextView(this).apply { accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val picker = Button(this).apply {
            text = selected
            setOnClickListener {
                AlertDialog.Builder(this@FlowActivity).setTitle("Стратегия Flowseal")
                    .setSingleChoiceItems(profiles.toTypedArray(), profiles.indexOf(selected)) { dialog, index ->
                        selected = profiles[index]
                        text = selected
                        prefs.edit().putString("flow_profile", selected).apply()
                        dialog.dismiss()
                    }.show()
            }
        }
        val toggle = Button(this).apply {
            setOnClickListener {
                val service = Intent(this@FlowActivity, FlowService::class.java)
                if (FlowService.active) startService(service.setAction(FlowService.STOP))
                else ContextCompat.startForegroundService(this@FlowActivity, service.putExtra("profile", selected))
            }
        }
        listOf(heading, help, picker, status, toggle).forEach { panel.addView(it) }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(panel) })
        lifecycleScope.launch {
            FlowService.state.collect {
                status.text = it
                toggle.text = if (FlowService.active) "Отключить" else "Подключить"
                picker.isEnabled = !FlowService.active
            }
        }
    }
}
