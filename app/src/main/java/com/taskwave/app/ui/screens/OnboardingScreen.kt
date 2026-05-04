package com.taskwave.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskwave.app.R

private data class Page(val titleRes: Int, val descRes: Int, val accentLight: Color, val accentDark: Color)

private val pages = listOf(
    Page(R.string.onboarding_1_title, R.string.onboarding_1_desc, Color(0xFF6C63FF), Color(0xFF8B85FF)),
    Page(R.string.onboarding_2_title, R.string.onboarding_2_desc, Color(0xFF4158D0), Color(0xFF5A78E8)),
    Page(R.string.onboarding_3_title, R.string.onboarding_3_desc, Color(0xFF4FC3F7), Color(0xFF4FC3F7)),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == currentPage) 28.dp else 8.dp,
                        animationSpec = tween(300), label = "dot"
                    )
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (i == currentPage) scheme.primary
                                else scheme.outline
                            )
                    )
                }
            }

            Spacer(Modifier.height(56.dp))

            // Illustration placeholder — gradient card
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                },
                label = "illustration"
            ) { idx ->
                val p = pages[idx]
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.linearGradient(listOf(p.accentLight.copy(.15f), p.accentDark.copy(.08f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Geometric decoration
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(p.accentLight.copy(.3f), Color.Transparent))
                            )
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        repeat(3) { row ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(p.accentLight.copy(alpha = 1f - row * 0.25f))
                                )
                                Box(
                                    Modifier
                                        .height(10.dp)
                                        .width((80 - row * 16).dp)
                                        .clip(CircleShape)
                                        .background(p.accentLight.copy(alpha = 1f - row * 0.2f))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn(tween(350)).togetherWith(fadeOut(tween(200))) },
                label = "text"
            ) { idx ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(pages[idx].titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = scheme.onSurface,
                        lineHeight = 36.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(pages[idx].descRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (currentPage < pages.size - 1) currentPage++ else onFinish()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
            ) {
                Text(
                    text = if (currentPage < pages.size - 1)
                        stringResource(R.string.onboarding_next)
                    else
                        stringResource(R.string.onboarding_start),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (currentPage < pages.size - 1) {
                TextButton(onClick = onFinish) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
