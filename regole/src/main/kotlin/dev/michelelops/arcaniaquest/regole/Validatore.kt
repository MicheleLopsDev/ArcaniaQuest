package dev.michelelops.arcaniaquest.regole

/** Un guaio trovato nel catalogo, con il modulo che lo contiene. */
data class Guaio(val modulo: String, val cosa: String) {
    override fun toString(): String = "$modulo: $cosa"
}

/**
 * Il controllo formale del catalogo.
 *
 * Dice se il dato e' coerente con se stesso: righe e colonne che tornano,
 * connettori che si aprono davvero verso fuori, tiri di dado tutti
 * presenti e nessuno doppio. Non puo' dire se un modulo somiglia alla
 * tavola stampata — per quello c'e' `verificato`, e ci vuole un occhio.
 */
object Validatore {

    fun verifica(catalogo: Catalogo): List<Guaio> {
        val guai = mutableListOf<Guaio>()
        val visti = mutableMapOf<Pair<String, Int>, String>()

        for (m in catalogo.tutti) {
            guai += verificaModulo(m)

            val tiro = m.pesca.tabella to m.pesca.valore
            visti[tiro]?.let {
                guai += Guaio(m.id, "esce con lo stesso tiro di $it (${tiro.first} ${tiro.second})")
            }
            visti[tiro] = m.id

            if (catalogo.regoleFamiglia[m.famiglia] == null) {
                guai += Guaio(m.id, "la famiglia ${m.famiglia} non ha regole nel catalogo")
            }
        }

        val d66 = catalogo.tutti.filter { it.pesca.tabella == "d66" }.map { it.pesca.valore }.toSet()
        val attesi = (1..6).flatMap { a -> (1..6).map { b -> a * 10 + b } }
        val mancanti = attesi.filterNot { it in d66 }
        if (mancanti.isNotEmpty()) {
            guai += Guaio("catalogo", "mancano i tiri d66: ${mancanti.joinToString()}")
        }

        return guai
    }

    fun verificaModulo(m: Modulo): List<Guaio> {
        val guai = mutableListOf<Guaio>()

        if (m.caselle.size != m.profondita) {
            guai += Guaio(m.id, "${m.caselle.size} righe ma profondita' ${m.profondita}")
        }
        m.caselle.forEachIndexed { z, riga ->
            if (riga.length != m.larghezza) {
                guai += Guaio(m.id, "riga $z lunga ${riga.length} ma larghezza ${m.larghezza}")
            }
            riga.forEachIndexed { x, c ->
                if (c != '0' && c != '1') guai += Guaio(m.id, "carattere '$c' in ($x, $z)")
            }
        }
        val celle = m.celle().toSet()
        if (celle.isEmpty()) {
            guai += Guaio(m.id, "nessuna casella calpestabile")
        } else {
            // Due caselle che si toccano solo d'angolo non si attraversano:
            // un pezzo cosi' si spacca in due meta' che non comunicano, e
            // dentro il gioco diventa una stanza dove non si entra.
            val staccate = celle - attaccate(m, celle.first())
            if (staccate.isNotEmpty()) {
                guai += Guaio(m.id, "caselle staccate dal resto: ${staccate.sortedWith(compareBy({ it.z }, { it.x }))}")
            }
        }

        for (k in m.connettori) {
            when {
                !m.dentro(k.x, k.z) ->
                    guai += Guaio(m.id, "connettore fuori dall'ingombro in (${k.x}, ${k.z})")
                !m.calpestabile(k.x, k.z) ->
                    guai += Guaio(m.id, "connettore su roccia in (${k.x}, ${k.z})")
                !m.apreVersoFuori(k) ->
                    guai += Guaio(m.id, "connettore ${k.lato} in (${k.x}, ${k.z}) da' su una casella interna")
            }
        }
        if (m.connettori.isEmpty()) {
            guai += Guaio(m.id, "nessun connettore: non si attacca a niente")
        }

        m.partenza?.let {
            if (!m.calpestabile(it.x, it.z)) {
                guai += Guaio(m.id, "partenza su roccia in (${it.x}, ${it.z})")
            }
        }
        if (m.famiglia == Famiglia.INIZIALE && m.partenza == null) {
            guai += Guaio(m.id, "modulo iniziale senza partenza")
        }
        if (m.famiglia != Famiglia.INIZIALE && m.partenza != null) {
            guai += Guaio(m.id, "ha una partenza ma non e' un modulo iniziale")
        }

        for (a in m.arredi) {
            if (!m.calpestabile(a.x, a.z)) {
                guai += Guaio(m.id, "arredo ${a.tipo} su roccia in (${a.x}, ${a.z})")
            }
        }

        return guai
    }

    /** Le caselle che si raggiungono a piedi da [da], dentro il modulo. */
    private fun attaccate(m: Modulo, da: Cella): Set<Cella> {
        val viste = mutableSetOf(da)
        val coda = ArrayDeque(listOf(da))
        while (coda.isNotEmpty()) {
            val c = coda.removeFirst()
            for (l in Lato.entries) {
                val n = Cella(c.x + l.dx, c.z + l.dz)
                if (n in viste || !m.calpestabile(n.x, n.z)) continue
                viste += n
                coda += n
            }
        }
        return viste
    }
}
