# ⚡ Litness Enchant Helper

[![DOWNLOAD MOD (.JAR)](https://img.shields.io/badge/⬇️%20SCARICA%20SUBITO%20LA%20MOD%20(.JAR)-VERSIONE%201.1.0-22c55e?style=for-the-badge&logo=curseforge&logoColor=white)](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.1.0/LitnessEnchantHelper-1.1.0.jar)
[![ULTIMI AGGIORNAMENTI](https://img.shields.io/badge/🔄%20ULTIMI%20AGGIORNAMENTI-VEDI%20LE%20NOVITÀ-6366f1?style=for-the-badge&logo=sparkles&logoColor=white)](#-ultimi-aggiornamenti)

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)
![Fabric](https://img.shields.io/badge/ModLoader-Fabric-blue.svg)
![Lunar Client](https://img.shields.io/badge/Lunar%20Client-Compatible-informational.svg)
![Release](https://img.shields.io/badge/Release-v1.1.0-blueviolet.svg)
![Author](https://img.shields.io/badge/Author-Waaxey-purple.svg)

> [!TIP]
> ### ⌨️ Come aprire il menu in gioco:
> Premi semplicemente il tasto **`N`** sulla tastiera in qualsiasi momento per aprire la schermata principale (Catalogo Incantesimi, Calcolatore, Guida Fusione e I Miei Set).  
> *(Puoi anche personalizzare il tasto in `Opzioni -> Comandi -> Controlli` di Minecraft sotto la voce "Litness Optimizer").*  
> 👉 *Per consultare tutte le nuove funzioni appena introdotte, clicca sul pulsante in alto o vai a [🔄 Ultimi Aggiornamenti](#-ultimi-aggiornamenti).*

**Litness Enchant Helper** è la mod Fabric client-side definitiva progettata per i giocatori del server Minecraft **Litness**.  
Il suo obiettivo principale è eliminare ogni spreco di livelli di esperienza, evitare il famigerato errore di Minecraft vanilla *"Troppo Costoso! (>39 livelli)"* e guidare il giocatore passo dopo passo nella creazione dell'equipaggiamento perfetto, supportando sia tutti gli incantesimi Vanilla sia tutti gli incantesimi Custom esclusivi del server.

---

## 🌟 Perché è indispensabile su Litness?

Sul server Litness, forgiare spade, archi, picconi o armature con 10–12 incantesimi (tra Vanilla e Custom) può diventare proibitivo a causa della meccanica di penalità cumulativa dell'incudine di Minecraft (*Prior Work Penalty*). Se i libri vengono fusi nell'ordine sbagliato, l'incudine raggiungerà rapidamente il limite di 39 livelli, rendendo l'oggetto impossibile da completare.

**Litness Enchant Helper** risolve questo problema alla radice calcolando matematicamente la sequenza ideale ad albero binario (*Pareto-Optimal*), garantendo il costo totale di XP più basso possibile e rimanendo sempre ampiamente sotto la soglia massima consentita.

---

## 🎯 Funzionalità Dettagliate

### 1. ⚡ Assistente Incudine in Tempo Reale
![Assistente Incudine](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_anvil.png)
- **Sidebar integrata**: non appena apri un'incudine di gioco, compare lateralmente una schermata pulita in stile *dark glass* che si adatta perfettamente alla GUI di Minecraft.
- **Rilevamento istantaneo**: riconosce automaticamente l'oggetto inserito nel primo slot (o tenuto in mano), la sua penalità attuale e i libri presenti nel tuo inventario.
- **Istruzioni chiare**:
  - `🔨 UNISCI LIBRI`: ti indica quali due libri incantati del tuo inventario unire per primi per ottimizzare la penalità.
  - `⚔ TEMPRA SU OGGETTO`: ti segnala quando è il momento perfetto per applicare il libro risultante sulla tua arma o armatura.

---

### 2. 🔨 Guida alla Fusione Passo-Passo (Albero Ottimale)
![Guida alla Fusione](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_guide.png)
- **Calcolo istantaneo**: visualizza ogni singolo passaggio con gli slot dell'incudine (Slot 1, Slot 2 e Risultato), con icone grafiche per ciascun pezzo.
- **Indicatore inventario**: su ogni passaggio della guida, un badge verde `✔ In Inventario` ti segnala se possiedi già quel libro nel tuo inventario.
- **Costi trasparenti**: mostra il costo in livelli di ciascun singolo step e il picco massimo di livelli richiesti.
- **Copia rapida**: con il pulsante `[📋 Copia Passi]` puoi copiare l'intera guida formattata negli appunti per incollarla in chat, su Discord o sul blocco note.

---

### 3. ⭐ Creatore & Gestore "Set Futuri"
- **Pianificazione libera**: crea in anticipo la combinazione dei sogni per qualsiasi pezzo di equipaggiamento (Spada, Piccone, Elmo, Corazza, Gambali o Stivali).
- **Lista da zero**: quando crei un nuovo set, la lista parte completamente pulita per permetterti di selezionare fino a 12 incantesimi desiderati.
- **Salvataggio persistente**: assegna un nome al set (es. *Spada Dungeon Boss*, *Corazza PvP*, *Piccone Farm*) e salvalo in memoria locale.
- **Guida teorica immediata**: cliccando `⚡ Guida` su qualsiasi set salvato, la mod genera immediatamente tutti i passaggi di fusione teorici anche se non possiedi ancora tutti i libri nel tuo inventario.

---

### 4. 📌 Pin HUD Checklist in Gioco (Real-Time Tracking)
![Pin HUD In Gioco](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_hud.png)
- **Checklist sempre attiva a schermo**: tieni d'occhio i tuoi progressi direttamente durante il gameplay senza dover riaprire il menu ogni volta.
- **Tracking visivo intelligente**:
  - 🟢 `✔ In Inventario`: se possiedi già il libro incantato nell'inventario.
  - 🔴 `✔ Applicato`: se l'incantesimo è già stato applicato sull'oggetto (con verifica specifica sullo slot di competenza: mano principale per armi/attrezzi, slot armatura per pezzi d'armatura).
  - ⚪ `• Mancante`: incantesimo ancora da reperire.
- **Pinnabile con un clic**: attiva o disattiva il Pin direttamente dalla scheda del set in *"I Miei Set"* tramite il pulsante `[📌 Pin HUD]` / `[★ Pinnato]`.

---

### 5. ⚠️ Sistema Anti-Errore e Rilevamento Conflitti
![Creatore Set Futuri e Anti-Conflitti](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_set_builder.png)
- **Triangolo di pericolo `⚠️`**: quando selezioni un incantesimo incompatibile con altri già scelti (es. *Affilatezza* vs *Anatema*, *Protezione* vs *Protezione dal fuoco*, ecc.), la mod mostra un triangolo di allerta e indica con precisione contro quale incantesimo va in conflitto.
- **Prevenzione sprechi**: impedisce di sprecare libri rari e preziosi in combinazioni non permesse dal gioco o dal regolamento del server.

---

### 6. 📖 Catalogo Completo degli Incantesimi (Vanilla & Custom)
![Catalogo Incantesimi](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_catalog.png)
- **Tutti gli incantesimi di Litness**: include l'intero archivio degli incantesimi Custom del server e quelli Vanilla di Minecraft.
- **Filtri rapidi avanzati**: ricerca testuale istantanea (con supporto per nomi italiani e alias inglesi), filtri di categoria (*Tutti / Vanilla / Custom*) e **filtri rapidi per tipo di oggetto** (*⚔ Spada, ⛏ Piccone, 🛡 Armature, 🏹 Arco, 🪓 Ascia, 🌾 Zappa, 🔧 Altri*).
- **Schede informative**: visualizza rarità (*Comune, Non comune, Raro, Epico, Leggendario, Speciale, Molto speciale*), descrizione completa, livello massimo, pezzi compatibili e costo base dell'incudine.

---

### 7. 🔮 Tooltip Arricchiti nell'Inventario (Lore & Info Live)
![Tooltip Lore e Conflitti](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_lore_tooltip.png)
- **Info immediate sui libri**: passando il cursore su qualsiasi libro incantato nel tuo inventario o nelle casse, la mod arricchisce automaticamente la lore di gioco con:
  - 🏷️ **Grado di Rarità**: visualizza il badge colorato della rarità ufficiale (*[COMUNE]*, *[NON COMUNE]*, *[RARO]*, *[EPICO]*, *[LEGGENDARIO]*, *[SPECIALE]*, *[MOLTO SPECIALE]*).
  - ⚡ **Livello Massimo**: indica se l'incantesimo ha raggiunto il cap (`[✓ Max II]`) o il livello massimo disponibile.
  - 🛡️ **Icone strumenti compatibili**: visualizza subito l'icona grafica dell'oggetto su cui è applicabile (es. armature, spade, asce, picconi).
  - ⚠️ **Rilevamento Conflitti**: segnala istantaneamente le incompatibilità dirette (es. `※ Conflitti: Spine, Static Barrier`).
  - 💰 **Costo Base Incudine**: calcola in anticipo il costo netto in livelli di XP per applicare quel libro.

---

<a id="ultimi-aggiornamenti"></a>
## 🔄 Ultimi Aggiornamenti

- 📌 **Pin HUD Checklist in Gioco**: visualizza a schermo durante il gameplay la checklist in tempo reale degli incantesimi con tracking intelligente degli incantesimi mancanti, dei libri in inventario e di quelli già applicati sul pezzo corrispondente.
- 🔔 **Notifica Automatica Aggiornamenti in Chat con Link Diretto**: all'ingresso nel server, la mod verifica in background su GitHub se è disponibile una versione più recente e, se presente, invia un messaggio in chat con link cliccabile per scaricare istantaneamente il file `.jar` o consultare le note di rilascio.
- ✏️ **Modifica Completa dei Set Salvati**: aggiunto il pulsante `[✏]` accanto a ogni set salvato in "I Miei Set" per modificarne all'istante nome, pezzo e incantesimi senza dover ricreare il set da zero.
- 💾 **Persistenza della Selezione tra le Sessioni**: uscendo e rientrando dal server o riavviando il gioco, la selezione del simulatore e il tipo di oggetto non si azzerano più e rimangono salvati esattamente come li avevi lasciati.
- 💰 **Persistenza dei Costi Custom**: i costi XP scoperti dai libri custom tramite lore vengono memorizzati su file e persistono tra i riavvii.
- ⛏️ **Supporto al Piccone nel Simulatore**: integrato il piccone nella barra di selezione rapida del Calcolatore/Simulatore, con icone del Piccone di Diamante, calcolo dei passi ottimali e salvataggio set.
- 🔍 **Nuova Barra Filtri per Oggetto nel Catalogo**: aggiunti pulsanti rapidi con evidenziazione verde attiva per filtrare gli incantesimi per *Spada*, *Piccone*, *Armature*, *Arco*, *Ascia*, *Zappa* e *Altri*.
- 🌿 **Descrizione Foraging Aggiornata**: corretta la lore di *Foraging* fedele al server (*"Le mele che cadono dalle foglie che rompi si moltiplicano: +210% in media"*).
- 🧭 **Fix Direzione Set Futuro**: corretta l'indicazione di apertura "Crea Nuovo Set" in alto a sinistra.

---

## 🎮 Controlli & Tasti Rapidi

| Tasto / Controllo | Azione |
| :--- | :--- |
| **`N`** | Apri la schermata principale (Catalogo / Guida Fusione / I Miei Set) |
| **`[📋 Guida Passi]`** | Tasto rapido in cima all'interfaccia dell'incudine per aprire la guida |
| **`[📋 Copia Passi]`** | Copia l'intera sequenza di fusione negli appunti |
| **`[Pen: +X]`** | Seleziona manualmente la penalità di lavoro precedente dell'oggetto (+0, +1, +3, +7, +15, +31) |
| **`[ESC]`** | Chiudi la schermata e torna al gioco |

---

## 📥 Installazione

### Metodo 1: Per Lunar Client (Consigliato)
1. Scarica il file **`LitnessEnchantHelper-1.1.0.jar`** dall'area [Releases](../../releases).
2. Premi sulla tastiera `Win + R`, incolla la seguente stringa e premi **Invio**:
   ```text
   %userprofile%\.lunarclient\profiles\26\mods\fabric-26.2\
   ```
3. Incolla il file `.jar` scaricato all'interno di questa cartella.

### Metodo 2: Per Fabric Launcher Standard
1. Assicurati di avere installato **Fabric Loader** per Minecraft 26.2 e la **Fabric API**.
2. Posiziona il file `LitnessEnchantHelper-1.1.0.jar` nella cartella `.minecraft/mods/`.
3. Avvia il gioco.

---

## 👤 Autore & Crediti

- **Sviluppato da**: **Waaxey**
- Realizzato appositamente per la community del server **Litness**.
- Licenza: [MIT License](LICENSE)

4. Avvia Minecraft selezionando la versione **Lunar Client 26.2 (Fabric)**.

### Metodo 2: Per Fabric Launcher Standard
1. Assicurati di avere installato **Fabric Loader** per Minecraft 26.2 e la **Fabric API**.
2. Posiziona il file `LitnessEnchantHelper-1.0.0.jar` nella cartella `.minecraft/mods/`.
3. Avvia il gioco.

---

## 👤 Autore & Crediti

- **Sviluppato da**: **Waaxey**
- Realizzato appositamente per la community del server **Litness**.
- Licenza: [MIT License](LICENSE)
