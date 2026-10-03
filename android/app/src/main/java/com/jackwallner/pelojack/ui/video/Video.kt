package com.jackwallner.pelojack.ui.video

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// YouTube's embedded player refuses to play without a referrer that names the app.
private const val YOUTUBE_ORIGIN = "https://com.jackwallner.pelojack"

private fun youTubePage(id: String, playing: Boolean) = """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#player{position:absolute;inset:0;width:100%;height:100%}</style>
</head><body><div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player, ready = false, wanted = $playing;
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {
    videoId: '$id',
    playerVars: { controls: 0, rel: 0, playsinline: 1, modestbranding: 1, iv_load_policy: 3, disablekb: 1, fs: 0, origin: '$YOUTUBE_ORIGIN' },
    events: {
      onReady: function () { ready = true; apply(); },
      onStateChange: function (e) { console.log('pelojack state ' + e.data); },
      onError: function (e) { console.log('pelojack error ' + e.data); }
    }
  });
}
function setPlaying(play) { wanted = play; apply(); }
function apply() { if (!ready) return; if (wanted) player.playVideo(); else player.pauseVideo(); }
</script></body></html>
""".trimIndent()

/** A YouTube video that plays and pauses with the ride. No YouTube controls are shown. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeVideo(id: String, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wanted = rememberUpdatedState(playing)
    var loaded by remember(id) { mutableStateOf(false) }
    val web = remember(id) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded = true
                }
            }
            loadDataWithBaseURL(YOUTUBE_ORIGIN, youTubePage(id, wanted.value), "text/html", "utf-8", null)
        }
    }
    // The page's script only exists once it has loaded; until then the initial state is baked in.
    LaunchedEffect(web, loaded, playing) { if (loaded) web.evaluateJavascript("setPlaying($playing)", null) }
    DisposableEffect(web) { onDispose { web.destroy() } }
    AndroidView({ web }, modifier)
}

/** A local video file or http(s) stream, paused with the ride. */
@OptIn(UnstableApi::class)
@Composable
fun FileVideo(location: String, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(location) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(location)))
            prepare()
        }
    }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(Color.BLACK)
                this.player = player
            }
        },
        modifier = modifier,
    )
}
