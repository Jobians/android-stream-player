package dev.jt.streamplayer

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ErrorActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_TITLE = "EXTRA_TITLE"
    const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
    const val EXTRA_IS_FATAL = "EXTRA_IS_FATAL"
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Error"
    val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "No details provided"
    val isFatal = intent.getBooleanExtra(EXTRA_IS_FATAL, true)

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#121212"))
    }

    ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
      val bars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.setPadding(
        dp(16) + bars.left,
        dp(16) + bars.top,
        dp(16) + bars.right,
        dp(16) + bars.bottom
      )
      insets
    }

    val titleView = TextView(this).apply {
      text = title
      textSize = 18f
      setTextColor(if (isFatal) Color.RED else Color.parseColor("#FF9800"))
      setPadding(0, 0, 0, dp(12))
    }

    val scrollView = ScrollView(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        0,
        1f
      )
    }

    val errorTextView = TextView(this).apply {
      text = message
      textSize = 12f
      setTextColor(Color.WHITE)
      setTextIsSelectable(true)
      typeface = Typeface.MONOSPACE
    }

    scrollView.addView(errorTextView)

    val actionButton = Button(this).apply {
      text = if (isFatal) "Close app" else "Back"
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = dp(12)
      }
      setOnClickListener {
        if (isFatal) {
          finishAffinity()
        } else {
          finish()
        }
      }
    }

    container.addView(titleView)
    container.addView(scrollView)
    container.addView(actionButton)

    setContentView(container)
  }
}
