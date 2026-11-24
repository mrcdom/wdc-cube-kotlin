package br.com.wdc.shopping.view.compose.views

import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.RootScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.bridge.RenderSlot

class RootView(app: RootPresenter, scope: RootScope) :
    ComposeCubeView<RootScope>("root-view", app, scope) {

    @Composable
    override fun Render() {
        @Suppress("UNUSED_VARIABLE")
        val rev = revision.value // subscribe to updates

        Box(modifier = Modifier.fillMaxSize()) {
            // Render the view of the first level
            val contentView = scope.content?.let(app::viewOf)
            if (contentView != null) {
                RenderSlot(contentView)
            }

            // Error snackbar
            val errorMessage = scope.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { safeCall { scope.errorMessage = null } }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
        }
    }
}
