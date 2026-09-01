package dev.michelelops.arcaniaquest.regole

/**
 * Appende le torce ai muri di un sotterraneo appena montato.
 *
 * Il catalogo dice com'e' fatta una stanza — le caselle, i connettori, la
 * pianta — ma non dove stanno le fiaccole, e non e' un'omissione: i 42
 * moduli sono la trascrizione delle tavole stampate, e le tavole le
 * torce non le dicono. Le mette qui il generatore, a partita nuova, cosi'
 * le tavole restano quelle che sono e ogni sotterraneo ha le sue luci.
 *
 * Un modulo che le torce ce le ha gia' scritte nel catalogo se le tiene:
 * quando qualcosa e' scritto a mano, comanda quello.
 */
object Fiaccolatura {

    /**
     * Quante caselle almeno fra una torcia e l'altra.
     *
     * Una torcia illumina un dieci metri scarsi. A tre caselle le pozze di
     * luce si toccherebbero e il sotterraneo diventerebbe un corridoio
     * d'albergo; a quattro restano isole di chiaro separate da tratti di
     * buio, che e' come dev'essere.
     */
    const val PASSO_FRA_TORCE = 4

    /**
     * Da cosa nasce la disposizione delle torce.
     *
     * Non si pesca dalla [Sorte] della partita ma da un seme derivato, uno
     * per pezzo. E' apposta: attingere a quella comune sposterebbe di un
     * tiro tutto quello che viene dopo, e ogni seme gia' scritto in giro
     * genererebbe da domani un sotterraneo diverso. Cosi' invece i vecchi
     * semi restano quelli, e le torce sono comunque sempre le stesse a
     * parita' di seme.
     */
    private const val SCARTO = 1000003L

    /** Gli stessi pezzi, con le torce appese dove ci stanno. */
    fun illumina(pezzi: List<Piazzato>, seme: Long): List<Piazzato> =
        pezzi.mapIndexed { i, p ->
            p.copy(modulo = conTorce(p.modulo, Sorte(seme * SCARTO + i)))
        }

    private fun conTorce(m: Modulo, sorte: Sorte): Modulo {
        if (m.arredi.any { it.tipo == "torcia" }) return m

        val appese = mutableListOf<Cella>()
        for (c in sorte.mescola(caselleAMuro(m))) {
            if (appese.any { distanza(it, c) < PASSO_FRA_TORCE }) continue
            appese += c
        }
        if (appese.isEmpty()) return m

        // in ordine di casella e non di pesca: due partite con le stesse
        // torce devono dare anche lo stesso file, se qualcuno lo guarda
        return m.copy(
            arredi = m.arredi + appese
                .sortedWith(compareBy({ it.z }, { it.x }))
                .map { Arredo("torcia", listOf(it.x, it.z)) }
        )
    }

    /**
     * Le caselle da cui si vede un muro libero: almeno un lato che dia su
     * roccia e che non sia gia' occupato da un connettore. Una torcia in
     * mezzo a una sala non avrebbe a cosa appendersi, e una nel vano di
     * una porta darebbe fuoco a chi passa.
     */
    private fun caselleAMuro(m: Modulo): List<Cella> =
        m.celle().filter { c ->
            Lato.entries.any { l ->
                !m.calpestabile(c.x + l.dx, c.z + l.dz) && !connettoreSu(m, c, l)
            }
        }

    private fun connettoreSu(m: Modulo, c: Cella, l: Lato): Boolean =
        m.connettori.any { it.x == c.x && it.z == c.z && it.lato == l }

    /**
     * Quanto distano due caselle contando anche le diagonali per un passo
     * solo: due torce in diagonale sono vicine quanto due in fila.
     */
    private fun distanza(a: Cella, b: Cella): Int =
        maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
}
