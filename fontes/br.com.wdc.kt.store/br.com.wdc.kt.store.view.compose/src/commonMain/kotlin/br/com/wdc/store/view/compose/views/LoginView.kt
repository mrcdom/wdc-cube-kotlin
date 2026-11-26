package br.com.wdc.store.view.compose.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.view.compose.bridge.useScope
import br.com.wdc.store.view.compose.bridge.safeCall
import br.com.wdc.store.view.compose.components.StoreLogoLarge
import br.com.wdc.store.view.compose.theme.Primary
import br.com.wdc.store.view.compose.theme.PrimaryContainer
import br.com.wdc.store.i18n.*
import br.com.wdc.store.view.compose.i18n.LocalMessages
import br.com.wdc.store.view.compose.i18n.text

@Composable
fun LoginView(scope: LoginScope) {
    val messages = LocalMessages.current
    useScope(scope)

    val loading = scope.loading
    var userName by remember { mutableStateOf(scope.userName ?: "") }
    var password by remember { mutableStateOf(scope.password ?: "") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Primary, PrimaryContainer)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(400.dp),
            shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(StoreTokens.Spacing.Xl.dp),
                verticalArrangement = Arrangement.spacedBy(StoreTokens.Spacing.Lg.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                StoreLogoLarge()

                Text(
                    messages.loginSubtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(StoreTokens.Spacing.Xs.dp))

                OutlinedTextField(
                    value = userName,
                    onValueChange = {
                        userName = it
                        scope.userName = it
                    },
                    label = { Text(messages.loginUsername()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        scope.password = it
                    },
                    label = { Text(messages.loginPassword()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { safeCall { scope.onEnter() } })
                )

                val errorMessage = scope.errorMessage
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(StoreTokens.Radius.Sm.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = messages.text(errorMessage),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(StoreTokens.Spacing.Md.dp)
                        )
                    }
                }

                Spacer(Modifier.height(StoreTokens.Spacing.Xs.dp))

                Button(
                    onClick = { safeCall { scope.onEnter() } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(StoreTokens.Radius.Md.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            messages.loginSignIn(),
                            fontSize = StoreTokens.FontSize.BodyLarge.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
