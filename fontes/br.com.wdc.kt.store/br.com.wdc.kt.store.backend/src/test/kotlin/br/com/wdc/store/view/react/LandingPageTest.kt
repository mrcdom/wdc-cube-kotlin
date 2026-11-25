package br.com.wdc.store.view.react

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The landing page says which clients can be opened, and warns when security is off. */
class LandingPageTest {

    @Test
    fun aDeployedClient_isOpen_andOneNotDeployed_showsHowToDeployIt() {
        val html = LandingPage(available = setOf("remote-react", "native"), securityOn = true).html

        assertContains(html, """<article class="card available">""" + "\n        <h3>React · TypeScript</h3>")
        assertContains(html, """<article class="card missing">""" + "\n        <h3>React · Kotlin</h3>")
        assertContains(html, """<article class="card available">""" + "\n        <h3>Native web · Kotlin</h3>")
        assertContains(html, """<article class="card missing">""" + "\n        <h3>Compose Multiplatform · Web</h3>")
        assertFalse("{{" in html, "every marker is filled")
    }

    @Test
    fun withoutSecurity_itWarnsThatTheLocalViewsCannotSignIn() {
        assertTrue("Security is off" in LandingPage(available = emptySet(), securityOn = false).html)
        assertFalse("Security is off" in LandingPage(available = emptySet(), securityOn = true).html)
    }
}
