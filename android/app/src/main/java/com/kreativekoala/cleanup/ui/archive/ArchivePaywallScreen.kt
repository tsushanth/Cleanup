package com.kreativekoala.cleanup.ui.archive

import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.kreativekoala.cleanup.billing.BillingManager
import com.kreativekoala.cleanup.data.model.ArchiveSubscriptionTier

/**
 * Port of iOS ArchivePaywallView.
 *
 * Shows archive storage tier options for subscription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePaywallScreen(
    billingManager: BillingManager,
    currentTier: ArchiveSubscriptionTier?,
    onDismiss: () -> Unit,
    onPurchaseComplete: () -> Unit
) {
    val purchaseInProgress by billingManager.purchaseInProgress.collectAsState()
    val activity = LocalContext.current as? Activity
    val archiveProducts = billingManager.archiveProducts

    var selectedTierIndex by remember { mutableIntStateOf(1) } // Default to 25GB

    val sortedProducts = archiveProducts.sortedBy {
        when {
            it.productId.contains("5gb") -> 0
            it.productId.contains("25gb") -> 1
            it.productId.contains("100gb") -> 2
            else -> 3
        }
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

            Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Delete with Confidence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Archive files to the cloud before deleting. Retrieve them anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Features
            ArchiveFeatureRow(Icons.Default.Shield, "Safety Net", "Never lose important files again")
            Spacer(modifier = Modifier.height(12.dp))
            ArchiveFeatureRow(Icons.Default.Restore, "Retrieve Anytime", "Download archived files whenever you need")
            Spacer(modifier = Modifier.height(12.dp))
            ArchiveFeatureRow(Icons.Default.Lock, "Encrypted Storage", "Your data is always encrypted and private")

            Spacer(modifier = Modifier.height(32.dp))

            // Storage plans
            Text(
                "Choose Your Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            ArchiveSubscriptionTier.entries.forEachIndexed { index, tier ->
                val product = sortedProducts.getOrNull(index)
                val price = product?.subscriptionOfferDetails
                    ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?.formattedPrice ?: tier.monthlyPrice
                val isCurrentPlan = currentTier == tier
                val isSelected = selectedTierIndex == index

                StoragePlanCard(
                    displayName = tier.displayName,
                    price = "$price/month",
                    isCurrentPlan = isCurrentPlan,
                    isBestValue = tier == ArchiveSubscriptionTier.TIER_25GB,
                    isSelected = isSelected,
                    onClick = { selectedTierIndex = index }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Purchase button
            Button(
                onClick = {
                    if (activity != null && sortedProducts.isNotEmpty()) {
                        val product = sortedProducts[selectedTierIndex.coerceIn(0, sortedProducts.lastIndex)]
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
                    Text("Subscribe", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

@Composable
private fun ArchiveFeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StoragePlanCard(
    displayName: String,
    price: String,
    isCurrentPlan: Boolean,
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
                        displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isCurrentPlan) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "CURRENT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (isBestValue && !isCurrentPlan) {
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
                Text(
                    price,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
