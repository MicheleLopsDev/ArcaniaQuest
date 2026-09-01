package dev.michelelops.arcaniaquest.regole

/** Un passo della perlustrazione: da dove a dove, e cosa c'era in mezzo. */
data class Passo(
    val numero: Int,
    val da: Cella,
    val a: Cella,
    val verso: Lato,
    val porta: Boolean,
    /** True quando si torna sui propri passi per riprovare un'altra strada. */
    val indietro: Boolean
) {
    override fun toString(): String {
        val segno = if (indietro) "<-" else "->"
        val nota = if (porta) "   porta" else ""
        return "%5d  (%3d,%3d) %s (%3d,%3d)  %-5s%s"
            .format(numero, da.x, da.z, segno, a.x, a.z, verso.name.lowercase(), nota)
    }
}

/**
 * L'esito di una perlustrazione.
 *
 * [percorribile] e' la risposta secca: dalla partenza si arriva a ogni
 * casella del sotterraneo, camminando un passo alla volta come farebbe
 * il gruppo. [passi] e' la strada che ci e' voluta, e serve quando la
 * risposta e' no — perche' allora bisogna capire dove ci si e' fermati.
 */
class Perlustrazione(
    val seme: Long,
    val passi: List<Passo>,
    val visitate: Set<Cella>,
    val nonRaggiunte: Set<Cella>,
    val porteAttraversate: Int
) {
    val percorribile: Boolean get() = nonRaggiunte.isEmpty()
    val quanteInTutto: Int get() = visitate.size + nonRaggiunte.size

    fun riassunto(): String {
        val esito = if (percorribile) "PERCORRIBILE" else "INTERROTTO"
        return "seme %-8s %-13s %d/%d caselle in %d passi, %d porte"
            .format(Sorte.scrivi(seme), esito, visitate.size, quanteInTutto, passi.size, porteAttraversate)
    }

    /** Il diario di tutti i passi, riga per riga. */
    fun diario(): String = buildString {
        appendLine(riassunto())
        for (p in passi) appendLine(p.toString())
        if (!percorribile) {
            appendLine("caselle mai raggiunte:")
            for (c in nonRaggiunte.sortedWith(compareBy({ it.z }, { it.x }))) {
                appendLine("       (%3d,%3d)".format(c.x, c.z))
            }
        }
    }
}

/**
 * Cammina tutto il sotterraneo, un passo alla volta, e dice se si tiene.
 *
 * Non e' la stessa cosa di chiedersi «e' connesso»: qui ci si muove come
 * si muove il gruppo, una casella per volta fra caselle vicine, e si
 * scrive dove si e' passati. Se un sotterraneo non si lascia percorrere,
 * il diario dice fin dove si e' arrivati invece di limitarsi a dire di no.
 *
 * Le porte chiuse **non fermano**: si contano e si passa. Una porta e' un
 * ritardo, non un muro — il gruppo ha il tasto per aprirla. Quello che
 * ferma davvero e' la roccia e i muri fra due pezzi che non si toccano.
 */
object Perlustratore {

    fun percorri(d: Dungeon): Perlustrazione {
        val tutte = d.pezzi.flatMap { it.celleMondo() }.toSet()
        val visitate = linkedSetOf(d.partenza)
        val passi = mutableListOf<Passo>()
        val percorso = ArrayDeque<Cella>()
        var porte = 0
        var qui = d.partenza
        var numero = 0

        while (true) {
            val avanti = Lato.entries.firstOrNull { l ->
                val n = Cella(qui.x + l.dx, qui.z + l.dz)
                n !in visitate && passabile(d, qui, l)
            }

            if (avanti != null) {
                val n = Cella(qui.x + avanti.dx, qui.z + avanti.dz)
                val conPorta = d.portaFra(qui.x, qui.z, avanti)?.conBattente == true
                if (conPorta) porte++
                passi += Passo(++numero, qui, n, avanti, conPorta, indietro = false)
                percorso.addLast(qui)
                visitate += n
                qui = n
                continue
            }

            // niente di nuovo da questa casella: si torna sui propri passi
            val prima = percorso.removeLastOrNull() ?: break
            val verso = Lato.entries.first { qui.x + it.dx == prima.x && qui.z + it.dz == prima.z }
            val conPorta = d.portaFra(qui.x, qui.z, verso)?.conBattente == true
            passi += Passo(++numero, qui, prima, verso, conPorta, indietro = true)
            qui = prima
        }

        return Perlustrazione(
            seme = d.seme,
            passi = passi,
            visitate = visitate,
            nonRaggiunte = tutte - visitate,
            porteAttraversate = porte
        )
    }

    /** Una porta chiusa si apre: quello che ferma e' la roccia e il muro. */
    private fun passabile(d: Dungeon, da: Cella, verso: Lato): Boolean =
        when (d.ostacolo(da.x, da.z, verso)) {
            Ostacolo.NIENTE, Ostacolo.PORTA_CHIUSA -> true
            Ostacolo.ROCCIA, Ostacolo.MURO -> false
        }
}
