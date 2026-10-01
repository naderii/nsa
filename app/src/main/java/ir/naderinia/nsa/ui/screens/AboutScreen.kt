package ir.naderinia.nsa.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    onShowChangelog: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "درباره نسا",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "بازگشت"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {

                // ─────────────────────────────
                // Hero Header
                // ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(top = 12.dp, bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(108.dp),
                            shape = RoundedCornerShape(34.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "نسا",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Nader's Smart Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "دستیار هوشمند شخصی برای مدیریت یادآورها و امور روزمره",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {

                    // ─────────────────────────────
                    // Version Card
                    // ─────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "نسخه برنامه",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = versionName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Build $versionCode",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Changelog Button
                    FilledTonalButton(
                        onClick = onShowChangelog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "مشاهده تازه‌های اپ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // ─────────────────────────────
                    // Developer Section
                    // ─────────────────────────────
                    Text(
                        text = "درباره سازنده",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            // Developer Info
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                Column {
                                    Text(
                                        text = "نادر نادری",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Network & Infrastructure Specialist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 22.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Links
                            ModernLinkItem(
                                icon = Icons.Default.Language,
                                title = "وب‌سایت شخصی",
                                onClick = { openUrl("https://nader.naderinia.ir/fa/") }
                            )
                            ModernLinkItem(
                                icon = Icons.Default.Code,
                                title = "صفحه پروژه در GitHub",
                                onClick = { openUrl("https://github.com/naderii/nsa") }
                            )
                            ModernLinkItem(
                                icon = Icons.Default.Link,
                                title = "تلگرام  @sananaderi",
                                onClick = { openUrl("https://t.me/sananaderi") }
                            )
                            ModernLinkItem(
                                icon = Icons.Default.Link,
                                title = "LinkedIn",
                                onClick = { openUrl("https://www.linkedin.com/in/nader-naderi-13247417") },
                                showDivider = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ─────────────────────────────
                    // About Text
                    // ─────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = "نسا یک پروژه مستقل با هدف ارائه ابزاری ساده، کاربردی و قابل اعتماد برای مدیریت یادآورها و امور روزمره است.",
                            modifier = Modifier.padding(22.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Justify
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Footer
                    Text(
                        text = "© 2026 Nader Naderi",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ModernLinkItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 78.dp, end = 22.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}