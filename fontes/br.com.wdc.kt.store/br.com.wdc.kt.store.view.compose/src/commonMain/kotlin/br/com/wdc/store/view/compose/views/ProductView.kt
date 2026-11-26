package br.com.wdc.store.view.compose.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.components.AsyncImage
import br.com.wdc.store.view.compose.components.verticalScrollbar
import br.com.wdc.store.view.compose.theme.PriceColor
import br.com.wdc.store.view.compose.util.formatPrice
import br.com.wdc.store.view.compose.util.productImageUrl
import br.com.wdc.store.view.compose.util.stripHtml

@Composable
fun ProductView(scope: ProductScope) {
    useScope(scope)

    val product = scope.product
    var quantity by remember { mutableIntStateOf(1) }

    Box(
        modifier = Modifier.fillMaxSize().padding(StoreTokens.Spacing.Lg.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        if (product != null) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Md.dp)
            ) {
                    val errorMessage = scope.errorMessage
                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(StoreTokens.Spacing.Md.dp)
                            )
                        }
                    }

                    // Product name
                    Text(
                        product.name ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Description (full width)
                    val rawDesc = product.description
                    if (!rawDesc.isNullOrBlank() && rawDesc != "unknown") {
                        val cleanDesc = stripHtml(rawDesc)
                        if (cleanDesc.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    cleanDesc,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(StoreTokens.Spacing.Lg.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }

                    // Price + quantity + image side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: price + quantity
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Lg.dp)
                        ) {
                            // Price
                            Surface(
                                shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                                color = PriceColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "\$${formatPrice(product.price)}",
                                    modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Lg.dp, vertical = StoreTokens.Spacing.Sm.dp),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PriceColor
                                )
                            }

                            // Quantity selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Md.dp)
                            ) {
                                Text(
                                    "Qty:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )

                                Surface(
                                    shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Xs.dp)
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { if (quantity > 1) quantity-- },
                                            modifier = Modifier.size(36.dp),
                                            enabled = quantity > 1
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(18.dp))
                                        }

                                        Text(
                                            "$quantity",
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )

                                        FilledTonalIconButton(
                                            onClick = { quantity++ },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Right: product image
                        AsyncImage(
                            url = productImageUrl(product.id),
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(StoreTokens.Radius.Md.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Fit,
                            placeholder = {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(StoreTokens.Radius.Md.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Md.dp, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = { safeCall { scope.onOpenProducts() } },
                            shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Back", fontWeight = FontWeight.Medium)
                        }
                        }
                        Button(
                            onClick = { safeCall { scope.onAddToCart(quantity) } },
                            shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Add to Cart", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Spacer(Modifier.height(StoreTokens.Spacing.Md.dp))
                Text(
                    "Loading product...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
