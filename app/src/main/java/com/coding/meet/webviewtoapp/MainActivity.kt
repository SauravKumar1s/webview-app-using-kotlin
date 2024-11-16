package com.coding.meet.webviewtoapp

import android.app.Dialog
import android.app.DownloadManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.coding.meet.webviewtoapp.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {

    private var webUrl = "https://web-view-sable.vercel.app/"
    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf()
    } else {
        arrayListOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    private var isLoaded = false
    private var doubleBackToExitPressedOnce = false

    private val networkConnectivityObserver: NetworkConnectivityObserver by lazy {
        NetworkConnectivityObserver(this)
    }

    private val loadingDialog: Dialog by lazy {
        Dialog(this)
    }

    private val mainBinding: ActivityMainBinding by lazy {
        DataBindingUtil.setContentView(this, R.layout.activity_main)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Loading dialog setup
        loadingDialog.setContentView(R.layout.loading_layout)
        loadingDialog.window!!.setLayout(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        val setting = mainBinding.webView.settings
        setting.javaScriptEnabled = true
        setting.allowFileAccess = true
        setting.domStorageEnabled = true
        setting.javaScriptCanOpenWindowsAutomatically = true
        setting.setSupportMultipleWindows(true)

        // Snackbar for no internet connection
        val snackbar = Snackbar.make(
            mainBinding.root,
            "No Internet Connection",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Wifi") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        // Network connectivity observer
        networkConnectivityObserver.observe(this) {
            when (it) {
                Status.Available -> {
                    if (snackbar.isShown) snackbar.dismiss()
                    mainBinding.swipeRefresh.isEnabled = true
                    if (!isLoaded) loadWebView()
                }
                else -> {
                    showNoInternet()
                    snackbar.show()
                    mainBinding.swipeRefresh.isRefreshing = false
                }
            }
        }

        // Swipe to refresh listener
        mainBinding.swipeRefresh.setOnRefreshListener {
            if (!isLoaded) {
                loadWebView()
            } else {
                setProgressDialogVisibility(false)
            }
        }

        // Bottom navigation setup
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    mainBinding.webView.loadUrl("https://web-view-sable.vercel.app/")
                    true
                }
                R.id.favorites -> {
                    mainBinding.webView.loadUrl("https://web-view-sable.vercel.app/favorites.html")
                    true
                }

                R.id.search -> {
                    mainBinding.webView.loadUrl("https://web-view-sable.vercel.app/search.html")
                    true
                }

                R.id.donate -> {
                    mainBinding.webView.loadUrl("https://web-view-sable.vercel.app/payment.html")
                    true
                }
                R.id.feedback -> {
                    mainBinding.webView.loadUrl("https://web-view-sable.vercel.app/feedback.html")
                    true
                }
                else -> false
            }
        }

        if (!isLoaded) {
            bottomNavigationView.selectedItemId = R.id.home
        }
    }

    private fun setProgressDialogVisibility(visible: Boolean) {
        if (visible) {
            loadingDialog.show()
        } else {
            loadingDialog.dismiss()
            mainBinding.swipeRefresh.isRefreshing = false
        }
    }

    private fun showNoInternet() {
        isLoaded = false
        setProgressDialogVisibility(false)
        mainBinding.webView.visibility = View.GONE
        mainBinding.noInternet.noInternetRL.visibility = View.VISIBLE
    }

    private fun loadWebView() {
        mainBinding.noInternet.noInternetRL.visibility = View.GONE
        mainBinding.webView.visibility = View.VISIBLE
        mainBinding.webView.loadUrl(webUrl)
        mainBinding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            Log.d("Url", url.trim())
            if (checkMultiplePermission()) {
                download(url.trim(), userAgent, contentDisposition, mimeType, contentLength)
            }
        }
        mainBinding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                setProgressDialogVisibility(true)
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoaded = true
                webUrl = url!!
                setProgressDialogVisibility(false)
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                isLoaded = false
                setProgressDialogVisibility(false)
                super.onReceivedError(view, request, error)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mainBinding.webView.canGoBack()) {
                    mainBinding.webView.goBack()
                } else {
                    showToastExit()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showToastExit() {
        if (doubleBackToExitPressedOnce) {
            finish()
        } else {
            doubleBackToExitPressedOnce = true
            Toast.makeText(this, "Please click back again to exit", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed(
                { doubleBackToExitPressedOnce = false }, 2000
            )
        }
    }

    private fun download(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        val folder = File(Environment.getExternalStorageDirectory().toString() + "/Download/Image")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()

        val request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType(mimeType)
        val cookie = CookieManager.getInstance().getCookie(url)
        request.addRequestHeader("cookie", cookie)
        request.addRequestHeader("User-Agent", userAgent)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        request.setTitle(fileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Image/$fileName")

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }

    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionNeeded.toTypedArray(), multiplePermissionId)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == multiplePermissionId) {
            var isGrant = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (isGrant) {
                Toast.makeText(this, "All permissions granted successfully", Toast.LENGTH_LONG).show()
            } else {
                if (permissions.any { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                    appSettingOpen(this)
                } else {
                    warningPermissionDialog(this) { _, which ->
                        if (which == DialogInterface.BUTTON_POSITIVE) checkMultiplePermission()
                    }
                }
            }
        }
    }
}
