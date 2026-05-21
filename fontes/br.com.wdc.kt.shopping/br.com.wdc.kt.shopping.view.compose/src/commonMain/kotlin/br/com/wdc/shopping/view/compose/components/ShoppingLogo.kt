package br.com.wdc.shopping.view.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp

/**
 * Logo grande para tela de login.
 */
@Composable
fun ShoppingLogoLarge() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.LocalMall,
                    contentDescription = "Shopping",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box {
            Text(
                "Shopping",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "compose",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 16.dp)
            )
        }
    }
}

/**
 * Logo pequeno para cabeçalho (fundo escuro).
 */
@Composable
fun ShoppingLogoHeader(size: Dp = 36.dp, compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.LocalMall,
                    contentDescription = "Shopping",
                    modifier = Modifier.size(size * 0.6f),
                    tint = Color.White
                )
            }
        }
        Box {
            Text(
                "Shopping",
                fontSize = if (compact) 16.sp else 20.sp,
                lineHeight = if (compact) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "compose",
                fontSize = if (compact) 9.sp else 10.sp,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = if (compact) 16.dp else 16.dp)
            )
        }
    }
}
