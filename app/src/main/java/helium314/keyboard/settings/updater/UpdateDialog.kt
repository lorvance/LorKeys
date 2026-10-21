package helium314.keyboard.settings.updater

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import java.io.File
import kotlin.math.sin

@Composable
fun ExpressiveWavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavyPhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokeWidthPx = 4.dp.toPx()
        val cornerRadius = CornerRadius(height / 2f, height / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - strokeWidthPx / 2f),
            size = Size(width, strokeWidthPx),
            cornerRadius = cornerRadius
        )

        val clampedProgress = progress.coerceIn(0f, 1f)
        val activeWidth = if (clampedProgress > 0.01f) width * clampedProgress else width * 0.15f
        val waveAmplitude = 4.dp.toPx()
        val waveLength = 26.dp.toPx()

        val path = Path()
        val step = 2f
        var first = true

        var x = 0f
        while (x <= activeWidth) {
            val normalizedX = x / waveLength
            val y = centerY + sin(normalizedX * 2.0 * Math.PI - phase).toFloat() * waveAmplitude
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += step
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(updateInfo.apkSize) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var cancelDownload by remember { mutableStateOf<(() -> Unit)?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cancelDownload?.invoke()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismissRequest()
        },
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.update_available_title, updateInfo.versionName),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.update_downloading),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ExpressiveWavyProgressBar(
                        progress = downloadProgress
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val downloadedMb = downloadedBytes.toFloat() / (1024f * 1024f)
                        val totalMb = totalBytes.toFloat() / (1024f * 1024f)
                        Text(
                            text = if (totalBytes > 0) "%.1f / %.1f MB".format(downloadedMb, totalMb) else "%.1f MB".format(downloadedMb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = "Что нового:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = updateInfo.changelog.ifBlank { "Обновление LorKeys ${updateInfo.versionName}" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isDownloading) {
                OutlinedButton(
                    onClick = {
                        cancelDownload?.invoke()
                        isDownloading = false
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (downloadedApk != null) {
                Button(
                    onClick = {
                        UpdateManager.installApk(context, downloadedApk!!)
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Установить")
                }
            } else {
                Button(
                    onClick = {
                        isDownloading = true
                        downloadProgress = 0f
                        downloadedBytes = 0L
                        cancelDownload = UpdateManager.downloadApk(
                            context = context,
                            url = updateInfo.downloadUrl,
                            onProgress = { progress, downloaded, total ->
                                downloadProgress = progress
                                downloadedBytes = downloaded
                                totalBytes = total
                            },
                            onComplete = { apkFile ->
                                isDownloading = false
                                downloadedApk = apkFile
                                UpdateManager.installApk(context, apkFile)
                                onDismissRequest()
                            },
                            onError = {
                                isDownloading = false
                            }
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.update_action_download))
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.update_action_later))
                }
            }
        }
    )
}
