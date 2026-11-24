package br.com.wdc.shopping.view.compose.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.bridge.RenderSlot
import br.com.wdc.shopping.view.compose.components.ShoppingLogoHeader

private val COMPACT_BREAKPOINT = 518.dp

class HomeView(app: RootPresenter, scope: HomeScope) :
    ComposeCubeView<HomeScope>("home-view", app, scope) {

    @Composable
    override fun Render() {
        @Suppress("UNUSED_VARIABLE")
        val rev = revision.value


        Column(modifier = Modifier.fillMaxSize()) {
            // Header (responsive)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < COMPACT_BREAKPOINT
                HeaderBar(
                    nickName = scope.nickName ?: "",
                    cartItemCount = scope.cartItemCount,
                    onOpenCart = { safeCall { scope.onOpenCart() } },
                    onExit = { safeCall { scope.onExit() } },
                    compact = isCompact
                )
            }

            // Error message
            val errorMessage = scope.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Content area (responsive)
            val contentView = scope.content?.let(app::viewOf)
            if (contentView != null) {
                RenderSlot(contentView)
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    val isCompact = maxWidth < COMPACT_BREAKPOINT
                    val padding = if (isCompact) 8.dp else 16.dp

                    if (isCompact) {
                        // Tabs for mobile
                        var selectedTab by remember { mutableIntStateOf(0) }
                        val tabTitles = listOf("Products" to Icons.Filled.ShoppingBag, "Purchases" to Icons.Filled.Inventory2)

                        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                            PrimaryTabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                tabTitles.forEachIndexed { index, (title, icon) ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Text(
                                                    title,
                                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Card(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                when (selectedTab) {
                                    0 -> {
                                        val productsView = scope.productsPanel?.let(app::viewOf)
                                        if (productsView != null) RenderSlot(productsView)
                                    }
                                    1 -> {
                                        val purchasesView = scope.purchasesPanel?.let(app::viewOf)
                                        if (purchasesView != null) RenderSlot(purchasesView)
                                    }
                                }
                            }
                        }
                    } else {
                        // Side-by-side for desktop
                        Row(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val productsView = scope.productsPanel?.let(app::viewOf)
                            if (productsView != null) {
                                Card(
                                    modifier = Modifier.weight(3f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    RenderSlot(productsView)
                                }
                            }

                            val purchasesView = scope.purchasesPanel?.let(app::viewOf)
                            if (purchasesView != null) {
                                Card(
                                    modifier = Modifier.weight(2f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    RenderSlot(purchasesView)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    nickName: String,
    cartItemCount: Int,
    onOpenCart: () -> Unit,
    onExit: () -> Unit,
    compact: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (compact) {
            // Compact header: two rows
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Top row: logo + exit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShoppingLogoHeader(size = 32.dp, compact = true)

                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bottom row: greeting + cart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Hello, $nickName",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )

                    BadgedBox(
                        badge = {
                            if (cartItemCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFFF5252),
                                    contentColor = Color.White
                                ) {
                                    Text("$cartItemCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        FilledTonalButton(
                            onClick = onOpenCart,
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Cart", fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Desktop header: single row
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo + Exit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShoppingLogoHeader(size = 36.dp, compact = false)

                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // User greeting
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Hello, $nickName",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            lineHeight = 16.sp
                        )
                    }

                    // Cart button with badge
                    BadgedBox(
                        badge = {
                            if (cartItemCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFFF5252),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        "$cartItemCount",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        FilledTonalButton(
                            onClick = onOpenCart,
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Cart", fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
