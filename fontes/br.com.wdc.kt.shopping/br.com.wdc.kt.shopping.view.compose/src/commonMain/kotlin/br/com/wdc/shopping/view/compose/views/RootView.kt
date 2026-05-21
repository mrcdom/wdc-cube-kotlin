package br.com.wdc.shopping.view.compose.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.bridge.RenderSlot

class RootView(private val presenter: RootPresenter) : ComposeCubeView("root-view", presenter) {

    @Composable
    override fun Render() {
        @Suppress("UNUSED_VARIABLE")
        val rev = revision.value // subscribe to updates

        val state = presenter.state

        Box(modifier = Modifier.fillMaxSize()) {
            // Render content view
            val contentView = state.contentView
            if (contentView != null) {
                RenderSlot(contentView)
            }

            // Error snackbar
            val errorMessage = state.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { state.errorMessage = null; presenter.update() }) {
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
