package com.example.widget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

private const val ACTIVITY_TEXT_SIZE_SP = 20f

/** Displays the minimal launcher activity included by this starter variant. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                gravity = Gravity.CENTER
                text = getString(R.string.activity_text)
                textSize = ACTIVITY_TEXT_SIZE_SP
            },
        )
    }
}
