package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import dev.michelelops.arcaniaquest.regole.Connettore
import dev.michelelops.arcaniaquest.regole.Forma
import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Modulo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Un'apertura nel muro di un modulo, come la vuole la mesh.
 *
 * @param indice quale connettore del modulo.
 * @param stretta vano da porta se vero, passaggio largo quanto la casella se falso.
 * @param battente se il legno va disegnato, cioe' se a disegnarlo tocca a
 *   questo pezzo e non al suo dirimpettaio.
 * @param spalancata se l'anta va girata sui cardini invece che chiusa nel vano.
 */
data class Apertura(
    val indice: Int,
    val stretta: Boolean,
    val battente: Boolean,
    val spalancata: Boolean = false
)

/**
 * Da un modulo alla sua mesh.
 *
 * Il dato e' la fonte: nessuna stanza viene disegnata a mano. Se cambia il
 * JSON cambia la geometria, e non c'e' un secondo posto da tenere
 * allineato.
 *
 * La mesh esce divisa in parti, una per materiale: pavimento, soffitto,
 * muri, stipiti, cime, architravi, battenti, ferramenta.
 */
object CostruttoreMesh {

    /**
     * Quante caselle copre una ripetizione della texture.
     *
     * Le immagini sono larghe il doppio di quanto sono alte e i muri sono
     * alti una casella: due caselle in orizzontale per una in verticale
     * tengono le pietre nelle giuste proporzioni invece di schiacciarle.
     */
    private const val PASSO_ORIZZONTALE = 2f
    private const val PASSO_VERTICALE = 1f

    /** Il battente e' un filo piu' largo del varco, se no restano fessure ai lati. */
    private const val LARGHEZZA_PORTA = Pianta.LARGO_PORTA * 2f + 0.04f
    private const val SPESSORE_PORTA = 0.18f

    /**
     * Lo spessore del battente sul confine fra due moduli, dove il muro e'
     * condiviso e quindi sottile. Con lo spessore pieno la porta sporgerebbe
     * dentro tutti e due i corridoi.
     */
    private const val SPESSORE_AL_CONFINE = 0.05f

    private const val ALTEZZA_PORTA = 2.1f

    /**
     * L'altezza di una banda di ferro.
     *
     * Non e' un capriccio: sotto i venti centimetri la fascia occupa una
     * ventina di pixel a schermo, e in venti pixel un ferro lavorato
     * diventa poltiglia o rumore a seconda del filtro.
     */
    private const val ALTEZZA_BANDA = 0.30f

    /**
     * Di quanto si spalanca un'anta aperta.
     *
     * Non novanta gradi tondi: a novanta finirebbe complanare al muro di
     * fianco, e due superfici sullo stesso piano litigano per la
     * profondita' sfarfallando.
     */
    private const val APERTURA_ANTA = 82f

    /** Tutti i varchi aperti: come si costruisce un modulo isolato. */
    fun apertureLibere(m: Modulo): List<Apertura> =
        m.connettori.mapIndexed { i, k -> Apertura(i, k.porta, k.porta) }

    /**
     * @param materiali le pietre e i legni, condivisi fra tutti i moduli.
     * @param aperture quali connettori bucano davvero il muro. Quelli
     *   rimasti liberi alla generazione restano murati, se no il dungeon
     *   si affaccia sul nulla.
     * @param fuoriOccupato dice se la casella appena oltre il muro (in
     *   coordinate del modulo) appartiene a un altro modulo. Serve a non
     *   costruire pietra dentro casa d'altri.
     */
    fun costruisci(
        m: Modulo,
        materiali: Materiali,
        aperture: List<Apertura> = apertureLibere(m),
        fuoriOccupato: (Int, Int) -> Boolean = { _, _ -> false }
    ): Model {
        val contorni = Pianta.formeDi(m).map { Pianta.contorno(it) }
        val muratura = misuraMuratura(m, aperture, contorni, fuoriOccupato)

        val mb = ModelBuilder()
        mb.begin()

        // ModelBuilder.part() chiude la parte precedente e restituisce
        // sempre lo stesso costruttore: ogni parte va riempita per intero
        // prima di aprire la successiva, se no finisce tutto nell'ultima
        // aperta col materiale sbagliato.
        pavimento(parte(mb, "pavimento", materiali.pavimento), contorni)
        soffitto(parte(mb, "soffitto", materiali.volta), contorni)
        facceDeiMuri(parte(mb, "muri", materiali.muro), muratura.tratti)
        stipiti(parte(mb, "stipiti", materiali.muro), muratura.stipiti)
        cimeDeiMuri(parte(mb, "cime", materiali.cima), muratura.tratti)
        architravi(parte(mb, "architravi", materiali.architrave), m, aperture, fuoriOccupato)
        battenti(parte(mb, "porte", materiali.legno), m, aperture, fuoriOccupato)
        bandeDiFerro(parte(mb, "ferramenta", materiali.ferro), m, aperture, fuoriOccupato)

        return mb.end()
    }

    /** Il centro di un battente, sul filo del confine fra le due caselle. */
    fun centroPorta(k: Connettore, v: Riquadro, c: Float): Pair<Float, Float> =
        ((v.x0 + v.x1) / 2f * c) to ((v.z0 + v.z1) / 2f * c)

    // ------------------------------------------------------------------
    // misura: dal contorno ai pezzi di muro
    // ------------------------------------------------------------------

    /**
     * Un tratto di muro gia' misurato.
     *
     * [rientro] e' di quanto la faccia interna sta indietro rispetto al
     * confine, [spessore] quanta pietra ci va sopra. Non sono sempre gli
     * stessi, ed e' tutta la questione dei muri condivisi:
     *
     * - se di la' c'e' roccia, il muro parte dal confine e si estrude in
     *   fuori per intero;
     * - se di la' comincia un altro modulo, il muro vale per due: ognuno
     *   ne fa mezzo, arretrando la propria faccia di mezzo spessore. Cosi'
     *   i due mezzi muri combaciano invece di infilarsi uno in casa
     *   dell'altro, e le due facce non finiscono sullo stesso piano.
     */
    private class Tratto(
        val ax: Float, val az: Float,
        val bx: Float, val bz: Float,
        val nx: Float, val nz: Float,
        val rientro: Float,
        val spessore: Float,
        val facciaEsterna: Boolean,
        /** Quanto muro si e' percorso fin qui: e' la u della texture. */
        val u0: Float, val u1: Float
    )

    /** Il taglio in testa a un muro: chiude lo spessore dove il muro finisce. */
    private class Stipite(
        val x: Float, val z: Float,
        val nx: Float, val nz: Float,
        val rientro: Float, val spessore: Float
    )

    private class Muratura(val tratti: List<Tratto>, val stipiti: List<Stipite>)

    private fun misuraMuratura(
        m: Modulo,
        aperture: List<Apertura>,
        contorni: List<List<FloatArray>>,
        fuoriOccupato: (Int, Int) -> Boolean
    ): Muratura {
        val forme = Pianta.formeDi(m)
        val varchi = aperture.map { Pianta.varco(m.connettori[it.indice], it.stretta) }
        val tratti = mutableListOf<Tratto>()
        val stipiti = mutableListOf<Stipite>()

        for ((i, poly) in contorni.withIndex()) {
            val baricentro = Pianta.baricentro(poly)
            val normali = Array(poly.size) { normaleUscente(poly, it, baricentro) }
            val tenuto = BooleanArray(poly.size) { k ->
                muroQui(poly, k, forme, i, varchi)
            }

            // La u non riparte a ogni segmento: si tiene il conto di quanto
            // muro si e' percorso, se no le pietre si spezzano ogni venti
            // centimetri.
            var percorso = 0f
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                val lungo = hypot(d[0] - a[0], d[1] - a[1])
                if (tenuto[k]) {
                    tratti += tratto(a, d, normali[k], percorso, lungo, fuoriOccupato)
                }
                percorso += lungo
            }

            stipiti += stipitiDi(poly, tenuto, normali, fuoriOccupato)
        }
        return Muratura(tratti, stipiti)
    }

    /** La normale del segmento [k], rivolta fuori dal poligono. */
    private fun normaleUscente(poly: List<FloatArray>, k: Int, baricentro: FloatArray): FloatArray {
        val a = poly[k]
        val d = poly[(k + 1) % poly.size]
        var nx = d[1] - a[1]
        var nz = -(d[0] - a[0])
        val l = hypot(nx, nz).takeIf { it > 1e-6f } ?: 1f
        nx /= l; nz /= l
        val mx = (a[0] + d[0]) / 2f
        val mz = (a[1] + d[1]) / 2f
        return if ((mx - baricentro[0]) * nx + (mz - baricentro[1]) * nz < 0f) {
            floatArrayOf(-nx, -nz)
        } else {
            floatArrayOf(nx, nz)
        }
    }

    /**
     * Se su questo segmento il muro c'e'.
     *
     * Non c'e' dove passa un connettore, e nemmeno dove due forme dello
     * stesso modulo si toccano: li' sarebbe un divisorio in mezzo alla
     * stessa stanza.
     */
    private fun muroQui(
        poly: List<FloatArray>,
        k: Int,
        forme: List<Forma>,
        formaCorrente: Int,
        varchi: List<Riquadro>
    ): Boolean {
        val a = poly[k]
        val d = poly[(k + 1) % poly.size]
        val mx = (a[0] + d[0]) / 2f
        val mz = (a[1] + d[1]) / 2f
        if (varchi.any { it.contiene(mx, mz) }) return false
        return forme.withIndex().none { (j, g) -> j != formaCorrente && Pianta.dentro(g, mx, mz) }
    }

    private fun tratto(
        a: FloatArray,
        d: FloatArray,
        n: FloatArray,
        percorso: Float,
        lungo: Float,
        fuoriOccupato: (Int, Int) -> Boolean
    ): Tratto {
        val c = Misure.CASELLA
        val t = Misure.SPESSORE_MURO
        val condiviso = fuoriOccupato(
            floor((a[0] + d[0]) / 2f + n[0] * 0.5f).toInt(),
            floor((a[1] + d[1]) / 2f + n[1] * 0.5f).toInt()
        )
        return Tratto(
            a[0] * c, a[1] * c, d[0] * c, d[1] * c, n[0], n[1],
            rientro = if (condiviso) t / 2f else 0f,
            spessore = if (condiviso) t / 2f else t,
            facciaEsterna = !condiviso,
            u0 = percorso / PASSO_ORIZZONTALE,
            u1 = (percorso + lungo) / PASSO_ORIZZONTALE
        )
    }

    /**
     * Dove un muro finisce, il suo spessore resterebbe aperto e si
     * vedrebbe il vuoto fra la faccia interna e quella esterna. Lo stipite
     * chiude quella testa.
     */
    private fun stipitiDi(
        poly: List<FloatArray>,
        tenuto: BooleanArray,
        normali: Array<FloatArray>,
        fuoriOccupato: (Int, Int) -> Boolean
    ): List<Stipite> {
        val c = Misure.CASELLA
        val t = Misure.SPESSORE_MURO
        val fuori = mutableListOf<Stipite>()
        for (k in poly.indices) {
            val succ = (k + 1) % poly.size
            if (tenuto[k] == tenuto[succ]) continue

            val vivo = if (tenuto[k]) k else succ
            val n = normali[vivo]
            val a = poly[vivo]
            val b = poly[(vivo + 1) % poly.size]
            val condiviso = fuoriOccupato(
                floor((a[0] + b[0]) / 2f + n[0] * 0.5f).toInt(),
                floor((a[1] + b[1]) / 2f + n[1] * 0.5f).toInt()
            )
            val v = poly[succ]
            fuori += Stipite(
                v[0] * c, v[1] * c, n[0], n[1],
                rientro = if (condiviso) t / 2f else 0f,
                spessore = if (condiviso) t / 2f else t
            )
        }
        return fuori
    }

    // ------------------------------------------------------------------
    // costruzione, una parte per materiale
    // ------------------------------------------------------------------

    private val ATTRIBUTI = (VertexAttributes.Usage.Position or
        VertexAttributes.Usage.Normal or
        VertexAttributes.Usage.TextureCoordinates).toLong()

    private val SU = Vector3(0f, 1f, 0f)
    private val GIU = Vector3(0f, -1f, 0f)

    private fun parte(mb: ModelBuilder, nome: String, materiale: Material) =
        mb.part(nome, GL20.GL_TRIANGLES, ATTRIBUTI, materiale)

    private fun pavimento(b: MeshPartBuilder, contorni: List<List<FloatArray>>) =
        ventaglio(b, contorni, 0f, SU, rovescio = false)

    /** Senza soffitto, di un sotterraneo si vede il cielo. */
    private fun soffitto(b: MeshPartBuilder, contorni: List<List<FloatArray>>) =
        ventaglio(b, contorni, Misure.ALTEZZA_MURO, GIU, rovescio = true)

    /** Un piano orizzontale, triangolato a ventaglio dal baricentro. */
    private fun ventaglio(
        b: MeshPartBuilder,
        contorni: List<List<FloatArray>>,
        y: Float,
        n: Vector3,
        rovescio: Boolean
    ) {
        for (poly in contorni) {
            val centro = Pianta.baricentro(poly)
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                if (rovescio) triangoloPiano(b, centro, d, a, y, n)
                else triangoloPiano(b, centro, a, d, y, n)
            }
        }
    }

    private fun facceDeiMuri(b: MeshPartBuilder, tratti: List<Tratto>) {
        val h = Misure.ALTEZZA_MURO
        val altoTex = h / Misure.CASELLA / PASSO_VERTICALE
        for (s in tratti) {
            val (ix, iz, jx, jz) = filoInterno(s)
            // la faccia interna c'e' sempre: e' quella che si vede da qui
            quadrato(
                b,
                Vector3(ix, 0f, iz), Vector3(jx, 0f, jz),
                Vector3(jx, h, jz), Vector3(ix, h, iz),
                Vector3(-s.nx, 0f, -s.nz), s.u0, s.u1, altoTex
            )
            if (!s.facciaEsterna) continue
            val ex = s.nx * s.spessore
            val ez = s.nz * s.spessore
            quadrato(
                b,
                Vector3(jx + ex, 0f, jz + ez), Vector3(ix + ex, 0f, iz + ez),
                Vector3(ix + ex, h, iz + ez), Vector3(jx + ex, h, jz + ez),
                Vector3(s.nx, 0f, s.nz), s.u1, s.u0, altoTex
            )
        }
    }

    private fun stipiti(b: MeshPartBuilder, stipiti: List<Stipite>) {
        val h = Misure.ALTEZZA_MURO
        val c = Misure.CASELLA
        for (p in stipiti) {
            val bx = p.x - p.nx * p.rientro
            val bz = p.z - p.nz * p.rientro
            val ex = p.nx * p.spessore
            val ez = p.nz * p.spessore
            // normale lungo il muro: quale dei due versi conta poco, le
            // facce non si scartano
            quadrato(
                b,
                Vector3(bx, 0f, bz), Vector3(bx + ex, 0f, bz + ez),
                Vector3(bx + ex, h, bz + ez), Vector3(bx, h, bz),
                Vector3(-p.nz, 0f, p.nx),
                0f, p.spessore / c / PASSO_ORIZZONTALE, h / c / PASSO_VERTICALE
            )
        }
    }

    /** Si vedono solo dall'alto, ma senza il muro sembra tagliato con la lama. */
    private fun cimeDeiMuri(b: MeshPartBuilder, tratti: List<Tratto>) {
        val h = Misure.ALTEZZA_MURO
        val c = Misure.CASELLA
        for (s in tratti) {
            val (ix, iz, jx, jz) = filoInterno(s)
            val ex = s.nx * s.spessore
            val ez = s.nz * s.spessore
            quadrato(
                b,
                Vector3(ix, h, iz), Vector3(jx, h, jz),
                Vector3(jx + ex, h, jz + ez), Vector3(ix + ex, h, iz + ez),
                SU, s.u0, s.u1, s.spessore / c / PASSO_VERTICALE
            )
        }
    }

    /** I due estremi della faccia interna, gia' arretrati del rientro. */
    private data class Filo(val ix: Float, val iz: Float, val jx: Float, val jz: Float)

    private fun filoInterno(s: Tratto) = Filo(
        s.ax - s.nx * s.rientro, s.az - s.nz * s.rientro,
        s.bx - s.nx * s.rientro, s.bz - s.nz * s.rientro
    )

    /**
     * Il varco toglie il muro per tutta l'altezza, ma la porta e' alta due
     * metri e dieci: senza architrave sopra ogni porta resta un buco.
     */
    private fun architravi(
        b: MeshPartBuilder,
        m: Modulo,
        aperture: List<Apertura>,
        fuoriOccupato: (Int, Int) -> Boolean
    ) {
        val c = Misure.CASELLA
        val alto = Misure.ALTEZZA_MURO - ALTEZZA_PORTA
        for (a in aperture.filter { it.stretta }) {
            val k = m.connettori[a.indice]
            val (cx, cz) = centroPorta(k, Pianta.varco(k, true), c)
            // spessa quanto il muro se di la' c'e' roccia; sottile se di la'
            // c'e' un altro modulo, che allora ne disegna una sua
            val spesso = if (fuoriOccupato(k.x + k.lato.dx, k.z + k.lato.dz)) SPESSORE_AL_CONFINE
                         else Misure.SPESSORE_MURO / c
            val largoVarco = Pianta.LARGO_PORTA * 2f + 0.06f
            val lungoX = lungoX(k)
            BoxShapeBuilder.build(
                b, cx, ALTEZZA_PORTA + alto / 2f, cz,
                (if (lungoX) largoVarco else spesso) * c,
                alto,
                (if (lungoX) spesso else largoVarco) * c
            )
        }
    }

    private fun battenti(
        b: MeshPartBuilder,
        m: Modulo,
        aperture: List<Apertura>,
        fuoriOccupato: (Int, Int) -> Boolean
    ) {
        val c = Misure.CASELLA
        for (a in aperture.filter { it.battente }) {
            val k = m.connettori[a.indice]
            BoxShapeBuilder.build(
                b,
                anta(
                    k, c, a.spalancata,
                    altezzaCentro = ALTEZZA_PORTA / 2f,
                    larga = LARGHEZZA_PORTA * c,
                    alta = ALTEZZA_PORTA,
                    spessa = spessoreBattente(k, fuoriOccupato) * c
                )
            )
        }
    }

    /** Due strisce bastano a far leggere il battente come una porta. */
    private fun bandeDiFerro(
        b: MeshPartBuilder,
        m: Modulo,
        aperture: List<Apertura>,
        fuoriOccupato: (Int, Int) -> Boolean
    ) {
        val c = Misure.CASELLA
        for (a in aperture.filter { it.battente }) {
            val k = m.connettori[a.indice]
            val spessa = (spessoreBattente(k, fuoriOccupato) + 0.02f) * c
            for (y in floatArrayOf(ALTEZZA_PORTA * 0.24f, ALTEZZA_PORTA * 0.76f)) {
                BoxShapeBuilder.build(
                    b,
                    anta(
                        k, c, a.spalancata,
                        altezzaCentro = y,
                        larga = LARGHEZZA_PORTA * 0.9f * c,
                        alta = ALTEZZA_BANDA,
                        spessa = spessa
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // l'anta sui cardini
    // ------------------------------------------------------------------

    /** Il muro corre perpendicolare al lato del connettore. */
    private fun lungoX(k: Connettore) = k.lato == Lato.NORD || k.lato == Lato.SUD

    /**
     * Una porta e' spessa quanto il muro che la ospita: piena contro
     * roccia, una lastra sottile sul confine fra due moduli.
     */
    private fun spessoreBattente(k: Connettore, fuoriOccupato: (Int, Int) -> Boolean): Float =
        if (fuoriOccupato(k.x + k.lato.dx, k.z + k.lato.dz)) SPESSORE_AL_CONFINE else SPESSORE_PORTA

    /**
     * Dove va messa un'anta, chiusa o spalancata.
     *
     * Il cardine sta a un capo del varco. Aperta, l'anta gira **verso il
     * modulo che la disegna**: scelta arbitraria, ma cosi' non finisce mai
     * dentro la stanza del vicino.
     */
    private fun anta(
        k: Connettore,
        c: Float,
        spalancata: Boolean,
        altezzaCentro: Float,
        larga: Float,
        alta: Float,
        spessa: Float
    ): Matrix4 {
        val (cx, cz) = centroPorta(k, Pianta.varco(k, true), c)
        val lungoX = lungoX(k)
        val mx = if (lungoX) 1f else 0f
        val mz = if (lungoX) 0f else 1f

        val cardineX = cx + mx * larga / 2f
        val cardineZ = cz + mz * larga / 2f

        // chiusa, l'anta va dal cardine verso l'altro capo del varco
        val chiusa = gradiDi(-mx, -mz)
        val gradi = if (!spalancata) chiusa else versoDentro(chiusa, k)
        return matriceAnta(cardineX, altezzaCentro, cardineZ, gradi, larga, alta, spessa)
    }

    /**
     * Fra i due versi di rotazione sceglie quello che porta l'anta dentro
     * il modulo, cioe' dalla parte opposta a dove punta il connettore.
     */
    private fun versoDentro(chiusa: Float, k: Connettore): Float {
        val dentro = gradiDi(-k.lato.dx.toFloat(), -k.lato.dz.toFloat())
        val piu = differenzaAngoli(chiusa + APERTURA_ANTA, dentro)
        val meno = differenzaAngoli(chiusa - APERTURA_ANTA, dentro)
        return if (piu <= meno) chiusa + APERTURA_ANTA else chiusa - APERTURA_ANTA
    }

    private fun matriceAnta(
        cardineX: Float, cardineY: Float, cardineZ: Float,
        gradi: Float, larga: Float, alta: Float, spessa: Float
    ): Matrix4 = Matrix4()
        .setToTranslation(cardineX, cardineY, cardineZ)
        .rotate(Vector3.Y, gradi)
        // il cubo di BoxShapeBuilder e' centrato: lo si porta a meta' anta
        .translate(larga / 2f, 0f, 0f)
        .scale(larga, alta, spessa)

    /**
     * I gradi di rotazione attorno a Y che portano l'asse X su (dx, dz).
     * Ruotando attorno a Y l'asse X va in (cos, 0, -sin): di qui il segno.
     */
    private fun gradiDi(dx: Float, dz: Float): Float =
        Math.toDegrees(atan2(-dz.toDouble(), dx.toDouble())).toFloat()

    /** Quanto distano due angoli, senza farsi ingannare dal giro completo. */
    private fun differenzaAngoli(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    // ------------------------------------------------------------------
    // pennelli
    // ------------------------------------------------------------------

    private fun vertice(p: Vector3, n: Vector3, u: Float, v: Float) =
        MeshPartBuilder.VertexInfo().setPos(p).setNor(n).setUV(u, v)

    /**
     * Un triangolo di pavimento o soffitto. La texture segue le coordinate
     * del piano, cosi' due caselle vicine continuano il disegno invece di
     * ricominciarlo da capo.
     */
    private fun triangoloPiano(
        b: MeshPartBuilder,
        centro: FloatArray, a: FloatArray, d: FloatArray,
        y: Float, n: Vector3
    ) {
        val c = Misure.CASELLA
        fun v(p: FloatArray) = vertice(
            Vector3(p[0] * c, y, p[1] * c), n,
            p[0] / PASSO_ORIZZONTALE, p[1] / PASSO_VERTICALE
        )
        b.triangle(v(centro), v(a), v(d))
    }

    private fun quadrato(
        b: MeshPartBuilder,
        p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, n: Vector3,
        u0: Float = 0f, u1: Float = 1f, v: Float = 1f
    ) {
        b.rect(
            vertice(p0, n, u0, v), vertice(p1, n, u1, v),
            vertice(p2, n, u1, 0f), vertice(p3, n, u0, 0f)
        )
    }
}
