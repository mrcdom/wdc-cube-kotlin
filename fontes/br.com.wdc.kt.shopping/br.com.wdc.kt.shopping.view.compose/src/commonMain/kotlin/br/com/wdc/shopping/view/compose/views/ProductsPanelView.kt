package br.com.wdc.shopping.view.compose.views

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
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.components.AsyncImage
import br.com.wdc.shopping.view.compose.theme.PriceColor
import br.com.wdc.shopping.view.compose.util.formatPrice
import br.com.wdc.shopping.view.compose.util.productImageUrl

class ProductsPanelView(private val presenter: ProductsPanelPresenter) : ComposeCubeView("products-panel-view", presenter) {

    @Composable
    override fun Render() {
        @Suppress("UNUSED_VARIABLE")
        val rev = revision.value

        val state = presenter.state
        val products = state.products

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
                    "Produtos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (products != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "${products.size} itens",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (products == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Carregando produtos...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (products.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum produto disponível",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minItemWidth = 160.dp
                    val spacing = 12.dp
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
                                        onClick = { safeCall { presenter.onOpenProduct(product.id) } }
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
            shape = RoundedCornerShape(8.dp),
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
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        product.name ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Price badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PriceColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "R$ ${formatPrice(product.price)}",
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
