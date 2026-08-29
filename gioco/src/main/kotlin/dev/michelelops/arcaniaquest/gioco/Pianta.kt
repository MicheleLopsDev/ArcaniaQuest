package dev.michelelops.arcaniaquest.gioco

import dev.michelelops.arcaniaquest.regole.Connettore
import dev.michelelops.arcaniaquest.regole.Forma
import dev.michelelops.arcaniaquest.regole.Modulo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Un rettangolo in caselle, usato per i varchi e per i confronti. */
data class Riquadro(val x0: Float, val z0: Float, val x1: Float, val z1: Float) {
    fun contiene(x: Float, z: Float): Boolean = x > x0 && x < x1 && z > z0 && z < z1
}

/**
 * La pianta di un modulo ridotta a poligoni convessi, in caselle.
 *
 * Da qui in poi la mesh non sa piu' niente di griglie: vede contorni. E'
 * quello che permette a una sala ovale di stare dentro una griglia
 * quadrata senza sembrare quadrata.
 */
object Pianta {

    /**
     * Le forme di un modulo. Se il modulo non ha una pianta scritta a
     * mano, se la ricava dalle caselle unendo le corse orizzontali: meno
     * rettangoli, meno muri interni da scartare dopo.
     */
    fun formeDi(m: Modulo): List<Forma> {
        if (m.pianta.isNotEmpty()) return m.pianta
        val forme = mutableListOf<Forma>()
        for (z in 0 until m.profondita) {
            var x = 0
            while (x < m.larghezza) {
                if (!m.calpestabile(x, z)) { x++; continue }
                var fine = x
                while (fine + 1 < m.larghezza && m.calpestabile(fine + 1, z)) fine++
                forme += Forma("rettangolo", x.toDouble(), z.toDouble(), (fine - x + 1).toDouble(), 1.0)
                x = fine + 1
            }
        }
        return forme
    }

    /** Il contorno di una forma, come anello di punti (x, z) in caselle. */
    fun contorno(f: Forma): List<FloatArray> {
        val x = f.x.toFloat(); val z = f.z.toFloat()
        val w = f.w.toFloat(); val d = f.d.toFloat()
        val punti = if (f.forma == "rettangoloArrotondato" && f.raggio > 0.0) {
            val r = minOf(f.raggio.toFloat(), w / 2f, d / 2f)
            arrotondato(x, z, w, d, r)
        } else {
            listOf(
                floatArrayOf(x, z), floatArrayOf(x + w, z),
                floatArrayOf(x + w, z + d), floatArrayOf(x, z + d)
            )
        }
        return suddividi(punti, Misure.PASSO_CONTORNO)
    }

    private fun arrotondato(x: Float, z: Float, w: Float, d: Float, r: Float): List<FloatArray> {
        val fette = 9
        val angoli = listOf(
            floatArrayOf(x + w - r, z + r, (-PI / 2).toFloat(), 0f),
            floatArrayOf(x + w - r, z + d - r, 0f, (PI / 2).toFloat()),
            floatArrayOf(x + r, z + d - r, (PI / 2).toFloat(), PI.toFloat()),
            floatArrayOf(x + r, z + r, PI.toFloat(), (PI * 1.5).toFloat())
        )
        val punti = mutableListOf<FloatArray>()
        for (a in angoli) {
            for (i in 0..fette) {
                val ang = a[2] + (a[3] - a[2]) * i / fette
                val p = floatArrayOf(a[0] + cos(ang) * r, a[1] + sin(ang) * r)
                val u = punti.lastOrNull()
                if (u == null || hypot(u[0] - p[0], u[1] - p[1]) > 1e-4f) punti += p
            }
        }
        return punti
    }

    /**
     * Spezza ogni lato in tratti corti. Serve perche' i varchi si
     * ritagliano per segmenti: un lato lungo o c'e' tutto o non c'e'.
     */
    private fun suddividi(poly: List<FloatArray>, passo: Float): List<FloatArray> {
        val fuori = mutableListOf<FloatArray>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val n = max(1, Math.ceil((hypot(b[0] - a[0], b[1] - a[1]) / passo).toDouble()).toInt())
            for (k in 0 until n) {
                val t = k.toFloat() / n
                fuori += floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            }
        }
        return fuori
    }

    /** Il baricentro di un contorno: serve a orientare le normali. */
    fun baricentro(poly: List<FloatArray>): FloatArray {
        var bx = 0f; var bz = 0f
        for (p in poly) { bx += p[0]; bz += p[1] }
        return floatArrayOf(bx / poly.size, bz / poly.size)
    }

    /**
     * Il punto sta dentro la forma, con un margine.
     *
     * Il margine non e' un dettaglio: due forme dello stesso modulo si
     * toccano lungo un bordo comune, e il muro da togliere sta
     * esattamente **su** quel bordo. Senza margine il confronto e' falso
     * per un pelo, il muro resta, e la sala si ritrova murata dal proprio
     * corridoio.
     *
     * Per il confronto basta il rettangolo d'ingombro: un arrotondato ci
     * sta comunque dentro.
     */
    fun dentro(f: Forma, x: Float, z: Float, margine: Float = MARGINE_CONFINE): Boolean =
        x > f.x - margine && x < f.x + f.w + margine &&
        z > f.z - margine && z < f.z + f.d + margine

    /** Quanto conta «essere sul bordo» di una forma vicina. */
    const val MARGINE_CONFINE = 0.03f

    /**
     * I varchi: dove il muro non c'e'. Uno per ogni connettore, a cavallo
     * del bordo della casella.
     */
    fun varchi(m: Modulo): List<Riquadro> = m.connettori.map { varco(it) }

    fun varco(k: Connettore): Riquadro {
        val x = k.x.toFloat(); val z = k.z.toFloat()
        val s = 0.3f      // quanto il varco sborda oltre il muro
        val l = 0.06f     // quanto lo si stringe ai lati, per non mangiare gli spigoli
        return when (k.lato) {
            dev.michelelops.arcaniaquest.regole.Lato.NORD ->
                Riquadro(x + l, z - s, x + 1f - l, z + s)
            dev.michelelops.arcaniaquest.regole.Lato.SUD ->
                Riquadro(x + l, z + 1f - s, x + 1f - l, z + 1f + s)
            dev.michelelops.arcaniaquest.regole.Lato.OVEST ->
                Riquadro(x - s, z + l, x + s, z + 1f - l)
            dev.michelelops.arcaniaquest.regole.Lato.EST ->
                Riquadro(x + 1f - s, z + l, x + 1f + s, z + 1f - l)
        }
    }
}
