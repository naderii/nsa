package ir.naderinia.nsa.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

    // ============================================================
    // Animations — built on Animatable (stable in ALL Compose versions)
    // ============================================================

    // Background gradient breathing
    val bgShift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        bgShift.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }
    val bgBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + 0.05f * bgShift.value),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
        )
    )

    // 3D floating rotation for app icon
    val rotY = remember { Animatable(-8f) }
    LaunchedEffect(Unit) {
        rotY.animateTo(
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }
    val rotX = remember { Animatable(4f) }
    LaunchedEffect(Unit) {
        rotX.animateTo(
            targetValue = -4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Glowing shadow breathing
    val glow = remember { Animatable(8f) } // elevation in dp, as Float
    LaunchedEffect(Unit) {
        glow.animateTo(
            targetValue = 24f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Entrance animation
    val enterAlpha = remember { Animatable(0f) }
    val enterY = remember { Animatable(60f) }
    LaunchedEffect(Unit) {
        enterAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 700))
        enterY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("درباره‌ی اپ") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("بازگشت") }
                }
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(padding)
        ) {
            // Decorative blurred orbs (depth / 3D background)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .size(220.dp)
                    .blur(70.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape)
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-50).dp, y = 60.dp)
                    .size(260.dp)
                    .blur(90.dp)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer { alpha = enterAlpha.value; translationY = enterY.value }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ---- 3D App Icon ----
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            rotationX = rotX.value
                            rotationY = rotY.value
                            cameraDistance = 16f * density
                        }
                        .shadow(glow.value.dp, RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            RoundedCornerShape(32.dp)
                        )
                        .border(
                            2.dp,
                            Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(32.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text("نسا", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Nader's Smart Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "نسخه $versionName  •  Build $versionCode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ---- Glass card ----
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {

                        Text("تازه‌های اپ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "تغییرات و ویژگی‌های جدید را ببینید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onShowChangelog,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Update, null)
                            Spacer(Modifier.width(8.dp))
                            Text("مشاهده تغییرات")
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(20.dp))

                        Text("درباره سازنده", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Person,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("نادر نادری", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Network & Infrastructure Specialist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Social buttons with better icons
                        FilledTonalButton(
                            onClick = { openUrl("https://github.com/naderii/nsa") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Code, null)
                            Spacer(Modifier.width(8.dp))
                            Text("GitHub")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = { openUrl("https://t.me/sananaderi") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Telegram  @sananaderi")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = { openUrl("https://www.linkedin.com/in/nader-naderi-13247417b") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Work, null)
                            Spacer(Modifier.width(8.dp))
                            Text("LinkedIn")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    "نسا یک پروژه مستقل با هدف ارائه ابزاری ساده و کاربردی برای مدیریت یادآورها و امور روزمره است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "© 2026 Nader Naderi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}