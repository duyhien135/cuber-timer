package com.example.cubetimer

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

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

/** Cau luu file sao luu cho WebView: WebView khong tu tai duoc file (blob URL
 *  khong qua duoc DownloadManager) nen JS goi window.AndroidBackup.saveFile
 *  de ghi thang vao thu muc Download. Tra ve "ok" hoac "error: ...". */
class BackupBridge(private val activity: Activity) {
    @JavascriptInterface
    fun saveFile(fileName: String, content: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "error: no uri"
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeText(content, Charsets.UTF_8)
            }
            "ok"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }
}

/** App bam gio Rubik: WebView chay file index.html trong assets.
 *  Moi logic nam trong index.html o goc repo; CI copy no vao assets truoc khi build. */
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val FILE_PICKER_REQUEST = 1001
    }

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
            addJavascriptInterface(BackupBridge(this@MainActivity), "AndroidBackup")
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

                // <input type=file> trong WebView khong tu mo duoc hop chon file:
                // mo trinh chon file cua he thong de nhap file sao luu JSON.
                override fun onShowFileChooser(
                    view: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/json"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(
                        Intent.createChooser(intent, "Chọn file sao lưu"),
                        FILE_PICKER_REQUEST
                    )
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

    // Ket qua chon file sao luu: tra URI ve cho <input type=file> trong WebView.
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST) {
            val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
            filePathCallback?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray())
            filePathCallback = null
        }
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
