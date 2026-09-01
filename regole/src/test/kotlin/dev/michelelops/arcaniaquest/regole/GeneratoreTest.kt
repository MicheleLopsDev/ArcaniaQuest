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

    /**
     * I semi su cui si prova. Sempre gli stessi, cosi' un guaio si
     * rivede: qualche centinaio in fila piu' qualcuno sparso e qualcuno
     * che ha gia' dato problemi giocando.
     */
    private val semi = (1L..250L).toList() +
        listOf(10777L /* 8BD */, 13L /* D */, 99991L, 777777L, 4_000_000L)

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
    fun `semi diversi danno quasi sempre sotterranei diversi`() {
        // Su qualche centinaio di semi qualche doppione ci sta: i pezzi
        // sono quarantadue e i sotterranei corti. Quello che conta e' che
        // non collassino tutti sulla stessa manciata di forme.
        val forme = semi.map { s -> dungeon(s).pezzi.map { Triple(it.chiave, it.ox, it.oz) } }
        val distinti = forme.toSet().size
        assertTrue(
            distinti > forme.size * 9 / 10,
            "solo $distinti forme distinte su ${forme.size} semi"
        )
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
    fun `una porta si apre, resta aperta, e si lascia richiudere`() {
        val d = semi.map { dungeon(it) }.firstOrNull { it.porteInTutto > 0 }
        assertNotNull(d, "nessun seme ha prodotto una porta: la prova non direbbe niente")

        val porta = d.passaggi.first { it.conBattente && !it.aperta }
        val da = porta.a
        val verso = Lato.entries.first { da.x + it.dx == porta.b.x && da.z + it.dz == porta.b.z }

        assertEquals(Ostacolo.PORTA_CHIUSA, d.ostacolo(da.x, da.z, verso))
        assertNotNull(d.apri(da.x, da.z, verso))
        assertEquals(Ostacolo.NIENTE, d.ostacolo(da.x, da.z, verso))

        // aperta resta aperta: aprirla di nuovo non cambia niente, e non
        // si richiude da sola nemmeno guardandola dall'altra parte
        assertTrue(d.apri(da.x, da.z, verso) == null)
        assertEquals(Ostacolo.NIENTE, d.ostacolo(da.x, da.z, verso))
        assertEquals(Ostacolo.NIENTE, d.ostacolo(porta.b.x, porta.b.z, verso.opposto))

        // ma il gruppo puo' richiuderla, e allora torna a sbarrare
        assertNotNull(d.commuta(da.x, da.z, verso))
        assertEquals(Ostacolo.PORTA_CHIUSA, d.ostacolo(da.x, da.z, verso))
        assertEquals(Ostacolo.PORTA_CHIUSA, d.ostacolo(porta.b.x, porta.b.z, verso.opposto))

        // e si riapre dall'altro lato: una porta non ha un verso giusto
        assertNotNull(d.commuta(porta.b.x, porta.b.z, verso.opposto))
        assertEquals(Ostacolo.NIENTE, d.ostacolo(da.x, da.z, verso))
    }

    @Test
    fun `un varco senza battente non si chiude`() {
        val d = semi.map { dungeon(it) }
            .firstOrNull { it.passaggi.any { p -> !p.conBattente } }
        assertNotNull(d, "nessun seme ha prodotto un varco senza battente")

        val varco = d.passaggi.first { !it.conBattente }
        val da = varco.a
        val verso = Lato.entries.first { da.x + it.dx == varco.b.x && da.z + it.dz == varco.b.z }

        // e' un buco nel muro: non c'e' niente da tirare
        assertTrue(varco.aperta)
        assertTrue(d.commuta(da.x, da.z, verso) == null)
        assertTrue(varco.aperta)
        assertEquals(Ostacolo.NIENTE, d.ostacolo(da.x, da.z, verso))
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

    @Test
    fun `qualche sotterraneo ha un anello`() {
        // Un sotterraneo tutto ad albero ha esattamente un passaggio meno
        // dei pezzi, e costringe a rifare sempre la stessa strada a
        // ritroso. La cucitura degli attacchi che si guardano in faccia
        // serve proprio a questo: se non ne producesse mai uno, sarebbe
        // codice morto.
        val conAnello = semi.count { seme ->
            val d = dungeon(seme)
            d.passaggi.size > d.pezzi.size - 1
        }
        assertTrue(conAnello > semi.size / 20, "solo $conAnello sotterranei su ${semi.size} hanno un anello")
    }

    @Test
    fun `si percorre tutto camminando, non solo sulla carta`() {
        for (seme in semi) {
            val d = dungeon(seme)
            val giro = Perlustratore.percorri(d)
            assertTrue(
                giro.percorribile,
                "seme $seme non si percorre: " + giro.riassunto()
            )
            // ogni passo unisce due caselle vicine: e' un cammino vero,
            // non un salto da una parte all'altra della mappa
            for (p in giro.passi) {
                val dist = kotlin.math.abs(p.da.x - p.a.x) + kotlin.math.abs(p.da.z - p.a.z)
                assertEquals(1, dist, "seme $seme: passo ${p.numero} salta")
            }
        }
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
