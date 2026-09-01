package dev.michelelops.arcaniaquest.regole

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiaccolaturaTest {

    private val catalogo: Catalogo by lazy {
        Catalogo.daJson(File(System.getProperty("arcaniaquest.catalogo")).readText())
    }
    private val generatore by lazy { Generatore(catalogo) }

    private fun dungeon(seme: Long, pezzi: Int = 12) = generatore.genera(Sorte(seme), pezzi)

    private val semi = (1L..120L).toList() + listOf(10777L, 13L, 99991L, 777777L)

    private fun torceDi(p: Piazzato) =
        p.modulo.arredi.filter { it.tipo == "torcia" }.map { Cella(it.x, it.z) }

    @Test
    fun `lo stesso seme appende le torce negli stessi punti`() {
        for (seme in semi) {
            val a = dungeon(seme).pezzi.map { it.chiave to torceDi(it) }
            val b = dungeon(seme).pezzi.map { it.chiave to torceDi(it) }
            assertEquals(a, b, "il seme $seme cambia le torce fra una partita e l'altra")
        }
    }

    @Test
    fun `ogni sotterraneo ha qualche torcia`() {
        for (seme in semi) {
            val quante = dungeon(seme).pezzi.sumOf { torceDi(it).size }
            assertTrue(quante > 0, "il seme $seme non ne ha nemmeno una")
        }
    }

    /** Se le torce le dice il catalogo, il passo minimo non c'entra: comanda la mano. */
    private fun scritteNelCatalogo(p: Piazzato) =
        catalogo[p.modulo.id].arredi.any { it.tipo == "torcia" }

    @Test
    fun `due torce dello stesso pezzo non stanno appiccicate`() {
        for (seme in semi) {
            for (p in dungeon(seme).pezzi.filterNot { scritteNelCatalogo(it) }) {
                val torce = torceDi(p)
                for (i in torce.indices) {
                    for (j in i + 1 until torce.size) {
                        val d = maxOf(
                            abs(torce[i].x - torce[j].x),
                            abs(torce[i].z - torce[j].z)
                        )
                        assertTrue(
                            d >= Fiaccolatura.PASSO_FRA_TORCE,
                            "seme $seme, ${p.chiave}: ${torce[i]} e ${torce[j]} distano $d"
                        )
                    }
                }
            }
        }
    }

    /** Una torcia vuole un muro a cui appendersi, e non il vano di una porta. */
    @Test
    fun `ogni torcia ha un muro libero a fianco`() {
        for (seme in semi) {
            for (p in dungeon(seme).pezzi) {
                for (c in torceDi(p)) {
                    val m = p.modulo
                    assertTrue(m.calpestabile(c.x, c.z), "seme $seme: torcia dentro la roccia in $c")
                    val muroLibero = Lato.entries.any { l ->
                        !m.calpestabile(c.x + l.dx, c.z + l.dz) &&
                            m.connettori.none { it.x == c.x && it.z == c.z && it.lato == l }
                    }
                    assertTrue(muroLibero, "seme $seme, ${p.chiave}: la torcia in $c non ha un muro")
                }
            }
        }
    }

    /**
     * Quando il catalogo le torce le dice, comandano quelle: la S25 ne ha
     * due scritte a mano e devono restare quelle due, dovunque finisca e
     * comunque sia girata.
     */
    @Test
    fun `un modulo con le torce nel catalogo se le tiene`() {
        val quante = catalogo["S25"].arredi.count { it.tipo == "torcia" }
        assertEquals(2, quante, "il catalogo della S25 e' cambiato, la prova non vale piu'")

        var trovate = 0
        for (seme in semi) {
            for (p in dungeon(seme).pezzi.filter { it.modulo.id == "S25" }) {
                assertEquals(quante, torceDi(p).size, "seme $seme: alla S25 sono state aggiunte torce")
                trovate++
            }
        }
        assertTrue(trovate > 0, "in nessuno dei semi di prova e' uscita una S25")
    }
}
