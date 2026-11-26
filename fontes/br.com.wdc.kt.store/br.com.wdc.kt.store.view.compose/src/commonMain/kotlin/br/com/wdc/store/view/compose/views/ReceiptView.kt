package br.com.wdc.store.view.compose.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.theme.PriceColor
import br.com.wdc.store.view.compose.theme.SuccessColor
import br.com.wdc.store.view.compose.theme.SuccessContainer
import br.com.wdc.store.view.compose.util.formatDate
import br.com.wdc.store.view.compose.util.formatPrice

@Composable
fun ReceiptView(scope: ReceiptScope) {
    useScope(scope)

    val receipt = scope.receipt

    Box(
        modifier = Modifier.fillMaxSize().padding(StoreTokens.Spacing.Lg.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Md.dp)
        ) {
                // Success banner
                if (scope.notifySuccess) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                        color = SuccessContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(32.dp), tint = SuccessColor)
                            Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))
                            Text(
                                "Purchase completed successfully!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SuccessColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (receipt != null) {
                    // Receipt header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Sm.dp)) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(28.dp))
                            Text(
                                "Receipt",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        receipt.date?.let {
                            Surface(
                                shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    formatDate(it),
                                    modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Md.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Items
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Header row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = StoreTokens.Spacing.Xs.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Item",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Qty",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                "Amount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(100.dp)
                            )
                        }

                        receipt.items.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(StoreTokens.Spacing.Md.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.description ?: "",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${item.quantity}x",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(50.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "\$${formatPrice(item.value)}",
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(100.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PriceColor
                                    )
                                }
                            }
                        }
                    }

                    // Total
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = StoreTokens.Spacing.Xs.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total: ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        receipt.total?.let {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PriceColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "\$${formatPrice(it)}",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PriceColor
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))

                // Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { safeCall { scope.onOpenProducts() } },
                        shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                "Continue Shopping",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
    }
}
