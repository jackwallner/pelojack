package com.jackwallner.pelojack.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NanitPageTest {
    @Test fun allowsOfficialHttpsPages() {
        assertTrue(NanitPage.accepts("https://my.nanit.com/"))
        assertTrue(NanitPage.accepts("https://login.nanit.com/login?next=%2F"))
        assertTrue(NanitPage.accepts("https://nanit.com:443/"))
    }

    @Test fun refusesOtherOriginsAndLocalFiles() {
        listOf("http://my.nanit.com", "https://my.nanit.com.attacker.example", "https://notnanit.com",
            "https://my.nanit.com@attacker.example", "https://user@my.nanit.com", "https://my.nanit.com:8443",
            "javascript:alert(1)", "file:///data/user/0/", "intent://nanit", "https://my.nanit.com\\@attacker.example")
            .forEach { assertFalse(it, NanitPage.accepts(it)) }
    }
}
