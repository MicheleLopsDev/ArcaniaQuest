package dev.michelelops.arcaniaquest.regole

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SorteTest {

    @Test
    fun `lo stesso seme da' la stessa sequenza`() {
        val a = Sorte(123456789L)
        val b = Sorte(123456789L)
        repeat(200) { assertEquals(a.d66(), b.d66()) }
    }

    @Test
    fun `semi diversi danno sequenze diverse`() {
        val a = Sorte(1L)
        val b = Sorte(2L)
        val primoA = List(40) { a.d66() }
        val primoB = List(40) { b.d66() }
        assertNotEquals(primoA, primoB)
    }

    @Test
    fun `il d66 sta sempre nella tavola`() {
        val s = Sorte(42L)
        repeat(2000) {
            val v = s.d66()
            assertTrue(v / 10 in 1..6 && v % 10 in 1..6, "tiro fuori tavola: $v")
        }
    }

    @Test
    fun `il seme scritto si rilegge uguale`() {
        for (seme in listOf(1L, 42L, 987654321L, 78364164095L)) {
            val riletto = Sorte.leggi(Sorte.scrivi(seme))
            assertEquals(seme, riletto.seme, "seme ${Sorte.scrivi(seme)}")
        }
    }

    @Test
    fun `una parola qualunque diventa un seme, e sempre lo stesso`() {
        val a = Sorte.leggi("la cripta di michele")
        val b = Sorte.leggi("la cripta di michele")
        assertEquals(a.seme, b.seme)
        assertNotEquals(a.seme, Sorte.leggi("la cripta di michel").seme)
    }
}

class EsplorazioneTest {

    @Test
    fun `si parte da zero e si arriva a cento`() {
        val e = Esplorazione(4)
        assertEquals(0, e.percento)
        e.calpesta(Cella(0, 0))
        e.calpesta(Cella(1, 0))
        assertEquals(50, e.percento)
        e.calpesta(Cella(0, 1))
        e.calpesta(Cella(1, 1))
        assertEquals(100, e.percento)
    }

    @Test
    fun `ripassare sulla stessa casella non conta due volte`() {
        val e = Esplorazione(10)
        assertTrue(e.calpesta(Cella(2, 2)))
        repeat(5) { assertTrue(!e.calpesta(Cella(2, 2))) }
        assertEquals(1, e.quante)
    }

    @Test
    fun `una casella intravista sta sulla mappa ma non conta come esplorata`() {
        val e = Esplorazione(10)
        e.vedi(Cella(5, 5))
        assertTrue(e.conosciuta(Cella(5, 5)), "va sulla mappa")
        assertTrue(!e.calpestata(Cella(5, 5)), "ma non e' stata calpestata")
        assertEquals(0, e.percento)
        assertEquals(1, e.quanteViste)
    }

    @Test
    fun `calpestare vuol dire anche vedere`() {
        val e = Esplorazione(10)
        e.calpesta(Cella(1, 1))
        assertTrue(e.conosciuta(Cella(1, 1)))
    }

    @Test
    fun `un modulo nuovo allarga il totale`() {
        val e = Esplorazione()
        assertEquals(0, e.percento)
        val m = Modulo(
            id = "T", nome = "prova", famiglia = Famiglia.STANZA, pesca = Pesca("prova", 1),
            ingombro = Ingombro(2, 1), caselle = listOf("11"),
            connettori = listOf(Connettore(Lato.NORD, listOf(0, 0)))
        )
        e.aggiungiModulo(m)
        assertEquals(2, e.inTutto)
        e.calpesta(Cella(0, 0))
        assertEquals(50, e.percento)
    }
}
