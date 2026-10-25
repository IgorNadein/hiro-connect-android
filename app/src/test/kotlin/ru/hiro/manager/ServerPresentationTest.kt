package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test

class ServerPresentationTest {
    @Test fun corporateBrandDoesNotLeakToOtherProviders() {
        assertTrue(isCorpProvider(" CORP - HiRo "))
        assertEquals("Войти через CORP - HiRo", oidcButtonLabel("corp"))
        assertFalse(isCorpProvider("another provider"))
        assertFalse(isCorpProvider(null))
        assertEquals("Войти через another provider", oidcButtonLabel("another provider"))
        assertEquals("Войти через SSO", oidcButtonLabel(" "))
    }
    @Test fun serverProblemIsPreservedWithoutInventingCachedHistory() {
        assertEquals("Телефония не подключена", journalProblemText(" Телефония не подключена "))
        assertNull(journalProblemText(" "))
        assertEquals("Asterisk недоступен", liveUnavailableText("Asterisk недоступен"))
        assertTrue(liveUnavailableText("").contains("недоступно"))
    }
    @Test fun incorrectAddressAndMissingFeatureHaveDifferentMessages() {
        assertTrue(serverErrorMessage(404,false,"/.well-known/hiro/client",null).contains("адрес"))
        assertTrue(serverErrorMessage(404,true,"/api/v1/calls",null).contains("раздел"))
        assertEquals("Звонок удалён",serverErrorMessage(404,true,"/api/v1/calls/id","Звонок удалён"))
        assertTrue(serverErrorMessage(502,true,"/api/v1/calls","").contains("Попробуйте позже"))
        assertEquals("Сессия завершена",serverErrorMessage(401,true,"/api/v1/calls","ignored"))
    }
}
