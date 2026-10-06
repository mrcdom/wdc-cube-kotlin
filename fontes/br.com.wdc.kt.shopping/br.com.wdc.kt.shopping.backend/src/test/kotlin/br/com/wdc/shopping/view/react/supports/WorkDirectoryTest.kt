package br.com.wdc.shopping.view.react.supports

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkDirectoryTest {

    private val none: (String) -> String? = { null }

    @Test
    fun comesFromTheArgument() {
        val work = Files.createTempDirectory("work").toRealPath()
        val resolved = WorkDirectory.resolve(arrayOf("9090", "--workdir=$work"), none)
        assertEquals(work, resolved.toRealPath())
        assertEquals(listOf("9090"), WorkDirectory.otherArguments(arrayOf("9090", "--workdir=$work")))
    }

    @Test
    fun fallsBackToTheEnvironmentVariable_andTheArgumentWins() {
        val fromEnvironment = Files.createTempDirectory("work-env").toRealPath()
        val fromArgument = Files.createTempDirectory("work-arg").toRealPath()
        val environment: (String) -> String? = { if (it == "SHOPPING_WORKDIR") fromEnvironment.toString() else null }

        assertEquals(fromEnvironment, WorkDirectory.resolve(emptyArray(), environment).toRealPath())
        assertEquals(fromArgument, WorkDirectory.resolve(arrayOf("--workdir=$fromArgument"), environment).toRealPath())
    }

    @Test
    fun isMandatory_andTheErrorSaysHowToInformIt() {
        for (args in listOf(emptyArray(), arrayOf("8080"), arrayOf("--workdir="))) {
            val e = assertFailsWith<IllegalArgumentException> { WorkDirectory.resolve(args, none) }
            assertTrue("--workdir=" in e.message!! && "SHOPPING_WORKDIR" in e.message!!, e.message)
        }
    }

    @Test
    fun mustExist() {
        val missing = Files.createTempDirectory("work").resolve("nao-existe")
        val e = assertFailsWith<IllegalArgumentException> { WorkDirectory.resolve(arrayOf("--workdir=$missing"), none) }
        assertTrue("não existe" in e.message!!, e.message)
        assertTrue(Files.notExists(missing))
    }
}
