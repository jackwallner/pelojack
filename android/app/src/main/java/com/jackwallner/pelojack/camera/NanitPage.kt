package com.jackwallner.pelojack.camera

import java.net.URI

/** Navigation and video presentation for Nanit's official viewer, without an account bridge. */
object NanitPage {
    const val URL = "https://my.nanit.com/"

    fun accepts(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return false
        uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            uri.port in listOf(-1, 443) && (host == "nanit.com" || host.endsWith(".nanit.com"))
    }.getOrDefault(false)

    // Keep the selected live video mounted so its WebRTC connection survives the transition.
    // No account state, credentials or stream URLs leave the page.
    val floatVideo = """
        (() => {
          const videos = [...document.querySelectorAll('video')].filter(v => {
            const r = v.getBoundingClientRect();
            return v.videoWidth > 0 && !v.paused && v.readyState >= 2 && r.width > 0 && r.height > 0;
          });
          videos.sort((a, b) => {
            const x = a.getBoundingClientRect(), y = b.getBoundingClientRect();
            return y.width * y.height - x.width * x.height;
          });
          const video = videos[0];
          if (!video) return false;
          if (document.getElementById('pelojack-camera-style')) return true;
          const ancestors = [];
          for (let p = video.parentElement; p; p = p.parentElement) {
            ancestors.push([p, p.getAttribute('style')]);
            p.style.setProperty('transform', 'none', 'important');
            p.style.setProperty('overflow', 'visible', 'important');
            p.style.setProperty('contain', 'none', 'important');
          }
          window.pelojackCameraPresentation = {video, ancestors};
          video.setAttribute('data-pelojack-camera', '');
          const style = document.createElement('style');
          style.id = 'pelojack-camera-style';
          style.textContent = `
            html, body { margin: 0 !important; background: black !important; overflow: hidden !important; }
            body * { visibility: hidden !important; }
            video[data-pelojack-camera] {
              visibility: visible !important; position: fixed !important; inset: 0 !important;
              width: 100vw !important; height: 100vh !important; max-width: none !important;
              max-height: none !important; object-fit: contain !important;
              z-index: 2147483647 !important; background: black !important;
            }`;
          document.head.appendChild(style);
          return true;
        })()
    """.trimIndent()

    val restoreVideo = """
        (() => {
          document.getElementById('pelojack-camera-style')?.remove();
          const saved = window.pelojackCameraPresentation;
          if (!saved) return;
          saved.video.removeAttribute('data-pelojack-camera');
          saved.ancestors.forEach(([element, style]) => {
            if (style === null) element.removeAttribute('style');
            else element.setAttribute('style', style);
          });
          delete window.pelojackCameraPresentation;
        })()
    """.trimIndent()
}
