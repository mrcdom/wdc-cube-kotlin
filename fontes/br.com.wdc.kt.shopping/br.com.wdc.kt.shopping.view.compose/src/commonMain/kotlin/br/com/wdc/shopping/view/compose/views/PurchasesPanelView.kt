package br.com.wdc.shopping.view.compose.views

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
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.theme.PriceColor
import br.com.wdc.shopping.view.compose.util.formatPrice

// Altura estimada de cada card de compra
private val ITEM_HEIGHT_DP = 76.dp

class PurchasesPanelView(private val presenter: PurchasesPanelPresenter) : ComposeCubeView("purchases-panel-view", presenter) {

    @Composable
    override fun Render() {
        @Suppress("UNUSED_VARIABLE")
        val rev = revision.value

        val state = presenter.state
        val density = LocalDensity.current
        var lastReportedCapacity by remember { mutableIntStateOf(-1) }

        val sizeChangedModifier = Modifier.onSizeChanged { size ->
            val heightDp = with(density) { size.height.toDp() }
            val capacity = (heightDp / ITEM_HEIGHT_DP).toInt().coerceAtLeast(1)
            if (capacity != lastReportedCapacity) {
                lastReportedCapacity = capacity
                safeCall { presenter.onItemSizeCapacityChanged(capacity) }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Compras",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.totalCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "${state.totalCount} itens",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Lista de compras
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(sizeChangedModifier)
            ) {
                val purchases = state.purchases
                if (purchases.isEmpty()) {
                    if (lastReportedCapacity >= 1 && state.totalCount == 0) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Nenhuma compra realizada",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(purchases, key = { it.id }) { purchase ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { safeCall { presenter.onOpenReceipt(purchase.id) } },
                                shape = RoundedCornerShape(8.dp),
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
                                    Spacer(Modifier.width(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PriceColor.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            "R$ ${formatPrice(purchase.total)}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

            // Paginação
            if (state.totalCount > state.pageSize && state.pageSize > 0) {
                val totalPages = (state.totalCount + state.pageSize - 1) / state.pageSize
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { safeCall { presenter.onPageChange(state.page - 1) } },
                        enabled = state.page > 0,
                        modifier = Modifier.size(36.dp)
                    ) { Text("‹", fontWeight = FontWeight.Bold) }

                    Spacer(Modifier.width(16.dp))
                    Text(
                        "${state.page + 1} / $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(16.dp))

                    FilledTonalIconButton(
                        onClick = { safeCall { presenter.onPageChange(state.page + 1) } },
                        enabled = state.page < totalPages - 1,
                        modifier = Modifier.size(36.dp)
                    ) { Text("›", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun formatDate(epochMs: Long): String {
    val totalSeconds = epochMs / 1000
    val days = totalSeconds / 86400
    val year = 1970 + (days / 365).toInt()
    val month = ((days % 365) / 30 + 1).toInt().coerceIn(1, 12)
    val day = ((days % 365) % 30 + 1).toInt().coerceIn(1, 31)
    return "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
}
