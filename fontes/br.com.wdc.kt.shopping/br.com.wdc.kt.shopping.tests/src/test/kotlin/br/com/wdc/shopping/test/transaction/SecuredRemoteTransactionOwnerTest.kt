package br.com.wdc.shopping.test.transaction

import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.persistence.client.HttpPurchaseRepository
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestTransactionService
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** O dono da transação remota é o usuário **e** a instância de cliente: o mesmo usuário em dois clientes não se bloqueia. */
class SecuredRemoteTransactionOwnerTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-secured-tx-owner", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @AfterEach
    fun signOut() = env.logout()

    private fun purchase() = Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } }

    @Test
    fun sameUser_inAnotherClient_writesWhileTheFirstHasAnOpenTransaction() = runBlocking {
        env.loginAs("fulano")
        // o segundo cliente (outro X-Client-Id) do mesmo usuário, com o mesmo token
        val other = OkHttpTransport("http://127.0.0.1:${env.port}").apply { accessTokenSupplier = { env.authClient!!.accessToken } }
        val otherPurchases = HttpPurchaseRepository(other)
        val before = env.purchaseRepo.count(PurchaseCriteria())

        RestTransactionService(env.transport).required {
            assertTrue(env.purchaseRepo.insert(purchase()))
            // antes: 409 "cabeçalho perdido", porque o dono era só o usuário
            assertTrue(otherPurchases.insert(purchase()))
        }
        assertEquals(before + 2, env.purchaseRepo.count(PurchaseCriteria()))
    }
}
