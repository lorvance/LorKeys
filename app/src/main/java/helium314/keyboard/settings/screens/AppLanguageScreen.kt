// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.AppLocaleUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination

@Composable
fun AppLanguageScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val currentTag = AppLocaleUtils.getSavedLanguageTag(context)
    val systemDefaultName = stringResource(R.string.app_language_system_default)

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.app_language_title),
        settings = emptyList(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    AppLocaleUtils.SUPPORTED_APP_LANGUAGES.forEachIndexed { index, item ->
                        val isSelected = (item.tag == currentTag)
                        val title = if (item.tag.isEmpty()) systemDefaultName else item.nativeName
                        val subtitle = if (item.tag.isEmpty()) {
                            stringResource(R.string.app_language_desc)
                        } else if (item.englishName != item.nativeName) {
                            item.englishName
                        } else {
                            null
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (item.tag != currentTag) {
                                        AppLocaleUtils.setAppLanguage(context, item.tag)
                                        val activity = context.getActivity() as? SettingsActivity
                                        if (activity != null) {
                                            val intent = Intent(activity, SettingsActivity::class.java).apply {
                                                putExtra(SettingsActivity.EXTRA_NAV_TARGET, SettingsDestination.AppLanguage)
                                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            }
                                            activity.finish()
                                            activity.startActivity(intent)
                                            @Suppress("DEPRECATION")
                                            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        }

                        if (index < AppLocaleUtils.SUPPORTED_APP_LANGUAGES.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
