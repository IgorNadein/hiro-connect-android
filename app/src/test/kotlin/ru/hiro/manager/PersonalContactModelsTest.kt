package ru.hiro.manager

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class PersonalContactModelsTest {
    @Test fun legacyAndExternalClientsHaveNoGuessedOrigin() {
        val old = hiroRoutingJson.decodeFromString<HiroClient>("""{"number":"+79990000001","name":"Legacy","rules":[],"updated_by":"editor"}""")
        val external = hiroRoutingJson.decodeFromString<HiroClient>("""{"number":"+79990000001","name":"Import","rules":[],"introduced_by_user_id":null}""")
        assertFalse(hiroRoutingJson.encodeToString(old).contains("introduced"))
        assertFalse(hiroRoutingJson.encodeToString(external).contains("introduced"))
    }
    @Test fun publicOriginIsDisplayOnlyAndNeverSentByTheEditor() {
        val client = hiroRoutingJson.decodeFromString<HiroClient>("""{"number":"+79990000001","name":"Client","rules":[],"introduced_by_user_id":"alice","introduced_by_name":"Alice","updated_by":"bob"}""")
        assertFalse(hiroRoutingJson.encodeToString(client).contains("alice"))
        assertFalse(hiroRoutingJson.encodeToString(client).contains("introduced"))
        val update = hiroRoutingJson.encodeToString(HiroClientUpdate("Edited", emptyList()))
        assertFalse(update.contains("introduced")); assertFalse(update.contains("owner"))
    }
    @Test fun contactsAreCanonicalPrivateAndOwnerIsNotAClientSuppliedField() {
        val items = canonicalPersonalContacts(listOf(HiroPersonalContact("89990000001", " First "), HiroPersonalContact("+79990000001", "Duplicate"), HiroPersonalContact("9990000001", "Ambiguous")))
        assertEquals(listOf(HiroPersonalContact("+79990000001", "First")), items)
        val encoded = hiroRoutingJson.encodeToString(HiroPersonalContactsUpdate(items))
        assertFalse(encoded.contains("owner")); assertFalse(encoded.contains("user_id"))
        val response = hiroRoutingJson.decodeFromString<HiroPersonalContacts>("""{"revision":1,"source_id":"phone","contacts":[{"number":"+79990000001","name":"First"}],"source_contacts":[]}""")
        assertEquals(1, response.contacts.size); assertTrue(response.sourceContacts.isEmpty())
    }
    @Test fun consentIsBoundToBothServerAndAccountNotSessionRefresh() {
        fun session(server: String, id: String, token: String) = HiroSession(server, "$server/api/v1", token, "refresh", "2030-01-01T00:00:00Z", "session", "$server/refresh", "$server/logout", HiroUser(id,id,id,"manager"))
        val first = session("https://one.example", "alice", "token-a")
        assertEquals(personalContactAccountKey(first), personalContactAccountKey(first.copy(accessToken="token-b")))
        assertNotEquals(personalContactAccountKey(first), personalContactAccountKey(session("https://one.example", "bob", "token-a")))
        assertNotEquals(personalContactAccountKey(first), personalContactAccountKey(session("https://two.example", "alice", "token-a")))
    }
}
