package helium314.keyboard.settings.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val apkSize: Long
)

object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/lorvance/LorKeys/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkForUpdate(
        context: Context,
        isManual: Boolean = false,
        onResult: (UpdateInfo?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(GITHUB_API_URL)
                    .header("User-Agent", "LorKeys-App")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            if (isManual) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.update_error, "HTTP ${response.code}"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onResult(null)
                        }
                        return@launch
                    }

                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name", "")
                    val versionName = json.optString("name", tagName.removePrefix("v").removePrefix("V"))
                    val changelog = json.optString("body", "")

                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    var apkSize = 0L

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            val url = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk") || url.endsWith(".apk")) {
                                downloadUrl = url
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    val remoteClean = versionName.removePrefix("v").removePrefix("V").trim()
                    val currentClean = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()

                    val isNewer = isVersionNewer(remoteClean, currentClean)

                    withContext(Dispatchers.Main) {
                        if (isNewer && downloadUrl.isNotEmpty()) {
                            onResult(
                                UpdateInfo(
                                    tagName = tagName,
                                    versionName = remoteClean,
                                    changelog = changelog,
                                    downloadUrl = downloadUrl,
                                    apkSize = apkSize
                                )
                            )
                        } else {
                            if (isManual) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.update_no_updates, BuildConfig.VERSION_NAME),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onResult(null)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isManual) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_error, e.localizedMessage ?: "Unknown error"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onResult(null)
                }
            }
        }
    }

    fun isVersionNewer(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
            val currentParts = current.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {
        }
        return false
    }

    fun downloadApk(
        context: Context,
        url: String,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        var isCancelled = false
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LorKeys-App")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Download failed HTTP ${response.code}")
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val total = body.contentLength()
                    val updatesDir = File(context.cacheDir, "updates")
                    if (!updatesDir.exists()) updatesDir.mkdirs()
                    val apkFile = File(updatesDir, "LorKeys_update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    val inputStream: InputStream = body.byteStream()
                    val outputStream = FileOutputStream(apkFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead: Long = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            outputStream.close()
                            inputStream.close()
                            apkFile.delete()
                            return@launch
                        }
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (total > 0) totalRead.toFloat() / total.toFloat() else 0f
                        withContext(Dispatchers.Main) {
                            onProgress(progress, totalRead, total)
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        onComplete(apkFile)
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        }

        return {
            isCancelled = true
            job.cancel()
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
