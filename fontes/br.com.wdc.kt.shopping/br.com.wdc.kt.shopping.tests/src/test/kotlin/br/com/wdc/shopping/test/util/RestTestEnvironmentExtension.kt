package br.com.wdc.shopping.test.util

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class RestTestEnvironmentExtension(
    private val env: RestTestEnvironment
) : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

    override fun beforeAll(context: ExtensionContext) {
        env.start()
    }

    override fun afterAll(context: ExtensionContext) {
        env.stop()
    }

    override fun beforeEach(context: ExtensionContext) {
        env.resetDatabase()
    }
}
