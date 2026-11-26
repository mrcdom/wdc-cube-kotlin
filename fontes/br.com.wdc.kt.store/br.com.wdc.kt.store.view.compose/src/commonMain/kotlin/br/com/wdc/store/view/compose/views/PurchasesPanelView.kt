package br.com.wdc.store.view.compose.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.theme.PriceColor
import br.com.wdc.store.view.compose.util.formatDate
import br.com.wdc.store.view.compose.util.formatPrice

// Estimated height of each purchase card
private val ITEM_HEIGHT_DP = 76.dp

@Composable
fun PurchasesPanelView(scope: PurchasesPanelScope) {
    useScope(scope)

    val density = LocalDensity.current
    var lastReportedCapacity by remember { mutableIntStateOf(-1) }

    val sizeChangedModifier = Modifier.onSizeChanged { size ->
        val heightDp = with(density) { size.height.toDp() }
        val capacity = (heightDp / ITEM_HEIGHT_DP).toInt().coerceAtLeast(1)
        if (capacity != lastReportedCapacity) {
            lastReportedCapacity = capacity
            safeCall { scope.onItemSizeCapacityChanged(capacity) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(StoreTokens.Spacing.Md.dp)
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = StoreTokens.Spacing.Md.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Purchases",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (scope.totalCount > 0) {
                Surface(
                    shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "${scope.totalCount} ${if (scope.totalCount == 1) "item" else "items"}",
                        modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Md.dp, vertical = StoreTokens.Spacing.Xs.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(bottom = StoreTokens.Spacing.Md.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Purchase list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(sizeChangedModifier)
        ) {
            val purchases = scope.purchases
            if (purchases.isEmpty()) {
                if (lastReportedCapacity >= 1 && scope.totalCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))
                            Text(
                                "No purchases yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Sm.dp)
                ) {
                    items(purchases, key = { it.id }) { purchase ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { safeCall { scope.onOpenReceipt(purchase.id) } },
                            shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        formatDate(purchase.date),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        purchase.items.joinToString(", "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(StoreTokens.Spacing.Md.dp))
                                Surface(
                                    shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                                    color = PriceColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        "\$${formatPrice(purchase.total)}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = StoreTokens.Spacing.Xs.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PriceColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pagination
        if (scope.totalCount > scope.pageSize && scope.pageSize > 0) {
            val totalPages = (scope.totalCount + scope.pageSize - 1) / scope.pageSize
            Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { safeCall { scope.onPageChange(scope.page - 1) } },
                    enabled = scope.page > 0,
                    modifier = Modifier.size(36.dp)
                ) { Text("‹", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.width(StoreTokens.Spacing.Lg.dp))
                Text(
                    "${scope.page + 1} / $totalPages",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(StoreTokens.Spacing.Lg.dp))

                FilledTonalIconButton(
                    onClick = { safeCall { scope.onPageChange(scope.page + 1) } },
                    enabled = scope.page < totalPages - 1,
                    modifier = Modifier.size(36.dp)
                ) { Text("›", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
