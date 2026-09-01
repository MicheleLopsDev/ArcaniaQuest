package dev.michelelops.arcaniaquest.gioco

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import dev.michelelops.arcaniaquest.regole.Modulo

/**
 * Da un modulo alla sua mesh.
 *
 * Il dato e' la fonte: nessuna stanza viene disegnata a mano. Se cambia il
 * JSON cambia la geometria, e non c'e' un secondo posto da tenere
 * allineato.
 */
/**
 * Un'apertura nel muro di un modulo, come la vuole la mesh.
 *
 * [stretta] dice se e' un vano da porta o un passaggio largo quanto la
 * casella; [battente] se il legno va disegnato, cioe' se la porta e'
 * chiusa e a disegnarla tocca a questo pezzo e non al suo dirimpettaio.
 */
data class Apertura(
    val indice: Int,
    val stretta: Boolean,
    val battente: Boolean,
    /** Una porta aperta resta li', spalancata: non svanisce. */
    val spalancata: Boolean = false
)

object CostruttoreMesh {

    /**
     * Quante caselle copre una ripetizione della texture.
     *
     * Le immagini sono larghe il doppio di quanto sono alte, e i muri
     * sono alti una casella: due caselle in orizzontale per una in
     * verticale tengono le pietre nelle giuste proporzioni invece di
     * schiacciarle.
     */
    private const val PASSO_ORIZZONTALE = 2f
    private const val PASSO_VERTICALE = 1f

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
     *   i due mezzi muri combaciano al confine invece di infilarsi uno in
     *   casa dell'altro, e soprattutto le due facce non finiscono sullo
     *   stesso piano — cosa che le faceva litigare per la profondita', e a
     *   schermo faceva sparire il muro.
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

    /** Come si costruisce un modulo isolato: tutti i varchi aperti. */
    fun apertureLibere(m: Modulo): List<Apertura> =
        m.connettori.mapIndexed { i, k -> Apertura(i, k.porta, k.porta, spalancata = false) }

    /**
     * @param fuoriOccupato dice se la casella (in coordinate del modulo)
     *   appena fuori dal muro appartiene a un altro modulo. Serve a non
     *   costruire pietra dentro casa d'altri: due moduli affiancati
     *   condividono il confine, e ognuno ci mette solo la propria faccia.
     */
    fun costruisci(
        m: Modulo,
        materiali: Materiali,
        aperture: List<Apertura> = apertureLibere(m),
        fuoriOccupato: (Int, Int) -> Boolean = { _, _ -> false }
    ): Model {
        val c = Misure.CASELLA
        val h = Misure.ALTEZZA_MURO
        val t = Misure.SPESSORE_MURO

        val forme = Pianta.formeDi(m)
        // Solo i connettori che sono davvero un passaggio bucano il muro:
        // quelli rimasti liberi alla fine della generazione restano murati,
        // altrimenti il dungeon si affaccerebbe sul nulla.
        val varchi = aperture.map { Pianta.varco(m.connettori[it.indice], it.stretta) }

        // I contorni si calcolano una volta sola: servono a piu' passate.
        val contorni = forme.map { Pianta.contorno(it) }
        val tratti = mutableListOf<Tratto>()
        val stipiti = mutableListOf<Stipite>()

        for ((i, poly) in contorni.withIndex()) {
            val b = Pianta.baricentro(poly)
            // La texture non riparte a ogni segmento: si tiene il conto di
            // quanto muro si e' percorso, se no le pietre si spezzano ogni
            // venti centimetri.
            var percorso = 0f

            // Prima si decide segmento per segmento se il muro c'e'.
            val tenuto = BooleanArray(poly.size)
            val normali = Array(poly.size) { floatArrayOf(0f, 0f) }
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                val mx = (a[0] + d[0]) / 2f
                val mz = (a[1] + d[1]) / 2f

                var nx = d[1] - a[1]
                var nz = -(d[0] - a[0])
                val l = kotlin.math.hypot(nx, nz).takeIf { it > 1e-6f } ?: 1f
                nx /= l; nz /= l
                if ((mx - b[0]) * nx + (mz - b[1]) * nz < 0f) { nx = -nx; nz = -nz }
                normali[k] = floatArrayOf(nx, nz)

                // dove c'e' un connettore non c'e' muro; e nemmeno dove due
                // forme dello stesso modulo si toccano, che li' sarebbe un
                // divisorio dentro la stessa stanza
                val varco = varchi.any { it.contiene(mx, mz) }
                val confine = forme.withIndex().any { (j, g) -> j != i && Pianta.dentro(g, mx, mz) }
                tenuto[k] = !varco && !confine

                val lungo = kotlin.math.hypot(d[0] - a[0], d[1] - a[1])
                if (tenuto[k]) {
                    // la casella appena oltre il muro, in coordinate del modulo
                    val fx = kotlin.math.floor(mx + nx * 0.5f).toInt()
                    val fz = kotlin.math.floor(mz + nz * 0.5f).toInt()
                    val condiviso = fuoriOccupato(fx, fz)
                    tratti += Tratto(
                        a[0] * c, a[1] * c, d[0] * c, d[1] * c, nx, nz,
                        rientro = if (condiviso) t / 2f else 0f,
                        spessore = if (condiviso) t / 2f else t,
                        facciaEsterna = !condiviso,
                        u0 = percorso / PASSO_ORIZZONTALE,
                        u1 = (percorso + lungo) / PASSO_ORIZZONTALE
                    )
                }
                percorso += lungo
            }

            // Poi si chiudono i tagli. Dove il muro finisce, il suo spessore
            // resterebbe aperto: si vedrebbe il vuoto attraverso i settanta
            // centimetri fra faccia interna ed esterna. E' lo stipite, e da
            // dentro il gioco e' proprio la fessura nera accanto alle porte.
            for (k in poly.indices) {
                val succ = (k + 1) % poly.size
                if (tenuto[k] == tenuto[succ]) continue
                val v = poly[succ]
                val n = if (tenuto[k]) normali[k] else normali[succ]
                // lo stipite chiude la testa del muro, con lo spessore che
                // quel muro ha davvero li'
                val vivo = if (tenuto[k]) k else succ
                val a2 = poly[vivo]
                val b2 = poly[(vivo + 1) % poly.size]
                val fx = kotlin.math.floor((a2[0] + b2[0]) / 2f + n[0] * 0.5f).toInt()
                val fz = kotlin.math.floor((a2[1] + b2[1]) / 2f + n[1] * 0.5f).toInt()
                val condiviso = fuoriOccupato(fx, fz)
                stipiti += Stipite(
                    v[0] * c, v[1] * c, n[0], n[1],
                    rientro = if (condiviso) t / 2f else 0f,
                    spessore = if (condiviso) t / 2f else t
                )
            }
        }

        val mb = ModelBuilder()
        mb.begin()
        val attributi = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.Normal or
            VertexAttributes.Usage.TextureCoordinates).toLong()
        val su = Vector3(0f, 1f, 0f)

        // ATTENZIONE: ModelBuilder.part() chiude la parte precedente e
        // restituisce sempre lo stesso costruttore. Ogni parte va quindi
        // riempita per intero prima di aprire la successiva, altrimenti
        // finisce tutto nell'ultima aperta, col materiale sbagliato.

        // 1. pavimento
        val pav = mb.part("pavimento", GL20.GL_TRIANGLES, attributi, materiali.pavimento)
        for (poly in contorni) {
            val b = Pianta.baricentro(poly)
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                orizzontale(pav, b, a, d, 0f, su)
            }
        }

        // 2. soffitto: stesso poligono del pavimento, tre metri piu' su e
        //    rivolto in giu'. Senza, di un sotterraneo si vede il cielo.
        val sof = mb.part("soffitto", GL20.GL_TRIANGLES, attributi, materiali.volta)
        val giu = Vector3(0f, -1f, 0f)
        for (poly in contorni) {
            val b = Pianta.baricentro(poly)
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                orizzontale(sof, b, d, a, h, giu)
            }
        }

        // 3. facce dei muri
        val mur = mb.part("muri", GL20.GL_TRIANGLES, attributi, materiali.muro)
        val altoTex = h / c / PASSO_VERTICALE
        for (s in tratti) {
            val dentro = Vector3(-s.nx, 0f, -s.nz)
            val ix = s.ax - s.nx * s.rientro; val iz = s.az - s.nz * s.rientro
            val jx = s.bx - s.nx * s.rientro; val jz = s.bz - s.nz * s.rientro
            // la faccia interna c'e' sempre: e' quella che si vede da qui
            quadrato(mur,
                Vector3(ix, 0f, iz), Vector3(jx, 0f, jz),
                Vector3(jx, h, jz), Vector3(ix, h, iz), dentro,
                s.u0, s.u1, altoTex)
            if (!s.facciaEsterna) continue
            val ex = s.nx * s.spessore; val ez = s.nz * s.spessore
            quadrato(mur,
                Vector3(jx + ex, 0f, jz + ez), Vector3(ix + ex, 0f, iz + ez),
                Vector3(ix + ex, h, iz + ez), Vector3(jx + ex, h, jz + ez),
                Vector3(s.nx, 0f, s.nz),
                s.u1, s.u0, altoTex)
        }

        // 4. stipiti: la testa dei muri tagliati
        val sti = mb.part("stipiti", GL20.GL_TRIANGLES, attributi, materiali.muro)
        for (p in stipiti) {
            val bx = p.x - p.nx * p.rientro; val bz = p.z - p.nz * p.rientro
            val ex = p.nx * p.spessore; val ez = p.nz * p.spessore
            // normale lungo il muro: quale dei due versi conta poco,
            // le facce non si scartano
            val lungo = Vector3(-p.nz, 0f, p.nx)
            quadrato(sti,
                Vector3(bx, 0f, bz), Vector3(bx + ex, 0f, bz + ez),
                Vector3(bx + ex, h, bz + ez), Vector3(bx, h, bz), lungo,
                0f, p.spessore / c / PASSO_ORIZZONTALE, h / c / PASSO_VERTICALE)
        }

        // 5. cime dei muri, per quando si guardera' dall'alto
        val cim = mb.part("cime", GL20.GL_TRIANGLES, attributi, materiali.cima)
        for (s in tratti) {
            val ix = s.ax - s.nx * s.rientro; val iz = s.az - s.nz * s.rientro
            val jx = s.bx - s.nx * s.rientro; val jz = s.bz - s.nz * s.rientro
            val ex = s.nx * s.spessore; val ez = s.nz * s.spessore
            quadrato(cim,
                Vector3(ix, h, iz), Vector3(jx, h, jz),
                Vector3(jx + ex, h, jz + ez), Vector3(ix + ex, h, iz + ez), su,
                s.u0, s.u1, s.spessore / c / PASSO_VERTICALE)
        }

        // 6. architravi: il varco toglie il muro per tutta l'altezza, ma la
        //    porta e' alta due metri e mezzo. Senza architrave sopra ogni
        //    porta resta un buco che da' sul nulla.
        val arc = mb.part("architravi", GL20.GL_TRIANGLES, attributi, materiali.muro)
        for (a in aperture.filter { it.stretta }) {
            val k = m.connettori[a.indice]
            val v = Pianta.varco(k, true)
            val lungoX = k.lato == dev.michelelops.arcaniaquest.regole.Lato.NORD ||
                         k.lato == dev.michelelops.arcaniaquest.regole.Lato.SUD
            val (cx, cz) = centroPorta(k, v, c)
            val larg = (if (lungoX) LARGHEZZA_PORTA else SPESSORE_PORTA) * c
            val prof = (if (lungoX) SPESSORE_PORTA else LARGHEZZA_PORTA) * c
            val alto = h - ALTEZZA_PORTA
            // L'architrave e' la muratura che tappa il varco sopra la
            // porta. Spessa quanto il muro se di la' c'e' roccia; sottile
            // se di la' c'e' un altro modulo, perche' allora anche lui ne
            // disegna una e le due si affiancano come le facce del muro.
            val oltre = fuoriOccupato(k.x + k.lato.dx, k.z + k.lato.dz)
            val spesso = if (oltre) SPESSORE_AL_CONFINE else Misure.SPESSORE_MURO / c
            val largoVarco = Pianta.LARGO_PORTA * 2f + 0.06f
            val largArc = (if (lungoX) largoVarco else spesso) * c
            val profArc = (if (lungoX) spesso else largoVarco) * c
            BoxShapeBuilder.build(arc, cx, ALTEZZA_PORTA + alto / 2f, cz, largArc, alto, profArc)
        }

        // 7. porte: un battente nel varco, cosi' si vede che di la' non si passa
        val por = mb.part("porte", GL20.GL_TRIANGLES, attributi, materiali.legno)
        for (a in aperture.filter { it.battente }) {
            val k = m.connettori[a.indice]
            val sp = spessoreBattente(m, k, fuoriOccupato) * c
            BoxShapeBuilder.build(
                por,
                anta(k, c, a.spalancata, ALTEZZA_PORTA / 2f, LARGHEZZA_PORTA * c, ALTEZZA_PORTA, sp)
            )
        }

        // 8. le bande di ferro: due strisce bastano a far leggere il
        //    battente come una porta invece che come un pannello marrone
        val fer = mb.part("ferramenta", GL20.GL_TRIANGLES, attributi, materiali.ferro)
        for (a in aperture.filter { it.battente }) {
            val k = m.connettori[a.indice]
            val sp = (spessoreBattente(m, k, fuoriOccupato) + 0.02f) * c
            for (y in floatArrayOf(ALTEZZA_PORTA * 0.24f, ALTEZZA_PORTA * 0.76f)) {
                BoxShapeBuilder.build(
                    fer,
                    anta(k, c, a.spalancata, y, LARGHEZZA_PORTA * 0.9f * c, 0.16f, sp)
                )
            }
        }

        return mb.end()
    }

    /**
     * Il battente e' un filo piu' largo del varco, non piu' stretto: se
     * fosse piu' stretto resterebbero due fessure ai lati da cui si vede
     * il vuoto.
     */
    private const val LARGHEZZA_PORTA = Pianta.LARGO_PORTA * 2f + 0.04f
    private const val SPESSORE_PORTA = 0.18f

    /**
     * Lo spessore del battente quando sta sul confine fra due moduli.
     *
     * Li' il muro non ha spessore — i due moduli condividono la faccia —
     * e una porta da mezzo metro sporge un quarto di metro dentro ognuno
     * dei due corridoi. Vista da vicino sembra un pannello piantato di
     * traverso: e' il difetto che si nota per primo camminando.
     */
    private const val SPESSORE_AL_CONFINE = 0.05f
    private const val ALTEZZA_PORTA = 2.1f

    /**
     * Il centro del battente: **sul filo del confine** fra le due caselle.
     *
     * Prima lo spostavo di mezzo spessore di muro verso fuori, per
     * infilarlo nella pietra. Funzionava finche' due moduli affiancati
     * avevano ognuno il suo muro: il battente spariva dentro il doppio
     * spessore. Da quando il confine e' condiviso e li' il muro non ha
     * piu' spessore, quello scarto piantava la porta dentro il corridoio
     * del vicino, di traverso.
     *
     * Sul confine invece sta bene in tutti e due i casi: se di la' c'e'
     * roccia sporge per meta' nel muro, come una porta vera; se di la'
     * c'e' un altro modulo, sporge di un quarto di metro per parte.
     */
    /**
     * Quanto e' spessa una porta: quanto il muro che la ospita.
     *
     * Sul confine fra due moduli il muro non ha spessore, e li' il
     * battente dev'essere una lastra sottile. Dove invece di la' c'e'
     * roccia, il muro e' pieno e la porta ci sta dentro comoda.
     */
    private fun spessoreBattente(
        m: Modulo,
        k: dev.michelelops.arcaniaquest.regole.Connettore,
        fuoriOccupato: (Int, Int) -> Boolean
    ): Float =
        if (fuoriOccupato(k.x + k.lato.dx, k.z + k.lato.dz)) SPESSORE_AL_CONFINE else SPESSORE_PORTA

    /**
     * La trasformazione di un'anta, chiusa o spalancata.
     *
     * Una porta aperta non sparisce: gira sui cardini e resta appoggiata
     * al muro di fianco. Il cardine sta a un capo del varco, e l'anta si
     * apre **verso il modulo che la disegna** — una scelta arbitraria ma
     * che ha il pregio di non mandarla mai dentro la stanza del vicino.
     *
     * Non si apre di novanta gradi tondi ma di ottantadue: a novanta
     * finirebbe complanare al muro laterale e le due superfici si
     * darebbero battaglia sullo stesso piano.
     */
    private fun anta(
        k: dev.michelelops.arcaniaquest.regole.Connettore,
        c: Float,
        spalancata: Boolean,
        altezzaCentro: Float,
        larga: Float,
        alta: Float,
        spessa: Float
    ): Matrix4 {
        val v = Pianta.varco(k, true)
        val (cx, cz) = centroPorta(k, v, c)

        // il muro corre perpendicolare al lato del connettore
        val lungoX = k.lato == dev.michelelops.arcaniaquest.regole.Lato.NORD ||
                     k.lato == dev.michelelops.arcaniaquest.regole.Lato.SUD
        val mx = if (lungoX) 1f else 0f
        val mz = if (lungoX) 0f else 1f
        // verso fuori dal modulo, cioe' dove l'anta non deve andare
        val nx = k.lato.dx.toFloat()
        val nz = k.lato.dz.toFloat()

        val cardineX = cx + mx * larga / 2f
        val cardineZ = cz + mz * larga / 2f

        // chiusa, l'anta va dal cardine verso l'altro capo del varco
        val chiusa = gradiDi(-mx, -mz)
        if (!spalancata) return matriceAnta(cardineX, altezzaCentro, cardineZ, chiusa, larga, alta, spessa)

        // aperta, deve finire dalla parte opposta a fuori: fra i due versi
        // di rotazione si sceglie quello che ci avvicina
        val dentro = gradiDi(-nx, -nz)
        val piu = differenza(chiusa + APERTURA, dentro)
        val meno = differenza(chiusa - APERTURA, dentro)
        val gradi = if (piu <= meno) chiusa + APERTURA else chiusa - APERTURA
        return matriceAnta(cardineX, altezzaCentro, cardineZ, gradi, larga, alta, spessa)
    }

    /** Quanto e' spalancata un'anta aperta, in gradi. */
    private const val APERTURA = 82f

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
        Math.toDegrees(kotlin.math.atan2(-dz.toDouble(), dx.toDouble())).toFloat()

    /** Quanto distano due angoli, senza farsi ingannare dal giro completo. */
    private fun differenza(a: Float, b: Float): Float {
        var d = kotlin.math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    fun centroPorta(
        k: dev.michelelops.arcaniaquest.regole.Connettore,
        v: Riquadro,
        c: Float
    ): Pair<Float, Float> =
        ((v.x0 + v.x1) / 2f * c) to ((v.z0 + v.z1) / 2f * c)

    /**
     * Niente scarto delle facce di dietro.
     *
     * Il gruppo sta sempre dentro il dungeon, quindi il risparmio sarebbe
     * minimo; in cambio si toglie di mezzo l'errore piu' insidioso di
     * tutti, quello di un triangolo avvolto al contrario che sparisce
     * senza dire niente.
     */
    private fun vertice(p: Vector3, n: Vector3, u: Float, v: Float) =
        MeshPartBuilder.VertexInfo().setPos(p).setNor(n).setUV(u, v)

    /**
     * Un triangolo di pavimento o di soffitto: la texture segue le
     * coordinate del piano, cosi' due caselle vicine continuano il
     * disegno invece di ricominciarlo da capo.
     */
    private fun orizzontale(
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
