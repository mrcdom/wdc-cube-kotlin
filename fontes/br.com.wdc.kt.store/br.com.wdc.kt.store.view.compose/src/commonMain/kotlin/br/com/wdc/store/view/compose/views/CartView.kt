package br.com.wdc.store.view.compose.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.bridge.RenderSlot
import br.com.wdc.store.view.compose.theme.PriceColor
import br.com.wdc.store.view.compose.util.formatPrice

@Composable
fun CartView(scope: CartScope) {
    useScope(scope)

    Box(
        modifier = Modifier.fillMaxSize().padding(StoreTokens.Spacing.Lg.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(StoreTokens.Spacing.Md.dp))
                    Text(
                        "Shopping Cart",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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

                if (scope.rows.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(StoreTokens.Spacing.Lg.dp))
                            Text(
                                "Your cart is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))
                            Text(
                                "Add products to start shopping",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    // Cart items
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Sm.dp)
                    ) {
                        for (row in scope.rows) {
                            // Keyed by the row's scope: a row keeps its view while its product stays in the cart
                            key(row) { RenderSlot(row) }
                        }
                    }

                    // Total section
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
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PriceColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "\$${formatPrice(scope.total)}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PriceColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(StoreTokens.Spacing.Xs.dp))

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
                            Text("Continue Shopping", fontWeight = FontWeight.Medium)
                        }
                    }
                    if (scope.rows.isNotEmpty()) {
                        Button(
                            onClick = { safeCall { scope.onBuy() } },
                            shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PriceColor
                            )
                        ) {
                            Text(
                                "Buy",
                                fontWeight = FontWeight.Bold,
                                fontSize = StoreTokens.FontSize.BodyLarge.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
    }
}


/** One row of the cart, a view of its own: it recomposes when its product's quantity changes, not the whole cart. */
@Composable
fun CartItemView(scope: CartItemScope) {
    useScope(scope)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(StoreTokens.Spacing.Lg.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scope.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(StoreTokens.Spacing.Xs.dp))
                Text(
                    "\$${formatPrice(scope.price)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PriceColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quantity controls
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Xs.dp, vertical = 2.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            safeCall { scope.onModifyQuantity((scope.quantity - 1).coerceAtLeast(1)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        "${scope.quantity}",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    FilledTonalIconButton(
                        onClick = { safeCall { scope.onModifyQuantity(scope.quantity + 1) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.width(StoreTokens.Spacing.Md.dp))

            // Remove button
            TextButton(
                onClick = { safeCall { scope.onRemove() } },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remove", fontSize = StoreTokens.FontSize.Caption.sp)
            }
        }
    }
}
