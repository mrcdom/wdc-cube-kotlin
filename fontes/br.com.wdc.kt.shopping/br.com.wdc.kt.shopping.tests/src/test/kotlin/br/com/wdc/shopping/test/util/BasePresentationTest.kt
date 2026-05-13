package br.com.wdc.shopping.test.util

import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.extension.RegisterExtension

open class BasePresentationTest {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    protected val app = ShoppingApplicationMock()

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        // App is recreated per test via field init
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        app.release()
    }
}
