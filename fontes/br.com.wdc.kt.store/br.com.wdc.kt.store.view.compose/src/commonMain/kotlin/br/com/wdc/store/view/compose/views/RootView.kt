package br.com.wdc.store.view.compose.views

import br.com.wdc.store.presentation.presenter.RootScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.bridge.RenderSlot
import br.com.wdc.store.i18n.*
import br.com.wdc.store.view.compose.i18n.LocalMessages
import br.com.wdc.store.view.compose.i18n.text

@Composable
fun RootView(scope: RootScope) {
    val messages = LocalMessages.current
    useScope(scope)

    Box(modifier = Modifier.fillMaxSize()) {
        // Render the view of the first level
        RenderSlot(scope.content)

        // Error snackbar
        val errorMessage = scope.errorMessage
        if (errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(StoreTokens.Spacing.Lg.dp),
                action = {
                    TextButton(onClick = { safeCall { scope.errorMessage = null } }) {
                        Text(messages.commonOk())
                    }
                }
            ) {
                Text(messages.text(errorMessage))
            }
        }
    }
}
