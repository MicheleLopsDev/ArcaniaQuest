# I moduli del dungeon

Il dungeon non è disegnato: è **pescato e incastrato**. Ogni partita mette
insieme pezzi presi da un catalogo, e i pezzi sono di tre famiglie che nel
gioco valgono cose diverse.

Sorgente: le tavole dei moduli del gioco da tavolo — pag. 191 «Modulo
Iniziale» (d6) e pagg. 192-194 «Moduli» (d66). Il catalogo vive in
`content/moduli/catalogo.json`.

---

## 1. Le tre famiglie

| Famiglia | Tabella | Cosa è |
|---|---|---|
| `iniziale` | d6 | Il pezzo da cui parte la partita. Contiene la **scala d'ingresso**. Se ne pesca uno solo. |
| `corridoio` | d66 (numeri con la C) | Passaggi. Collegano, non ospitano. |
| `stanza` | d66 (numeri senza lettera) | Ambienti. Ospitano incontri, tesori, arredi. |

La distinzione stanza/corridoio non è estetica: **cambia le regole**, e
per questo sta nel dato e non nella mesh.

### Cosa cambia fra stanza e corridoio

| | `corridoio` | `stanza` |
|---|---|---|
| Incontri | solo mostri **vaganti**, che arrivano da un'uscita | incontri **fissi**, piazzati quando il modulo entra in gioco |
| Formazione | il gruppo va **in fila**: combatte solo la prima fila | formazione **libera**: tutti e quattro possono agire |
| Agguato | possibile: chi è dietro l'angolo non si vede | no: la stanza si vede entrando |
| Tesoro | no | sì |
| Arredi | no | scala, altare, fontana, forziere, statua |
| Luce | buio, si vede quanto illumina la torcia | può avere torce a muro proprie |
| Riposo | vietato | permesso se la stanza è vuota e le porte chiuse |

Sono le regole di partenza, quelle che rendono diverso attraversare da
esplorare. Vanno bilanciate giocando, non decise a tavolino.

---

## 2. Come è fatto un modulo

```json
{
  "id": "S25",
  "nome": "Sala Ovale",
  "famiglia": "stanza",
  "pesca": { "tabella": "d66", "valore": 25 },
  "ingombro": { "w": 6, "d": 4 },
  "caselle": ["111000", "111111", "111000", "111000"],
  "pianta": [
    { "forma": "rettangoloArrotondato", "x": 0, "z": 0, "w": 3, "d": 4, "raggio": 0.9 },
    { "forma": "rettangolo", "x": 3, "z": 1, "w": 3, "d": 1 }
  ],
  "connettori": [
    { "lato": "est",   "cella": [5, 1], "porta": true },
    { "lato": "ovest", "cella": [0, 2], "porta": true }
  ],
  "verificato": false
}
```

### I campi

- **`id`** — `I<n>` iniziali, `C<n>` corridoi, `S<n>` stanze. Il numero è
  quello della tavola stampata, così il catalogo resta confrontabile col
  foglio.
- **`ingombro`** — il rettangolo che contiene il modulo, in caselle.
  Una casella = **3 metri**, come i muri sono alti 3 metri.
- **`caselle`** — una riga per ogni `z` (da nord a sud), un carattere per
  ogni `x` (da ovest a est). `1` si cammina, `0` è roccia viva. È l'unica
  cosa che serve al movimento e alla collisione: nessuna fisica.
- **`pianta`** — *facoltativo*. Le forme da cui si genera la mesh. Se
  manca, la pianta si ricava dalle `caselle`: è il caso della quasi
  totalità dei moduli, che sono fatti di angoli retti. Si scrive a mano
  solo quando la stanza **non** è squadrata — la Sala Ovale (S25) e la
  Sala a Mandorla (S34) sono le uniche due finora. Il pavimento infatti è
  un poligono, non un mosaico di caselle: è così che una sala ovale sta
  dentro una griglia quadrata senza sembrare quadrata.
- **`connettori`** — dove il modulo si attacca. `cella` è la casella
  **interna** al modulo, `lato` è il bordo di quella casella che si apre
  verso fuori. `porta: true` mette un battente; `false` è un'apertura
  vuota.

  Attenzione: un connettore **non sta per forza sul bordo del rettangolo
  d'ingombro**. Sta dove il modulo si apre verso l'esterno, e in un pezzo
  a L quel punto può cadere in mezzo al rettangolo. La regola vera è: la
  casella di là dal `lato` dev'essere roccia o fuori dal modulo. Per il
  generatore non cambia niente, perché incastra caselle, non rettangoli.
- **`verificato`** — `false` finché un occhio umano non ha confrontato il
  modulo con la tavola stampata. Serve: i moduli sono stati trascritti da
  fotografie, e alcune letture sono incerte.

I moduli `iniziale` hanno in più un campo `partenza`: la casella da cui
entra il gruppo e il verso in cui guarda all'inizio. Non è un connettore —
lì c'è la scala da cui si scende, e non ci si attacca nessun altro modulo.

```json
"partenza": { "cella": [3, 1], "verso": "nord" }
```

---

## 3. Come si incastrano

Il generatore lavora solo sui connettori, non sulla forma:

1. Pesca un modulo `iniziale` col d6 e lo piazza all'origine.
2. Prende un connettore ancora libero.
3. Pesca col d66 un modulo che abbia almeno un connettore libero sul
   **lato opposto** (est cerca ovest, nord cerca sud).
4. Ruota il modulo di 0°, 90°, 180° o 270° finché i due lati combaciano,
   poi lo trasla perché le due caselle di confine siano adiacenti.
5. Rifiuta il piazzamento se una qualunque casella del nuovo modulo cade
   su una casella già occupata.
6. Ripete finché non ha piazzato il numero di moduli previsto, poi tappa
   con muri ciechi i connettori rimasti liberi.

Regole di forma da tenere quando ci saranno abbastanza pezzi: mai due
corridoi in fila più di *n* volte, ogni stanza raggiungibile, almeno un
anello per non costringere a tornare sempre sui propri passi.

---

## 4. Stato del catalogo

**Completo come numeri: 42 moduli.** 6 iniziali (d6) e tutti e 36 quelli
della tavola d66 — 13 corridoi e 23 stanze.

**Non completo come qualità: tutti a `"verificato": false`.** La
trascrizione viene da fotografie delle tavole stampate, e il numero
esatto di caselle e la posizione dei connettori sono la cosa più facile
da sbagliare su un'immagine piccola. Finché un modulo non è confrontato
con l'originale, il suo dato è un'ipotesi.

Il controllo formale invece è automatico:

```
python strumenti/valida_catalogo.py
```

Verifica che le righe corrispondano alla profondità, le colonne alla
larghezza, che i connettori stiano su una casella calpestabile e si
aprano davvero verso l'esterno, e che nessun tiro di dado sia doppio o
manchi. Non può dirti se il modulo somiglia alla tavola: quello lo può
fare solo un occhio.
