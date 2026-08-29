package dev.michelelops.arcaniaquest.regole

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La rotazione e' il cuore del generatore: se sbaglia, i moduli si
 * incastrano storti e il dungeon non si accorge di niente.
 */
class RotazioneTest {

    private val catalogo: Catalogo by lazy {
        Catalogo.daJson(File(System.getProperty("arcaniaquest.catalogo")).readText())
    }

    /** Un pezzo asimmetrico, fatto a mano: cosi' gli errori si vedono. */
    private val provino = Modulo(
        id = "T1", nome = "Provino", famiglia = Famiglia.CORRIDOIO,
        pesca = Pesca("prova", 1),
        ingombro = Ingombro(w = 3, d = 2),
        caselle = listOf("110", "011"),
        connettori = listOf(
            Connettore(Lato.OVEST, listOf(0, 0), porta = true),
            Connettore(Lato.EST, listOf(2, 1), porta = false)
        )
    )

    @Test
    fun `quattro quarti riportano il modulo com'era`() {
        for (m in catalogo.tutti) {
            assertEquals(m, m.ruotato(4), "il modulo ${m.id} non torna dopo quattro quarti")
        }
    }

    @Test
    fun `un quarto scambia larghezza e profondita`() {
        val g = provino.ruotato(1)
        assertEquals(provino.profondita, g.larghezza)
        assertEquals(provino.larghezza, g.profondita)
    }

    @Test
    fun `ruotando, il numero di caselle non cambia`() {
        for (m in catalogo.tutti) for (q in 1..3) {
            assertEquals(m.celle().size, m.ruotato(q).celle().size, "${m.id} ruotato di $q")
        }
    }

    @Test
    fun `un connettore a ovest guarda a nord dopo un quarto`() {
        val g = provino.ruotato(1)
        val lati = g.connettori.map { it.lato }.toSet()
        assertEquals(setOf(Lato.NORD, Lato.SUD), lati)
    }

    @Test
    fun `i connettori restano su casella calpestabile e aperti verso fuori`() {
        for (m in catalogo.tutti) for (q in 0..3) {
            val g = m.ruotato(q)
            for (k in g.connettori) {
                assertTrue(g.calpestabile(k.x, k.z), "${m.id} q$q: connettore su roccia $k")
                assertTrue(g.apreVersoFuori(k), "${m.id} q$q: connettore chiuso dentro $k")
            }
        }
    }

    @Test
    fun `la porta resta una porta anche dopo aver girato`() {
        val g = provino.ruotato(3)
        assertEquals(1, g.connettori.count { it.porta }, "porte dopo tre quarti")
    }

    @Test
    fun `la sala ovale resta ovale`() {
        val ovale = catalogo["S25"]
        val g = ovale.ruotato(1)
        assertEquals(ovale.pianta.size, g.pianta.size, "le forme della pianta si perdono ruotando")
        val tondo = g.pianta.first { it.forma == "rettangoloArrotondato" }
        assertEquals(0.9, tondo.raggio, "il raggio si e' perso")
        // La camera era 3 larga e 4 profonda: ruotata dev'essere 4 e 3.
        assertEquals(4.0, tondo.w)
        assertEquals(3.0, tondo.d)
        // E deve restare dentro il nuovo ingombro.
        assertTrue(tondo.x >= 0.0 && tondo.x + tondo.w <= g.larghezza.toDouble(), "esce dall'ingombro in x")
        assertTrue(tondo.z >= 0.0 && tondo.z + tondo.d <= g.profondita.toDouble(), "esce dall'ingombro in z")
    }

    @Test
    fun `la partenza gira insieme al modulo`() {
        val iniziale = catalogo.iniziali.first { it.partenza != null }
        val g = iniziale.ruotato(1)
        val p = g.partenza!!
        assertTrue(g.calpestabile(p.x, p.z), "la partenza e' finita nella roccia")
        assertEquals(iniziale.partenza!!.verso.ruotato(1), p.verso)
    }
}
