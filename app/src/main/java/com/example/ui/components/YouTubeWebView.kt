package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

class WebViewController {
    var webView: WebView? = null

    fun goBack(): Boolean {
        return if (webView?.canGoBack() == true) {
            webView?.goBack()
            true
        } else {
            false
        }
    }

    fun goForward(): Boolean {
        return if (webView?.canGoForward() == true) {
            webView?.goForward()
            true
        } else {
            false
        }
    }

    fun reload() {
        webView?.reload()
    }

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    initialUrl: String,
    controller: WebViewController,
    onUrlChanged: (String, String?) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLoadingProgress: (Int) -> Unit,
    onNavStateChanged: (Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                loadWithOverviewMode = true
                useWideViewPort = true
                allowContentAccess = true
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onLoadingProgress(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrEmpty()) {
                        onTitleChanged(title)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Keep youtube inside webview
                    if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        return false
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let { onUrlChanged(it, view?.title) }
                    onNavStateChanged(canGoBack(), canGoForward())
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let { onUrlChanged(it, view?.title) }
                    onNavStateChanged(canGoBack(), canGoForward())

                    // Inject helper script to detect SPA url changes (YouTube Mobile uses history.pushState)
                    val jsDetect = """
                        (function() {
                            if (window.__cliptube_injected) return;
                            window.__cliptube_injected = true;
                            
                            var lastUrl = location.href;
                            new MutationObserver(function() {
                                if (location.href !== lastUrl) {
                                    lastUrl = location.href;
                                    document.title = document.title;
                                }
                            }).observe(document, {subtree: true, childList: true});
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsDetect, null)
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    url?.let { onUrlChanged(it, view?.title) }
                    onNavStateChanged(canGoBack(), canGoForward())
                }
            }
        }
    }

    controller.webView = webView

    LaunchedEffect(initialUrl) {
        if (webView.url != initialUrl && initialUrl.isNotEmpty()) {
            webView.loadUrl(initialUrl)
        }
    }

    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
        onNavStateChanged(webView.canGoBack(), webView.canGoForward())
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            controller.webView = null
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxSize()
            .testTag("youtube_webview")
    )
}
