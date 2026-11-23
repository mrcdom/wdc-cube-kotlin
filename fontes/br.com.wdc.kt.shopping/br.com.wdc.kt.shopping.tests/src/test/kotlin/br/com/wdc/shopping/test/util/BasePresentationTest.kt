package br.com.wdc.shopping.test.util

import br.com.wdc.shopping.test.mock.RootPresenterMock
import org.junit.jupiter.api.extension.RegisterExtension

open class BasePresentationTest {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    protected val app = RootPresenterMock()

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        // App is recreated per test via field init
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        app.release()
    }
}
