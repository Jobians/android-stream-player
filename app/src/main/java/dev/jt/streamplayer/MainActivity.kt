package dev.jt.streamplayer

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
class MainActivity : AppCompatActivity() {
  private var player: ExoPlayer? = null
  private lateinit var playerView: PlayerView
  private lateinit var urlInput: EditText
  private lateinit var playButton: Button
  private lateinit var settingsButton: ImageButton
  private lateinit var savedUrlsButton: ImageButton

  private lateinit var prefs: SharedPreferences
  private var currentUrl: String = ""
  private var hasSavedCurrentUrl: Boolean = false

  data class PlayerSettings(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val liveTargetOffsetMs: Long,
    val forceRtspTcp: Boolean,
    val repeatMode: Int,
    val resizeMode: Int
  )

  companion object {
    private const val PREFS_NAME = "exoplayer_prefs"
    private const val KEY_LAST_URL = "last_url"
    private const val KEY_SAVED_URLS = "saved_urls"
    private const val KEY_MIN_BUFFER = "min_buffer_ms"
    private const val KEY_MAX_BUFFER = "max_buffer_ms"
    private const val KEY_BUFFER_PLAYBACK = "buffer_for_playback_ms"
    private const val KEY_BUFFER_PLAYBACK_REBUFFER = "buffer_for_playback_after_rebuffer_ms"
    private const val KEY_LIVE_OFFSET = "live_target_offset_ms"
    private const val KEY_FORCE_TCP = "force_rtsp_tcp"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_RESIZE_MODE = "resize_mode"

    private const val DEFAULT_MIN_BUFFER = 1000
    private const val DEFAULT_MAX_BUFFER = 5000
    private const val DEFAULT_BUFFER_PLAYBACK = 250
    private const val DEFAULT_BUFFER_PLAYBACK_REBUFFER = 500
    private const val DEFAULT_LIVE_OFFSET = 0L
    private const val DEFAULT_FORCE_TCP = true
    private const val DEFAULT_REPEAT_MODE = Player.REPEAT_MODE_OFF
    private val DEFAULT_RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private const val MAX_SAVED_URLS = 20
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
      runOnUiThread {
        if (isUnrecoverableStreamParsingError(throwable)) {
          handleUnplayableStream(throwable)
        } else {
          showCrashOnScreen(throwable)
        }
      }
    }

    try {
      WindowCompat.setDecorFitsSystemWindows(window, false)
      setContentView(R.layout.activity_main)

      prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

      val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
      playerView = findViewById(R.id.playerView)
      urlInput = findViewById(R.id.urlInput)
      playButton = findViewById(R.id.playButton)
      settingsButton = findViewById(R.id.settingsButton)
      savedUrlsButton = findViewById(R.id.savedUrlsButton)
      val topBar = findViewById<View>(R.id.topBar)

      val topBarBase = Rect(
        topBar.paddingLeft, topBar.paddingTop, topBar.paddingRight, topBar.paddingBottom
      )

      ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
        val bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(0, 0, 0, bars.bottom)
        topBar.setPadding(
          topBarBase.left + bars.left,
          topBarBase.top + bars.top,
          topBarBase.right + bars.right,
          topBarBase.bottom
        )
        insets
      }

      urlInput.setText(prefs.getString(KEY_LAST_URL, "") ?: "")

      playButton.setOnClickListener {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
          Toast.makeText(this, "Enter a URL first", Toast.LENGTH_SHORT).show()
        } else {
          playUrl(url)
        }
      }

      settingsButton.setOnClickListener { showSettingsDialog() }
      savedUrlsButton.setOnClickListener { showSavedUrlsDialog() }

      playerView.setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
          if (player != null) {
            topBarGroupVisibility(visibility)
          } else {
            topBarGroupVisibility(View.VISIBLE)
          }
        }
      )
    } catch (e: Throwable) {
      showCrashOnScreen(e)
    }
  }

  private fun topBarGroupVisibility(visibility: Int) {
    findViewById<View>(R.id.topBar).visibility = visibility

    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
    if (visibility == View.VISIBLE) {
      insetsController.show(WindowInsetsCompat.Type.systemBars())
    } else {
      insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  override fun onStart() {
    super.onStart()

    try {
      val savedUrl = prefs.getString(KEY_LAST_URL, "") ?: ""
      if (savedUrl.isNotEmpty()) {
        playUrl(savedUrl)
      }
    } catch (e: Throwable) {
      showCrashOnScreen(e)
    }
  }

  override fun onResume() {
    super.onResume()
    if (player == null) {
      topBarGroupVisibility(View.VISIBLE)
    }
  }

  private fun playUrl(url: String) {
    try {
      currentUrl = url
      hasSavedCurrentUrl = false
      urlInput.setText(url)
      releasePlayer()
      initializePlayer(url, loadSettings())
    } catch (e: Throwable) {
      showCrashOnScreen(e)
    }
  }

  private fun initializePlayer(url: String, settings: PlayerSettings) {
    val loadControl = DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        settings.minBufferMs,
        settings.maxBufferMs,
        settings.bufferForPlaybackMs,
        settings.bufferForPlaybackAfterRebufferMs
      )
      .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
      .build()

    val mediaSourceFactory = DefaultMediaSourceFactory(this)
    mediaSourceFactory.setLiveTargetOffsetMs(settings.liveTargetOffsetMs)

    val newPlayer = ExoPlayer.Builder(this)
      .setMediaSourceFactory(mediaSourceFactory)
      .setLoadControl(loadControl)
      .build()

    player = newPlayer
    playerView.player = newPlayer
    playerView.resizeMode = settings.resizeMode

    newPlayer.addListener(object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        handleUnplayableStream(error)
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && !hasSavedCurrentUrl) {
          hasSavedCurrentUrl = true
          saveUrl(url)
        }
      }
    })

    try {
      val mediaSource: MediaSource = if (isRtspUrl(url)) {
        RtspMediaSource.Factory()
          .setForceUseRtpTcp(settings.forceRtspTcp)
          .createMediaSource(MediaItem.fromUri(url))
      } else {
        mediaSourceFactory.createMediaSource(MediaItem.fromUri(url))
      }

      newPlayer.setMediaSource(mediaSource)
      newPlayer.repeatMode = settings.repeatMode
      newPlayer.prepare()
      newPlayer.playWhenReady = true
    } catch (e: Throwable) {
      handleUnplayableStream(e)
    }
  }

  private fun isRtspUrl(url: String): Boolean =
    url.trim().startsWith("rtsp://", ignoreCase = true)

  private fun releasePlayer() {
    try {
      playerView.player = null
      player?.release()
      player = null
      topBarGroupVisibility(View.VISIBLE)
    } catch (_: Throwable) {
    }
  }

  private fun isUnrecoverableStreamParsingError(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    var depth = 0
    while (current != null && depth < 10) {
      val fromRtspOrContainerParsing = current.stackTrace.any {
        it.className.startsWith("androidx.media3.exoplayer.rtsp") ||
          it.className.startsWith("androidx.media3.container")
      }
      if (fromRtspOrContainerParsing) return true
      current = current.cause
      depth++
    }
    return false
  }

  private fun handleUnplayableStream(throwable: Throwable) {
    try {
      releasePlayer()
    } catch (_: Throwable) {
    }
    prefs.edit().remove(KEY_LAST_URL).apply()
    currentUrl = ""
    topBarGroupVisibility(View.VISIBLE)

    val rootCause = getRootCause(throwable)
    val exceptionName = rootCause.javaClass.name
    val detailedMessage = rootCause.localizedMessage ?: rootCause.message ?: "No detailed message provided."

    val infoMessage = buildString {
      if (throwable is PlaybackException) {
        append("ExoPlayer Error: ${throwable.errorCodeName} (Code ${throwable.errorCode})\n\n")
      }
      append("Root Exception:\n$exceptionName\n\n")
      append("Details:\n$detailedMessage\n\n")
      append("Full Stack Trace:\n${throwable.stackTraceToString()}")
    }

    val intent = Intent(this, ErrorActivity::class.java).apply {
      putExtra(ErrorActivity.EXTRA_TITLE, "Stream Playback Error")
      putExtra(ErrorActivity.EXTRA_MESSAGE, infoMessage)
      putExtra(ErrorActivity.EXTRA_IS_FATAL, false)
    }

    startActivity(intent)
  }

  private fun getRootCause(throwable: Throwable): Throwable {
    var cause = throwable
    while (cause.cause != null && cause.cause != cause) {
      cause = cause.cause!!
    }
    return cause
  }

  // ---------- Persistence: URLs ----------

  private fun saveUrl(url: String) {
    prefs.edit().putString(KEY_LAST_URL, url).apply()

    val existing = LinkedHashSet(prefs.getStringSet(KEY_SAVED_URLS, emptySet()) ?: emptySet())
    existing.remove(url)
    existing.add(url)
    val trimmed = if (existing.size > MAX_SAVED_URLS) {
      existing.toList().takeLast(MAX_SAVED_URLS).toSet()
    } else {
      existing
    }
    prefs.edit().putStringSet(KEY_SAVED_URLS, trimmed).apply()
  }

  private fun loadSavedUrls(): List<String> =
    (prefs.getStringSet(KEY_SAVED_URLS, emptySet()) ?: emptySet()).toList()

  private fun showSavedUrlsDialog() {
    val saved = loadSavedUrls()

    if (saved.isEmpty()) {
      Toast.makeText(
        this,
        "No saved streams yet — streams are saved automatically once they play successfully",
        Toast.LENGTH_LONG
      ).show()
      return
    }

    val builder = AlertDialog.Builder(this)
      .setTitle("Saved streams")
      .setItems(saved.toTypedArray()) { _, which ->
        playUrl(saved[which])
      }
      .setNegativeButton("Close", null)

    if (saved.isNotEmpty()) {
      builder.setNeutralButton("Clear all") { _, _ ->
        AlertDialog.Builder(this)
          .setTitle("Clear all saved streams?")
          .setMessage("This can't be undone.")
          .setPositiveButton("Clear") { _, _ ->
            prefs.edit().remove(KEY_SAVED_URLS).apply()
            Toast.makeText(this, "Saved streams cleared", Toast.LENGTH_SHORT).show()
          }
          .setNegativeButton("Cancel", null)
          .show()
      }
    }

    builder.show()
  }

  // ---------- Persistence: player settings ----------

  private fun loadSettings(): PlayerSettings = PlayerSettings(
    minBufferMs = prefs.getInt(KEY_MIN_BUFFER, DEFAULT_MIN_BUFFER),
    maxBufferMs = prefs.getInt(KEY_MAX_BUFFER, DEFAULT_MAX_BUFFER),
    bufferForPlaybackMs = prefs.getInt(KEY_BUFFER_PLAYBACK, DEFAULT_BUFFER_PLAYBACK),
    bufferForPlaybackAfterRebufferMs = prefs.getInt(
      KEY_BUFFER_PLAYBACK_REBUFFER,
      DEFAULT_BUFFER_PLAYBACK_REBUFFER
    ),
    liveTargetOffsetMs = prefs.getLong(KEY_LIVE_OFFSET, DEFAULT_LIVE_OFFSET),
    forceRtspTcp = prefs.getBoolean(KEY_FORCE_TCP, DEFAULT_FORCE_TCP),
    repeatMode = prefs.getInt(KEY_REPEAT_MODE, DEFAULT_REPEAT_MODE),
    resizeMode = prefs.getInt(KEY_RESIZE_MODE, DEFAULT_RESIZE_MODE)
  )

  private fun saveSettings(settings: PlayerSettings) {
    prefs.edit()
      .putInt(KEY_MIN_BUFFER, settings.minBufferMs)
      .putInt(KEY_MAX_BUFFER, settings.maxBufferMs)
      .putInt(KEY_BUFFER_PLAYBACK, settings.bufferForPlaybackMs)
      .putInt(KEY_BUFFER_PLAYBACK_REBUFFER, settings.bufferForPlaybackAfterRebufferMs)
      .putLong(KEY_LIVE_OFFSET, settings.liveTargetOffsetMs)
      .putBoolean(KEY_FORCE_TCP, settings.forceRtspTcp)
      .putInt(KEY_REPEAT_MODE, settings.repeatMode)
      .putInt(KEY_RESIZE_MODE, settings.resizeMode)
      .apply()
  }

  // ---------- Settings dialog ----------
  private val repeatModeLabels = listOf("Off", "Repeat one", "Repeat all")
  private val repeatModeValues = listOf(
    Player.REPEAT_MODE_OFF,
    Player.REPEAT_MODE_ONE,
    Player.REPEAT_MODE_ALL
  )

  private val resizeModeLabels = listOf("Fit", "Zoom", "Fill", "Fixed width", "Fixed height")
  private val resizeModeValues = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
  )

  private fun showSettingsDialog() {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)

    val minBufferInput = view.findViewById<EditText>(R.id.minBufferInput)
    val maxBufferInput = view.findViewById<EditText>(R.id.maxBufferInput)
    val bufferForPlaybackInput = view.findViewById<EditText>(R.id.bufferForPlaybackInput)
    val bufferForPlaybackAfterRebufferInput =
      view.findViewById<EditText>(R.id.bufferForPlaybackAfterRebufferInput)
    val liveOffsetInput = view.findViewById<EditText>(R.id.liveOffsetInput)
    val forceTcpCheckbox = view.findViewById<CheckBox>(R.id.forceTcpCheckbox)
    val repeatModeSpinner = view.findViewById<Spinner>(R.id.repeatModeSpinner)
    val resizeModeSpinner = view.findViewById<Spinner>(R.id.resizeModeSpinner)

    repeatModeSpinner.adapter =
      ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repeatModeLabels)
    resizeModeSpinner.adapter =
      ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resizeModeLabels)

    fun populate(settings: PlayerSettings) {
      minBufferInput.setText(settings.minBufferMs.toString())
      maxBufferInput.setText(settings.maxBufferMs.toString())
      bufferForPlaybackInput.setText(settings.bufferForPlaybackMs.toString())
      bufferForPlaybackAfterRebufferInput.setText(
        settings.bufferForPlaybackAfterRebufferMs.toString()
      )
      liveOffsetInput.setText(settings.liveTargetOffsetMs.toString())
      forceTcpCheckbox.isChecked = settings.forceRtspTcp
      repeatModeSpinner.setSelection(
        repeatModeValues.indexOf(settings.repeatMode).coerceAtLeast(0)
      )
      resizeModeSpinner.setSelection(
        resizeModeValues.indexOf(settings.resizeMode).coerceAtLeast(0)
      )
    }

    populate(loadSettings())

    val dialog = AlertDialog.Builder(this)
      .setTitle("Player settings")
      .setView(view)
      .setPositiveButton("Save", null)
      .setNegativeButton("Cancel", null)
      .setNeutralButton("Reset defaults", null)
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val newSettings = PlayerSettings(
          minBufferMs = minBufferInput.text.toString().toIntOrNull()
            ?: DEFAULT_MIN_BUFFER,
          maxBufferMs = maxBufferInput.text.toString().toIntOrNull()
            ?: DEFAULT_MAX_BUFFER,
          bufferForPlaybackMs = bufferForPlaybackInput.text.toString().toIntOrNull()
            ?: DEFAULT_BUFFER_PLAYBACK,
          bufferForPlaybackAfterRebufferMs =
            bufferForPlaybackAfterRebufferInput.text.toString().toIntOrNull()
              ?: DEFAULT_BUFFER_PLAYBACK_REBUFFER,
          liveTargetOffsetMs = liveOffsetInput.text.toString().toLongOrNull()
            ?: DEFAULT_LIVE_OFFSET,
          forceRtspTcp = forceTcpCheckbox.isChecked,
          repeatMode = repeatModeValues[repeatModeSpinner.selectedItemPosition],
          resizeMode = resizeModeValues[resizeModeSpinner.selectedItemPosition]
        )

        if (newSettings.minBufferMs > newSettings.maxBufferMs) {
          Toast.makeText(
            this,
            "Min buffer can't exceed max buffer",
            Toast.LENGTH_SHORT
          ).show()
          return@setOnClickListener
        }

        saveSettings(newSettings)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()

        if (currentUrl.isNotEmpty()) {
          playUrl(currentUrl)
        }
        dialog.dismiss()
      }

      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
        populate(
          PlayerSettings(
            minBufferMs = DEFAULT_MIN_BUFFER,
            maxBufferMs = DEFAULT_MAX_BUFFER,
            bufferForPlaybackMs = DEFAULT_BUFFER_PLAYBACK,
            bufferForPlaybackAfterRebufferMs = DEFAULT_BUFFER_PLAYBACK_REBUFFER,
            liveTargetOffsetMs = DEFAULT_LIVE_OFFSET,
            forceRtspTcp = DEFAULT_FORCE_TCP,
            repeatMode = DEFAULT_REPEAT_MODE,
            resizeMode = DEFAULT_RESIZE_MODE
          )
        )
      }
    }

    dialog.show()
  }

  // ---------- Crash handling ----------
  private fun showCrashOnScreen(throwable: Throwable) {
    releasePlayer()

    val message = buildString {
      append("Exception:\n${throwable.javaClass.name}\n\n")
      append("Message:\n${throwable.message ?: "No message"}\n\n")
      append("Stack trace:\n${throwable.stackTraceToString()}")
    }

    val intent = Intent(this, ErrorActivity::class.java).apply {
      putExtra(ErrorActivity.EXTRA_TITLE, "App Encountered a Critical Error")
      putExtra(ErrorActivity.EXTRA_MESSAGE, message)
      putExtra(ErrorActivity.EXTRA_IS_FATAL, true)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    startActivity(intent)
    finish()
  }

  override fun onStop() {
    super.onStop()
    releasePlayer()
  }
}
