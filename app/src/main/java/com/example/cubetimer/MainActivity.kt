package com.example.cubetimer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/** Cau rung native cho WebView: navigator.vibrate khong an tren mot so may
 *  (WebView cua OEM chan), nen JS goi window.AndroidVibrate.vibrate("[200]")
 *  de rung truc tiep qua Vibrator cua he thong. Pattern JS truyen vao la
 *  kieu navigator.vibrate ([200] = rung 200ms, [200,100,200] = rung-nghi-rung);
 *  o day them so 0 o dau vi VibrationEffect tinh phan tu dau la thoi gian doi. */
class VibrateBridge(private val context: Context) {
    @JavascriptInterface
    fun vibrate(patternJson: String) {
        try {
            val parts = patternJson.trim().trim('[', ']')
                .split(",").mapNotNull { it.trim().toLongOrNull() }
            if (parts.isEmpty()) return
            val timings = longArrayOf(0L) + parts.toLongArray()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } catch (e: Exception) {
            // Bo qua: may khong co motor rung hoac pattern loi.
        }
    }
}

/** App bam gio Rubik: WebView chay file index.html trong assets.
 *  Moi logic nam trong index.html o goc repo; CI copy no vao assets truoc khi build. */
class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Khong tat man hinh khi dang bam gio.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // localStorage luu lich su luot giai
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            addJavascriptInterface(VibrateBridge(this@MainActivity), "AndroidVibrate")
            webViewClient = WebViewClient()
            // WebView khong tu hien hop thoai JS (alert/confirm) nen phai tu ve
            // bang AlertDialog, khong thi nut Xoa toan bo trong app se khong chay.
            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ -> result.confirm() }
                        .setCancelable(false)
                        .show()
                    return true
                }

                override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(message)
                        .setPositiveButton("Đồng ý") { _, _ -> result.confirm() }
                        .setNegativeButton("Hủy") { _, _ -> result.cancel() }
                        .setOnCancelListener { result.cancel() }
                        .show()
                    return true
                }
            }
        }
        setContentView(webView)
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // Nut Back: dang o trang Lich su thi ve Timer, khong thi thoat app.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        webView.evaluateJavascript("currentView") { value ->
            if (value?.trim('"') == "history") {
                webView.evaluateJavascript("showTimer()", null)
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
