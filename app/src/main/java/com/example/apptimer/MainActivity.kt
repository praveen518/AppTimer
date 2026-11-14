package com.example.apptimer

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private val pkgs = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply { textSize = 16f }
        val enableBtn = Button(this).apply {
            text = "1. Turn on App Timer service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val battBtn = Button(this).apply {
            text = "2. Allow background running (battery)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(this).apply { text = "3. Time limit (minutes): " }
        val limit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("limit", 15).toString())
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                val m = limit.text.toString().toIntOrNull() ?: 15
                val v = if (m < 1) 1 else m
                prefs.edit().putInt("limit", v).apply()
                limit.setText(v.toString())
                Toast.makeText(this@MainActivity, "Saved: $v minutes", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(label)
        row.addView(limit)
        row.addView(save)
        val hint = TextView(this).apply { text = "4. Tick the apps to monitor:" }
        val list = ListView(this)

        root.addView(status)
        root.addView(enableBtn)
        root.addView(battBtn)
        root.addView(row)
        root.addView(hint)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
            runOnUiThread {
                pkgs.clear()
                pkgs.addAll(items.map { it.first })
                list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
                list.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_multiple_choice, items.map { it.second }
                )
                val saved = prefs.getStringSet("apps", emptySet()) ?: emptySet()
                pkgs.forEachIndexed { idx, p -> list.setItemChecked(idx, saved.contains(p)) }
                list.setOnItemClickListener { _, _, _, _ ->
                    val s = HashSet<String>()
                    for (k in pkgs.indices) if (list.isItemChecked(k)) s.add(pkgs[k])
                    prefs.edit().putStringSet("apps", s).apply()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        status.text = if (enabled.contains(packageName)) "Service status: ON" else "Service status: OFF (do step 1)"
    }
}
