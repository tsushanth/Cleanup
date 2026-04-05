package com.kreativekoala.cleanup.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.cleanup.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Track permission states
    var hasMediaPermission by remember {
        mutableStateOf(mediaPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    var hasContactPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // Media permission launcher
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMediaPermission = permissions.values.all { it }
    }

    // Contact permission launcher
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasContactPermission = permissions.values.all { it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> MediaAccessPage(
                    hasPermission = hasMediaPermission,
                    onRequestPermission = { mediaPermissionLauncher.launch(mediaPermissions) },
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    onSkip = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> ContactAccessPage(
                    hasPermission = hasContactPermission,
                    onRequestPermission = {
                        contactPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS
                            )
                        )
                    },
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                    onSkip = { coroutineScope.launch { pagerState.animateScrollToPage(3) } }
                )
                3 -> ReadyPage(
                    hasMediaPermission = hasMediaPermission,
                    hasContactPermission = hasContactPermission,
                    onGetStarted = onComplete
                )
            }
        }

        // Page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(text = stringResource(R.string.onboarding_welcome_button), onClick = onNext)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MediaAccessPage(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    if (hasPermission) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (hasPermission) Icons.Default.CheckCircle else Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (hasPermission) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            if (hasPermission) stringResource(R.string.onboarding_media_granted_title) else stringResource(R.string.onboarding_media_request_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            if (hasPermission)
                stringResource(R.string.onboarding_media_granted_subtitle)
            else
                stringResource(R.string.onboarding_media_request_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        FeatureRow(Icons.Default.Shield, stringResource(R.string.onboarding_feature_private_title), stringResource(R.string.onboarding_feature_private_subtitle))
        Spacer(modifier = Modifier.height(16.dp))
        FeatureRow(Icons.Default.Bolt, stringResource(R.string.onboarding_feature_fast_title), stringResource(R.string.onboarding_feature_fast_subtitle))
        Spacer(modifier = Modifier.height(16.dp))
        FeatureRow(Icons.Default.Delete, stringResource(R.string.onboarding_feature_you_choose_title), stringResource(R.string.onboarding_feature_you_choose_subtitle))

        Spacer(modifier = Modifier.weight(1f))

        if (hasPermission) {
            GradientButton(text = stringResource(R.string.onboarding_continue), onClick = onNext)
        } else {
            GradientButton(text = stringResource(R.string.onboarding_allow_photo_video), onClick = onRequestPermission)

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ContactAccessPage(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    if (hasPermission) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Contacts,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (hasPermission) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            if (hasPermission) stringResource(R.string.onboarding_contact_granted_title) else stringResource(R.string.onboarding_contact_request_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            if (hasPermission)
                stringResource(R.string.onboarding_contact_granted_subtitle)
            else
                stringResource(R.string.onboarding_contact_request_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        FeatureRow(Icons.Default.People, stringResource(R.string.onboarding_feature_find_dupes_title), stringResource(R.string.onboarding_feature_find_dupes_subtitle))
        Spacer(modifier = Modifier.height(16.dp))
        FeatureRow(Icons.Default.Difference, stringResource(R.string.onboarding_feature_smart_merge_title), stringResource(R.string.onboarding_feature_smart_merge_subtitle))
        Spacer(modifier = Modifier.height(16.dp))
        FeatureRow(Icons.Default.Shield, stringResource(R.string.onboarding_feature_safe_title), stringResource(R.string.onboarding_feature_safe_subtitle))

        Spacer(modifier = Modifier.weight(1f))

        if (hasPermission) {
            GradientButton(text = stringResource(R.string.onboarding_continue), onClick = onNext)
        } else {
            GradientButton(text = stringResource(R.string.onboarding_allow_contact), onClick = onRequestPermission)

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ReadyPage(
    hasMediaPermission: Boolean,
    hasContactPermission: Boolean,
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.RocketLaunch,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color(0xFF4CAF50)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.onboarding_ready_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Show permission status
        PermissionStatusRow(
            icon = Icons.Default.PhotoLibrary,
            label = stringResource(R.string.onboarding_photos_videos),
            granted = hasMediaPermission
        )
        Spacer(modifier = Modifier.height(14.dp))
        PermissionStatusRow(
            icon = Icons.Default.Contacts,
            label = stringResource(R.string.onboarding_contact_access),
            granted = hasContactPermission
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Free tier info
        FreeFeatureRow(stringResource(R.string.onboarding_free_duplicates), Icons.Default.FileCopy)
        Spacer(modifier = Modifier.height(14.dp))
        FreeFeatureRow(stringResource(R.string.onboarding_free_similar), Icons.Default.PhotoLibrary)
        Spacer(modifier = Modifier.height(14.dp))
        FreeFeatureRow(stringResource(R.string.onboarding_free_screenshots), Icons.Default.Screenshot)
        Spacer(modifier = Modifier.height(14.dp))
        FreeFeatureRow(stringResource(R.string.onboarding_free_videos), Icons.Default.VideoLibrary)
        Spacer(modifier = Modifier.height(14.dp))
        FreeFeatureRow(stringResource(R.string.onboarding_free_contacts), Icons.Default.People)

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(text = stringResource(R.string.onboarding_get_started), onClick = onGetStarted)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionStatusRow(icon: ImageVector, label: String, granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FreeFeatureRow(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Surface(
            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_free_label),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
