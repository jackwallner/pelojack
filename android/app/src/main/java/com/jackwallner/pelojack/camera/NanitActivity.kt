package com.jackwallner.pelojack.camera

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jackwallner.pelojack.BuildConfig
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.ui.PelotonWindow
import java.lang.ref.WeakReference

/** Landscape sign-in and a camera-only Android PiP window over a ride or another app. */
class NanitActivity : ComponentActivity() {
    private val app get() = application as PelojackApp
    private lateinit var web: WebView
    private lateinit var toolbar: LinearLayout
    private lateinit var content: FrameLayout
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var floating = false
    private var entering = false
    private var testFeed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(24), 0, px(24), 0)
            setBackgroundColor(Color.rgb(21, 23, 27))
        }
        toolbar.addView(button("Back to ride") { finish() })
        toolbar.addView(TextView(this).apply {
            text = "Nanit camera"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(28).toFloat())
            setPadding(px(32), 0, px(16), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(button("Reload") { if (!floating) web.reload() })
        toolbar.addView(button("Float camera") { floatCamera() })
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(96)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "").replace("Version/4.0 ", "").replace(" Mobile ", " ")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (NanitPage.accepts(request.url.toString())) return false
                    if (request.isForMainFrame) message("Use your Nanit email to sign in here")
                    return true
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) message("Camera page could not load. Check Wi-Fi, then tap Reload.")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    hideCustomView()
                    customView = view
                    customCallback = callback
                    web.visibility = View.GONE
                    content.addView(view, fullSize())
                }

                override fun onHideCustomView() = hideCustomView()
            }
        }
        content.addView(web, fullSize())
        testFeed = BuildConfig.DEBUG && intent.getBooleanExtra("camera-test", false)
        if (testFeed) {
            web.loadDataWithBaseURL(NanitPage.URL, assets.open("camera-pip-test.html").bufferedReader().use { it.readText() }, "text/html", "UTF-8", null)
        } else web.loadUrl(NanitPage.URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        app.devLink.window = WeakReference(window.decorView)
        applySystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) web.onPause()
    }

    override fun onDestroy() {
        hideCustomView()
        content.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        floating = isInPictureInPictureMode
        toolbar.visibility = if (floating) View.GONE else View.VISIBLE
        if (!floating && !isFinishing) web.evaluateJavascript(NanitPage.restoreVideo, null)
    }

    private fun floatCamera() {
        if (entering || floating) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            message("This tablet does not support picture in picture")
            return
        }
        if (!testFeed && !NanitPage.accepts(web.url.orEmpty())) return
        entering = true
        web.evaluateJavascript(NanitPage.floatVideo) { ready ->
            entering = false
            if (isFinishing || isDestroyed) return@evaluateJavascript
            if (ready != "true") {
                message("Sign in and play your camera feed first, then tap Float camera")
                return@evaluateJavascript
            }
            val bounds = Rect().also { content.getGlobalVisibleRect(it) }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9)).setSourceRectHint(bounds).build()
            toolbar.visibility = View.GONE
            val entered = runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
            if (!entered) {
                toolbar.visibility = View.VISIBLE
                web.evaluateJavascript(NanitPage.restoreVideo, null)
                message("Picture in picture is unavailable. The camera is still open here.")
            }
        }
    }

    private fun hideCustomView() {
        customView?.let(content::removeView)
        customView = null
        val callback = customCallback
        customCallback = null
        callback?.onCustomViewHidden()
        if (::web.isInitialized) web.visibility = View.VISIBLE
    }

    private fun applySystemBars() {
        val hidden = app.settings.state.value.hideSystemBars
        PelotonWindow.hideNavigationBar(window, hidden)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24).toFloat())
        setPadding(px(24), 0, px(24), 0)
        setOnClickListener { action() }
    }

    private fun px(design: Int) = (design * resources.displayMetrics.widthPixels / 1920f).toInt()
    private fun fullSize() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
