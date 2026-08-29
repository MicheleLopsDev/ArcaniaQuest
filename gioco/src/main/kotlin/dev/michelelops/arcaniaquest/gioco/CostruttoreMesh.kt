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
import com.badlogic.gdx.math.Vector3
import dev.michelelops.arcaniaquest.regole.Modulo

/**
 * Da un modulo alla sua mesh.
 *
 * Il dato e' la fonte: nessuna stanza viene disegnata a mano. Se cambia il
 * JSON cambia la geometria, e non c'e' un secondo posto da tenere
 * allineato.
 */
object CostruttoreMesh {

    private val PIETRA = Color(0.52f, 0.51f, 0.47f, 1f)
    private val PIETRA_MURO = Color(0.60f, 0.59f, 0.55f, 1f)
    private val PIETRA_CIMA = Color(0.30f, 0.30f, 0.29f, 1f)
    private val PIETRA_VOLTA = Color(0.38f, 0.37f, 0.35f, 1f)
    private val LEGNO = Color(0.40f, 0.25f, 0.15f, 1f)

    /** Un tratto di muro gia' misurato: due estremi e la normale in fuori. */
    private class Tratto(
        val ax: Float, val az: Float,
        val bx: Float, val bz: Float,
        val nx: Float, val nz: Float
    )

    fun costruisci(m: Modulo): Model {
        val c = Misure.CASELLA
        val h = Misure.ALTEZZA_MURO
        val t = Misure.SPESSORE_MURO

        val forme = Pianta.formeDi(m)
        val varchi = Pianta.varchi(m)

        // I contorni si calcolano una volta sola: servono a piu' passate.
        val contorni = forme.map { Pianta.contorno(it) }
        val tratti = mutableListOf<Tratto>()
        for ((i, poly) in contorni.withIndex()) {
            val b = Pianta.baricentro(poly)
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                val mx = (a[0] + d[0]) / 2f
                val mz = (a[1] + d[1]) / 2f

                // dove c'e' un connettore non c'e' muro
                if (varchi.any { it.contiene(mx, mz) }) continue
                // e nemmeno dove due forme dello stesso modulo si toccano:
                // li' sarebbe un divisorio dentro la stessa stanza
                if (forme.withIndex().any { (j, g) -> j != i && Pianta.dentro(g, mx, mz) }) continue

                var nx = d[1] - a[1]
                var nz = -(d[0] - a[0])
                val l = kotlin.math.hypot(nx, nz).takeIf { it > 1e-6f } ?: 1f
                nx /= l; nz /= l
                if ((mx - b[0]) * nx + (mz - b[1]) * nz < 0f) { nx = -nx; nz = -nz }

                tratti += Tratto(a[0] * c, a[1] * c, d[0] * c, d[1] * c, nx, nz)
            }
        }

        val mb = ModelBuilder()
        mb.begin()
        val attributi = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        val su = Vector3(0f, 1f, 0f)

        // ATTENZIONE: ModelBuilder.part() chiude la parte precedente e
        // restituisce sempre lo stesso costruttore. Ogni parte va quindi
        // riempita per intero prima di aprire la successiva, altrimenti
        // finisce tutto nell'ultima aperta, col materiale sbagliato.

        // 1. pavimento
        val pav = mb.part("pavimento", GL20.GL_TRIANGLES, attributi, materiale(PIETRA))
        for (poly in contorni) {
            val b = Pianta.baricentro(poly)
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                triangolo(pav,
                    Vector3(b[0] * c, 0f, b[1] * c),
                    Vector3(a[0] * c, 0f, a[1] * c),
                    Vector3(d[0] * c, 0f, d[1] * c),
                    su)
            }
        }

        // 2. soffitto: stesso poligono del pavimento, tre metri piu' su e
        //    rivolto in giu'. Senza, di un sotterraneo si vede il cielo.
        val sof = mb.part("soffitto", GL20.GL_TRIANGLES, attributi, materiale(PIETRA_VOLTA))
        val giu = Vector3(0f, -1f, 0f)
        for (poly in contorni) {
            val b = Pianta.baricentro(poly)
            for (k in poly.indices) {
                val a = poly[k]
                val d = poly[(k + 1) % poly.size]
                triangolo(sof,
                    Vector3(b[0] * c, h, b[1] * c),
                    Vector3(d[0] * c, h, d[1] * c),
                    Vector3(a[0] * c, h, a[1] * c),
                    giu)
            }
        }

        // 3. facce dei muri
        val mur = mb.part("muri", GL20.GL_TRIANGLES, attributi, materiale(PIETRA_MURO))
        for (s in tratti) {
            val ex = s.nx * t; val ez = s.nz * t
            val dentro = Vector3(-s.nx, 0f, -s.nz)
            quadrato(mur,
                Vector3(s.ax, 0f, s.az), Vector3(s.bx, 0f, s.bz),
                Vector3(s.bx, h, s.bz), Vector3(s.ax, h, s.az), dentro)
            quadrato(mur,
                Vector3(s.bx + ex, 0f, s.bz + ez), Vector3(s.ax + ex, 0f, s.az + ez),
                Vector3(s.ax + ex, h, s.az + ez), Vector3(s.bx + ex, h, s.bz + ez),
                Vector3(s.nx, 0f, s.nz))
        }

        // 4. cime dei muri, per quando si guardera' dall'alto
        val cim = mb.part("cime", GL20.GL_TRIANGLES, attributi, materiale(PIETRA_CIMA))
        for (s in tratti) {
            val ex = s.nx * t; val ez = s.nz * t
            quadrato(cim,
                Vector3(s.ax, h, s.az), Vector3(s.bx, h, s.bz),
                Vector3(s.bx + ex, h, s.bz + ez), Vector3(s.ax + ex, h, s.az + ez), su)
        }

        // 5. porte: un battente nel varco, cosi' si vede che di la' non si passa
        val por = mb.part("porte", GL20.GL_TRIANGLES, attributi, materiale(LEGNO))
        for (k in m.connettori.filter { it.porta }) {
            val r = Pianta.varco(k)
            val cx = (r.x0 + r.x1) / 2f * c
            val cz = (r.z0 + r.z1) / 2f * c
            val orizzontale = (r.x1 - r.x0) > (r.z1 - r.z0)
            val larg = (if (orizzontale) (r.x1 - r.x0) else 0.24f) * c
            val prof = (if (orizzontale) 0.24f else (r.z1 - r.z0)) * c
            BoxShapeBuilder.build(por, cx, h * 0.42f, cz, larg * 0.92f, h * 0.84f, prof * 0.92f)
        }

        return mb.end()
    }

    /**
     * Niente scarto delle facce di dietro.
     *
     * Il gruppo sta sempre dentro il dungeon, quindi il risparmio sarebbe
     * minimo; in cambio si toglie di mezzo l'errore piu' insidioso di
     * tutti, quello di un triangolo avvolto al contrario che sparisce
     * senza dire niente.
     */
    private fun materiale(c: Color) = Material(
        ColorAttribute.createDiffuse(c),
        IntAttribute.createCullFace(GL20.GL_NONE)
    )

    private fun vertice(p: Vector3, n: Vector3) =
        MeshPartBuilder.VertexInfo().setPos(p).setNor(n)

    private fun triangolo(b: MeshPartBuilder, a: Vector3, c: Vector3, d: Vector3, n: Vector3) {
        b.triangle(vertice(a, n), vertice(c, n), vertice(d, n))
    }

    private fun quadrato(b: MeshPartBuilder, p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, n: Vector3) {
        b.rect(vertice(p0, n), vertice(p1, n), vertice(p2, n), vertice(p3, n))
    }
}
