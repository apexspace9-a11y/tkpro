package vn.lightbill.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences("lightbill", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        webView = WebView(this)
        setContentView(FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        configureWebView()
        val url = prefs.getString("server_url", null)
        if (url.isNullOrBlank()) showServerDialog(false) else loadServer(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            userAgentString = "$userAgentString LightBillAndroid/2.0"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                val server = prefs.getString("server_url", "") ?: ""
                val host = runCatching { Uri.parse(server).host }.getOrNull()
                if (uri.scheme == "https" && uri.host == host) return false
                if (uri.scheme == "zalopay" || uri.scheme == "zalo" || uri.scheme == "https") {
                    return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
                    catch (_: ActivityNotFoundException) { Toast.makeText(this@MainActivity, "Không mở được liên kết", Toast.LENGTH_SHORT).show(); true }
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.visibility = View.VISIBLE
            }
        }
        webView.setOnLongClickListener { false }
    }

    private fun loadServer(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("https://")) {
            Toast.makeText(this, "LightBill yêu cầu HTTPS", Toast.LENGTH_LONG).show()
            showServerDialog(false)
            return
        }
        prefs.edit().putString("server_url", normalized).apply()
        webView.loadUrl(normalized + "/")
    }

    private fun showServerDialog(cancelable: Boolean) {
        val input = EditText(this).apply {
            hint = "https://billing.tenmien.vn"
            setText(prefs.getString("server_url", ""))
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Máy chủ LightBill")
            .setMessage("Nhập địa chỉ HTTPS của web/API LightBill")
            .setView(input)
            .setCancelable(cancelable)
            .setPositiveButton("Kết nối") { _, _ -> loadServer(input.text.toString()) }
            .setNegativeButton(if (cancelable) "Hủy" else "Thoát") { _, _ -> if (!cancelable) finish() }
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
