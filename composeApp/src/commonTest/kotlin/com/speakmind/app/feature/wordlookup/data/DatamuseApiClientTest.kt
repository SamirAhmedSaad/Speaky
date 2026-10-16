package com.speakmind.app.feature.wordlookup.data

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatamuseApiClientTest {

    private val client = DatamuseApiClient(HttpClient())

    @Test
    fun skipsPlaceNamesAndOldSenses() {
        val json = """[{"word":"happy","defs":[
            "adj\tHaving a feeling arising from a consciousness of well-being or of enjoyment.",
            "n\tA place in the United States.",
            "n\tA township in Graham County, Kansas.",
            "adj\t(obsolete) Lucky.",
            "v\tTo make happy."]}]"""
        val sense = client.parse("happy", json)!!
        assertEquals("adjective", sense.partOfSpeech)
        assertTrue(sense.meaning.startsWith("Having a feeling"))
        assertFalse("place" in sense.meaning.lowercase())
        assertFalse("Lucky" in sense.meaning)
    }

    @Test
    fun keepsOrdinarySensesThatMentionPlaces() {
        val json = """[{"word":"football","defs":["n\tA sport popular in the United Kingdom and elsewhere."]}]"""
        assertEquals("A sport popular in the United Kingdom and elsewhere.", client.parse("football", json)!!.meaning)
    }
}
