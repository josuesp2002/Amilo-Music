package com.example.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

@Composable
fun AutoUpdater() {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/josuesp2002/amilo-music-update/refs/heads/main/update.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val vCode = json.getInt("version_code")
                    val vName = json.getString("version_name")
                    val dUrl = json.getString("download_url")
                    val changelog = json.optString("changelog", "")
                    
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toInt()
                    } else {
                        packageInfo.versionCode
                    }
                    
                    if (vCode > currentVersionCode) {
                        updateInfo = UpdateInfo(vCode, vName, dUrl, changelog)
                        showDialog = true
                    }
                }
            } catch (e: Exception) {
                // Ignore silent update errors
            }
        }
    }

    if (showDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nueva versión disponible") },
            text = { 
                Text("La versión ${updateInfo!!.versionName} ya está lista.\n\n${updateInfo!!.changelog}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadAndInstall(context, updateInfo!!)
                        showDialog = false
                    }
                ) {
                    Text("Actualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Más tarde")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) {
    try {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AmiloMusic_Update.apk")
        if (file.exists()) {
            file.delete()
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateInfo.downloadUrl)
        val request = DownloadManager.Request(uri)
            .setTitle("Descargando actualización")
            .setDescription("Amilo Music ${updateInfo.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AmiloMusic_Update.apk")
            .setMimeType("application/vnd.android.package-archive")
        
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        val fileUri = downloadManager.getUriForDownloadedFile(downloadId)
                        if (fileUri != null) {
                            val installIntent = Intent(Intent.ACTION_VIEW)
                            installIntent.setDataAndType(fileUri, "application/vnd.android.package-archive")
                            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            ctx.startActivity(installIntent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (e: Exception) {}
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
