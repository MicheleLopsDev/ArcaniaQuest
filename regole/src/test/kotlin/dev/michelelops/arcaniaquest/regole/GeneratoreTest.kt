package dev.michelelops.arcaniaquest.regole

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratoreTest {

    private val catalogo: Catalogo by lazy {
        Catalogo.daJson(File(System.getProperty("arcaniaquest.catalogo")).readText())
    }
    private val generatore by lazy { Generatore(catalogo) }

    private fun dungeon(seme: Long, pezzi: Int = 12) = generatore.genera(Sorte(seme), pezzi)

    /** I semi su cui si prova: pochi ma sempre gli stessi, cosi' un guaio si rivede. */
    private val semi = listOf(1L, 7L, 42L, 1234L, 99991L, 777777L)

    @Test
    fun `lo stesso seme rifa' lo stesso sotterraneo`() {
        for (seme in semi) {
            val a = dungeon(seme)
            val b = dungeon(seme)
            assertEquals(
                a.pezzi.map { Triple(it.chiave, it.ox, it.oz) },
                b.pezzi.map { Triple(it.chiave, it.ox, it.oz) },
                "seme $seme"
            )
        }
    }

    @Test
    fun `semi diversi danno sotterranei diversi`() {
        val forme = semi.map { s -> dungeon(s).pezzi.map { Triple(it.chiave, it.ox, it.oz) } }
        assertEquals(forme.size, forme.toSet().size, "due semi hanno dato lo stesso dungeon")
    }

    @Test
    fun `nessuna casella e' occupata da due pezzi`() {
        for (seme in semi) {
            val d = dungeon(seme)
            val tutte = d.pezzi.flatMap { it.celleMondo() }
            assertEquals(tutte.size, tutte.toSet().size, "caselle sovrapposte, seme $seme")
        }
    }

    @Test
    fun `si arriva dappertutto partendo dalla partenza`() {
        for (seme in semi) {
            val d = dungeon(seme)
            val viste = raggiungibili(d)
            val tutte = d.pezzi.flatMap { it.celleMondo() }.toSet()
            val isolate = tutte - viste
            assertTrue(
                isolate.isEmpty(),
                "seme $seme: ${isolate.size} caselle su ${tutte.size} non si raggiungono"
            )
        }
    }

    @Test
    fun `la partenza sta su una casella calpestabile`() {
        for (seme in semi) {
            val d = dungeon(seme)
            assertTrue(d.calpestabile(d.partenza.x, d.partenza.z), "seme $seme")
        }
    }

    @Test
    fun `ogni passaggio unisce due caselle vicine di pezzi diversi`() {
        for (seme in semi) {
            val d = dungeon(seme)
            for (p in d.passaggi) {
                val dist = kotlin.math.abs(p.a.x - p.b.x) + kotlin.math.abs(p.a.z - p.b.z)
                assertEquals(1, dist, "seme $seme: passaggio fra caselle non adiacenti")
                val qua = d.pezzoIn(p.a.x, p.a.z)
                val la = d.pezzoIn(p.b.x, p.b.z)
                assertNotNull(qua); assertNotNull(la)
                assertTrue(qua.chiave != la.chiave, "seme $seme: passaggio dentro lo stesso pezzo")
            }
        }
    }

    @Test
    fun `il sotterraneo ha piu' di un pezzo`() {
        for (seme in semi) {
            assertTrue(dungeon(seme).pezzi.size > 1, "seme $seme: nessun pezzo si e' incastrato")
        }
    }

    @Test
    fun `non si chiedono piu' pezzi di quelli richiesti`() {
        for (quanti in listOf(1, 3, 8, 20)) {
            val d = dungeon(42L, quanti)
            assertTrue(d.pezzi.size <= quanti, "chiesti $quanti, messi ${d.pezzi.size}")
        }
    }

    @Test
    fun `una porta chiusa ferma, e una volta aperta resta aperta`() {
        val d = semi.map { dungeon(it) }.firstOrNull { it.porteInTutto > 0 }
        assertNotNull(d, "nessun seme ha prodotto una porta: la prova non direbbe niente")

        val porta = d.passaggi.first { it.conBattente && !it.aperta }
        val da = porta.a
        val verso = Lato.entries.first { da.x + it.dx == porta.b.x && da.z + it.dz == porta.b.z }

        assertEquals(Ostacolo.PORTA_CHIUSA, d.ostacolo(da.x, da.z, verso))
        assertNotNull(d.apri(da.x, da.z, verso))
        assertEquals(Ostacolo.NIENTE, d.ostacolo(da.x, da.z, verso))

        // aprirla di nuovo non fa niente, e soprattutto non si richiude
        assertTrue(d.apri(da.x, da.z, verso) == null)
        assertEquals(Ostacolo.NIENTE, d.ostacolo(da.x, da.z, verso))
        assertEquals(Ostacolo.NIENTE, d.ostacolo(porta.b.x, porta.b.z, verso.opposto))
    }

    @Test
    fun `fra due pezzi vicini senza passaggio c'e' muro`() {
        var trovato = false
        for (seme in semi) {
            val d = dungeon(seme)
            for (p in d.pezzi) for (c in p.celleMondo()) for (l in Lato.entries) {
                val la = d.pezzoIn(c.x + l.dx, c.z + l.dz) ?: continue
                if (la.chiave == p.chiave) continue
                if (d.portaFra(c.x, c.z, l) != null) continue
                assertEquals(Ostacolo.MURO, d.ostacolo(c.x, c.z, l), "seme $seme")
                trovato = true
            }
        }
        // se non capita mai, la prova non ha provato niente: meglio saperlo
        assertTrue(trovato, "nessun seme ha prodotto due pezzi affiancati senza passaggio")
    }

    /** Le caselle che si raggiungono a piedi, con le porte che si possono aprire. */
    private fun raggiungibili(d: Dungeon): Set<Cella> {
        val viste = mutableSetOf(d.partenza)
        val coda = ArrayDeque(listOf(d.partenza))
        while (coda.isNotEmpty()) {
            val c = coda.removeFirst()
            for (l in Lato.entries) {
                val n = Cella(c.x + l.dx, c.z + l.dz)
                if (n in viste) continue
                // una porta chiusa non isola: si apre e si passa
                when (d.ostacolo(c.x, c.z, l)) {
                    Ostacolo.NIENTE, Ostacolo.PORTA_CHIUSA -> {
                        viste += n
                        coda += n
                    }
                    else -> {}
                }
            }
        }
        return viste
    }
}
