package dev.michelelops.arcaniaquest.regole

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Il catalogo vero, quello che finisce nel gioco. */
private val catalogo: Catalogo by lazy {
    val percorso = System.getProperty("arcaniaquest.catalogo")
        ?: error("manca -Darcaniaquest.catalogo (lo passa build.gradle.kts)")
    Catalogo.daJson(File(percorso).readText())
}

class CatalogoTest {

    @Test
    fun `il catalogo si carica intero`() {
        assertEquals(6, catalogo.iniziali.size, "iniziali")
        assertEquals(36, catalogo.pescabili.size, "moduli d66")
        assertEquals(42, catalogo.tutti.size, "moduli in tutto")
    }

    @Test
    fun `non ha guai formali`() {
        val guai = Validatore.verifica(catalogo)
        assertTrue(guai.isEmpty(), "guai trovati:\n" + guai.joinToString("\n"))
    }

    @Test
    fun `la tavola d66 copre tutti e trentasei i tiri`() {
        for (primo in 1..6) for (secondo in 1..6) {
            val m = catalogo.d66(primo, secondo)
            assertEquals(primo * 10 + secondo, m.pesca.valore)
        }
    }

    @Test
    fun `il d6 pesca sempre un modulo iniziale`() {
        for (tiro in 1..6) {
            assertEquals(Famiglia.INIZIALE, catalogo.iniziale(tiro).famiglia)
        }
    }

    @Test
    fun `ogni famiglia ha le sue regole, e sono diverse`() {
        val corridoio = catalogo.regoleFamiglia.getValue(Famiglia.CORRIDOIO)
        val stanza = catalogo.regoleFamiglia.getValue(Famiglia.STANZA)
        assertEquals(Formazione.FILA, corridoio.formazione)
        assertEquals(Formazione.LIBERA, stanza.formazione)
        assertFalse(corridoio.tesoro, "nei corridoi non si trova tesoro")
        assertTrue(stanza.tesoro, "nelle stanze si")
        assertTrue(corridoio.agguato, "nei corridoi si puo' essere colti alle spalle")
    }

    @Test
    fun `i corridoi sono quelli con la C sulla tavola`() {
        assertTrue(catalogo.corridoi.all { it.id.startsWith("C") }, "id dei corridoi")
        assertTrue(catalogo.stanze.all { it.id.startsWith("S") }, "id delle stanze")
        assertTrue(catalogo.iniziali.all { it.id.startsWith("I") }, "id degli iniziali")
    }

    @Test
    fun `ogni modulo ha un'uscita`() {
        val ciechi = catalogo.tutti.filter { it.connettori.isEmpty() }
        assertTrue(ciechi.isEmpty(), "moduli senza uscita: ${ciechi.map { it.id }}")
    }
}
