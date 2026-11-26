package br.com.wdc.store.view.compose.views

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
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.bridge.RenderSlot
import br.com.wdc.store.view.compose.components.StoreLogoHeader
import br.com.wdc.store.i18n.*
import br.com.wdc.store.view.compose.i18n.LocalMessages
import br.com.wdc.store.view.compose.i18n.text

private val COMPACT_BREAKPOINT = 518.dp

@Composable
fun HomeView(scope: HomeScope) {
    val messages = LocalMessages.current
    useScope(scope)

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
        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = StoreTokens.Spacing.Lg.dp, vertical = StoreTokens.Spacing.Sm.dp),
                shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = messages.text(errorMessage),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(StoreTokens.Spacing.Md.dp)
                )
            }
        }

        // Content area (responsive)
        val content = scope.content
        if (content != null) {
            RenderSlot(content)
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                val isCompact = maxWidth < COMPACT_BREAKPOINT
                val padding = if (isCompact) StoreTokens.Spacing.Sm.dp else StoreTokens.Spacing.Lg.dp

                if (isCompact) {
                    // Tabs for mobile
                    var selectedTab by remember { mutableIntStateOf(0) }
                    val tabTitles = listOf(messages.homeTabProducts() to Icons.Filled.ShoppingBag, messages.homeTabPurchases() to Icons.Filled.Inventory2)

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

                        Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))

                        Card(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            when (selectedTab) {
                                0 -> {
                                    RenderSlot(scope.productsPanel)
                                }
                                1 -> {
                                    RenderSlot(scope.purchasesPanel)
                                }
                            }
                        }
                    }
                } else {
                    // Side-by-side for desktop
                    Row(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Lg.dp)
                    ) {
                        val productsPanel = scope.productsPanel
                        if (productsPanel != null) {
                            Card(
                                modifier = Modifier.weight(3f).fillMaxHeight(),
                                shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                RenderSlot(productsPanel)
                            }
                        }

                        val purchasesPanel = scope.purchasesPanel
                        if (purchasesPanel != null) {
                            Card(
                                modifier = Modifier.weight(2f).fillMaxHeight(),
                                shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                RenderSlot(purchasesPanel)
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
    val messages = LocalMessages.current
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (compact) {
            // Compact header: two rows
            Column(
                modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Md.dp, vertical = StoreTokens.Spacing.Sm.dp)
            ) {
                // Top row: logo + exit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Sm.dp)
                ) {
                    StoreLogoHeader(size = 32.dp, compact = true)

                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(StoreTokens.Radius.Sm.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = messages.homeSignOut(),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(StoreTokens.Spacing.Sm.dp))

                // Bottom row: greeting + cart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        messages.homeGreeting(nickName),
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
                            contentPadding = PaddingValues(horizontal = StoreTokens.Spacing.Md.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(messages.homeCart(), fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Desktop header: single row
            Row(
                modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Xl.dp, vertical = StoreTokens.Spacing.Sm.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo + Exit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Sm.dp)
                ) {
                    StoreLogoHeader(size = 36.dp, compact = false)

                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(StoreTokens.Radius.Sm.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = messages.homeSignOut(),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Lg.dp)
                ) {
                    // User greeting
                    Surface(
                        shape = RoundedCornerShape(StoreTokens.Radius.Xl.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            messages.homeGreeting(nickName),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = StoreTokens.Spacing.Lg.dp, vertical = 6.dp),
                            fontSize = StoreTokens.FontSize.Body.sp,
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
                            contentPadding = PaddingValues(horizontal = StoreTokens.Spacing.Md.dp, vertical = StoreTokens.Spacing.Sm.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(messages.homeCart(), fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
