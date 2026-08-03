package com.github.panlelapin.mementomori

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Minimal launcher screen for the application icon in the app drawer. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.activity_text)
            },
        )
    }
}
