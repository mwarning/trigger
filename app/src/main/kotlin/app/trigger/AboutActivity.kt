/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = getString(R.string.menu_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        findViewById<TextView>(R.id.versionTv).text = if (BuildConfig.DEBUG) {
            BuildConfig.VERSION_NAME + " (debug)"
        } else {
            BuildConfig.VERSION_NAME
        }

        findViewById<TextView>(R.id.licenseTV).setOnClickListener {
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }
    }
}
