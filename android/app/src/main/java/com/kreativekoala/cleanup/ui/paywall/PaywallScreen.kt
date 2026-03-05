package com.kreativekoala.cleanup.ui.paywall

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.kreativekoala.cleanup.billing.BillingManager
import com.kreativekoala.cleanup.billing.PaywallContext

/**
 * Full-screen paywall matching the iOS PaywallView.
 *
 * Shows context-aware title/subtitle, feature list, product options, and purchase button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    context: PaywallContext,
    billingManager: BillingManager,
    onDismiss: () -> Unit,
    onPurchaseComplete: () -> Unit
) {
    val products by billingManager.products.collectAsState()
    val purchaseInProgress by billingManager.purchaseInProgress.collectAsState()
    val lastError by billingManager.lastError.collectAsState()
    val activity = LocalContext.current as? Activity

    val proProducts = billingManager.proProducts
    var selectedProductIndex by remember { mutableIntStateOf(0) } // 0 = yearly (best value)

    // Sort: yearly first, monthly second
    val sortedProducts = proProducts.sortedBy {
        if (it.productId.contains("yearly")) 0 else 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Pro badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    "PRO",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Context-aware title
            Text(
                context.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                context.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Features list
            val features = listOf(
                PaywallFeature(Icons.Default.AutoAwesome, "Unlimited Cleanups", "Clean your device as often as you want"),
                PaywallFeature(Icons.Default.FileCopy, "Delete Duplicates & Similar", "Find and remove duplicate photos instantly"),
                PaywallFeature(Icons.Default.VideoLibrary, "Large Video Management", "Compress or delete space-hogging videos"),
                PaywallFeature(Icons.Default.Lock, "Private & Secure", "All scanning happens on your device")
            )

            features.forEach { feature ->
                FeatureRow(feature)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Product options
            sortedProducts.forEachIndexed { index, product ->
                val isSelected = selectedProductIndex == index
                val isYearly = product.productId.contains("yearly")
                val price = product.subscriptionOfferDetails
                    ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?.formattedPrice ?: ""
                val period = if (isYearly) "per year" else "per month"

                ProductOption(
                    price = price,
                    period = period,
                    isBestValue = isYearly,
                    isSelected = isSelected,
                    onClick = { selectedProductIndex = index }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Purchase button
            Button(
                onClick = {
                    if (activity != null && sortedProducts.isNotEmpty()) {
                        val product = sortedProducts[selectedProductIndex]
                        billingManager.launchPurchase(activity, product) { success ->
                            if (success) onPurchaseComplete()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !purchaseInProgress && sortedProducts.isNotEmpty()
            ) {
                if (purchaseInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore purchases
            TextButton(onClick = {
                // Restore handled by querying existing purchases
                billingManager.queryExistingPurchases()
            }) {
                Text("Restore Purchases", style = MaterialTheme.typography.bodySmall)
            }

            // Error message
            lastError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Terms
            Text(
                "Subscriptions auto-renew. Cancel anytime in Google Play settings.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private data class PaywallFeature(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun FeatureRow(feature: PaywallFeature) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            feature.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                feature.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProductOption(
    price: String,
    period: String,
    isBestValue: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$price $period",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isBestValue) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50)
                        ) {
                            Text(
                                "BEST VALUE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (isBestValue) {
                    Text(
                        "Save 75% vs monthly",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
