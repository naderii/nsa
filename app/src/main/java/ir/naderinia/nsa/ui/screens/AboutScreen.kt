package ir.naderinia.nsa.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.R
import ir.naderinia.nsa.ui.theme.NsaShapes

@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    onShowChangelog: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AboutTopBar(
                    onBack = onBack
                )
            }
        ) { paddingValues ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // =====================================================
                // Hero
                // =====================================================

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = NsaShapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(
                                id = R.drawable.ic_launcher_foreground
                            ),
                            contentDescription = "لوگوی نسا",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                Text(
                    text = "نسا",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = "Nader's Smart Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "نسخه $versionName  •  Build $versionCode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 7.dp
                        )
                    )
                }

                Spacer(
                    modifier = Modifier.height(26.dp)
                )

                // =====================================================
                // Intro / Hero description
                // =====================================================

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NsaShapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "یک دستیار ساده برای زندگی روزمره",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Right
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "نسا برای مدیریت یادآورها، امور مالی و کارهای روزمره طراحی شده است؛ با تمرکز بر سادگی، حریم خصوصی و تجربه کاربری مناسب.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Right
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(26.dp)
                )

                // =====================================================
                // Features title
                // =====================================================

                SectionTitle(
                    title = "امکانات نسا"
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                // =====================================================
                // Features grid - Row 1
                // =====================================================

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    FeatureItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Notifications,
                        title = "یادآوری‌ها",
                        description = "مدیریت کارها و رویدادهای روزمره",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    FeatureItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Event,
                        title = "تقویم و دسته‌بندی",
                        description = "دسته‌بندی ساده و دسترسی سریع",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                // =====================================================
                // Features grid - Row 2
                // =====================================================

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    FeatureItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Backup,
                        title = "پشتیبان‌گیری",
                        description = "پشتیبان دستی و خودکار اطلاعات",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    FeatureItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Lock,
                        title = "حریم خصوصی",
                        description = "تمرکز بر امنیت و حفاظت از اطلاعات",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(
                    modifier = Modifier.height(28.dp)
                )

                // =====================================================
                // Developer
                // =====================================================

                SectionTitle(
                    title = "سازنده"
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NsaShapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Surface(
                                modifier = Modifier.size(50.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ن",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(
                                modifier = Modifier.width(12.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "نادر نادری",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(
                                    modifier = Modifier.height(2.dp)
                                )

                                Text(
                                    text = "Network & Infrastructure Specialist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        // Website
                        LinkRow(
                            icon = Icons.Default.Language,
                            title = "وب‌سایت شخصی",
                            subtitle = "nader.naderinia.ir",
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                openUrl(
                                    "https://nader.naderinia.ir/"
                                )
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        // GitHub
                        LinkRow(
                            icon = Icons.Default.Code,
                            title = "GitHub",
                            subtitle = "github.com/naderii/nsa",
                            iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                openUrl(
                                    "https://github.com/naderii/nsa"
                                )
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        // Telegram
                        LinkRow(
                            icon = Icons.AutoMirrored.Filled.Send,
                            title = "Telegram",
                            subtitle = "@sananaderi",
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                openUrl(
                                    "https://t.me/sananaderi"
                                )
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        // LinkedIn
                        LinkRow(
                            icon = Icons.Default.Work,
                            title = "LinkedIn",
                            subtitle = "Nader Naderi",
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = {
                                openUrl(
                                    "https://www.linkedin.com/in/nader-naderi-13247417b"
                                )
                            }
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(26.dp)
                )

                // =====================================================
                // Project information
                // =====================================================

                SectionTitle(
                    title = "درباره پروژه"
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = "نسا یک پروژه مستقل است که با Kotlin و Jetpack Compose توسعه داده شده و هدف آن ارائه ابزاری ساده، کاربردی و قابل اعتماد برای مدیریت بخشی از امور روزمره است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(
                    modifier = Modifier.height(24.dp)
                )

                // =====================================================
                // Changelog
                // =====================================================

                Button(
                    onClick = onShowChangelog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = NsaShapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = "مشاهده تغییرات نسخه"
                    )
                }

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                // =====================================================
                // Copyright
                // =====================================================

                Text(
                    text = "© 2026 Nader Naderi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )
            }
        }
    }
}


// ====================================================================
// Top App Bar
// ====================================================================

@Composable
private fun AboutTopBar(
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "بازگشت"
                )
            }

            Spacer(
                modifier = Modifier.width(4.dp)
            )

            Text(
                text = "درباره‌ی اپ",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}


// ====================================================================
// Section title
// ====================================================================

@Composable
private fun SectionTitle(
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(NsaShapes.extraSmall)
                .background(
                    MaterialTheme.colorScheme.primary
                )
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}


// ====================================================================
// Feature item
// ====================================================================

@Composable
private fun FeatureItem(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        shape = NsaShapes.medium,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Surface(
                modifier = Modifier.size(38.dp),
                shape = NsaShapes.small,
                color = contentColor.copy(alpha = 0.12f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = contentColor
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.82f)
            )
        }
    }
}


// ====================================================================
// Link row
// ====================================================================

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconContainerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NsaShapes.small)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 4.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            modifier = Modifier.size(42.dp),
            shape = NsaShapes.small,
            color = iconContainerColor
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = iconColor
                )
            }
        }

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}