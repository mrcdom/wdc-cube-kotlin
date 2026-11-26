package br.com.wdc.store.view.compose.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.components.AsyncImage
import br.com.wdc.store.view.compose.theme.PriceColor
import br.com.wdc.store.view.compose.util.formatPrice
import br.com.wdc.store.view.compose.util.productImageUrl

@Composable
fun ProductsPanelView(scope: ProductsPanelScope) {
    useScope(scope)

    val products = scope.products

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
                "Products",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (products != null) {
                Surface(
                    shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${products.size} ${if (products.size == 1) "item" else "items"}",
                        modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Md.dp, vertical = StoreTokens.Spacing.Xs.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(bottom = StoreTokens.Spacing.Md.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        if (products == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(StoreTokens.Spacing.Md.dp))
                    Text(
                        "Loading products...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No products available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val minItemWidth = 160.dp
                val spacing = StoreTokens.Spacing.Md.dp
                val availableWidth = maxWidth
                val columns = calculateColumns(availableWidth, minItemWidth, spacing)
                val itemWidth = (availableWidth - spacing * (columns - 1)) / columns

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    val rows = products.chunked(columns)
                    for (row in rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            for (product in row) {
                                ProductCard(
                                    product = product,
                                    modifier = Modifier.width(itemWidth),
                                    onClick = { safeCall { scope.onOpenProduct(product.id) } }
                                )
                            }
                            // Fill remaining space if row is incomplete
                            repeat(columns - row.size) {
                                Spacer(Modifier.width(itemWidth))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateColumns(availableWidth: Dp, minChildWidth: Dp, spacing: Dp): Int {
    var cols = 1
    while ((cols + 1) * minChildWidth.value + cols * spacing.value <= availableWidth.value) {
        cols++
    }
    return cols
}

@Composable
private fun ProductCard(product: ProductInfo, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Product image
            AsyncImage(
                url = productImageUrl(product.id),
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = StoreTokens.Radius.Sm.dp, topEnd = StoreTokens.Radius.Sm.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Content: name + price
            Column(modifier = Modifier.padding(StoreTokens.Spacing.Md.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    product.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Price badge
                Surface(
                    shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                    color = PriceColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "\$${formatPrice(product.price)}",
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
