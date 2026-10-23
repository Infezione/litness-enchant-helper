# ⚡ Litness Enchant Helper

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)
![Fabric](https://img.shields.io/badge/ModLoader-Fabric-blue.svg)
![Lunar Client](https://img.shields.io/badge/Lunar%20Client-Compatible-informational.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)
![Author](https://img.shields.io/badge/Author-Waaxey-purple.svg)

**Litness Enchant Helper** è la mod Fabric client-side definitiva progettata per i giocatori del server Minecraft **Litness**.  
Il suo obiettivo principale è eliminare ogni spreco di livelli di esperienza, evitare il famigerato errore di Minecraft vanilla *"Troppo Costoso! (>39 livelli)"* e guidare il giocatore passo dopo passo nella creazione dell'equipaggiamento perfetto, supportando sia tutti gli incantesimi Vanilla sia tutti gli incantesimi Custom esclusivi del server.

---

## 🌟 Perché è indispensabile su Litness?

Sul server Litness, forgiare spade, archi o armature "God-Set" con 10–12 incantesimi (tra Vanilla e Custom) può diventare proibitivo a causa della meccanica di penalità cumulativa dell'incudine di Minecraft (*Prior Work Penalty*). Se i libri vengono fusi nell'ordine sbagliato, l'incudine raggiungerà rapidamente il limite di 39 livelli, rendendo l'oggetto impossibile da completare.

**Litness Enchant Helper** risolve questo problema alla radice calcolando matematicamente la sequenza ideale ad albero binario (*Pareto-Optimal*), garantendo il costo totale di XP più basso possibile e rimanendo sempre ampiamente sotto la soglia massima consentita.

---

## 🎯 Funzionalità Dettagliate

### 1. ⚡ Assistente Incudine in Tempo Reale
![Assistente Incudine](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_anvil.png)
- **Sidebar integrata**: non appena apri un'incudine di gioco, compare lateralmente una schermata pulita in stile *dark glass* che si adatta perfettamente alla GUI di Minecraft.
- **Rilevamento istantaneo**: riconosce automaticamente l'oggetto inserito nel primo slot (o tenuto in mano), la sua penalità attuale e i libri presenti nel tuo inventario.
- **Istruzioni chiare**:
  - `🔨 UNISCI LIBRI`: ti indica quali due libri incantati della tua borsa unire per primi per ottimizzare la penalità.
  - `⚔ TEMPRA SU OGGETTO`: ti segnala quando è il momento perfetto per applicare il libro risultante sulla tua arma o armatura.

---

### 2. 🔨 Guida alla Fusione Passo-Passo (Albero Ottimale)
![Guida alla Fusione](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_guide.png)
- **Calcolo istantaneo**: visualizza ogni singolo passaggio con gli slot dell'incudine (Slot 1, Slot 2 e Risultato), con icone grafiche per ciascun pezzo.
- **Indicatore inventario**: su ogni passaggio della guida, un badge verde `✔ In Borsa` ti segnala se possiedi già quel libro nella tua borsa.
- **Costi trasparenti**: mostra il costo in livelli di ciascun singolo step e il picco massimo di livelli richiesti.
- **Copia rapida**: con il pulsante `[📋 Copia Passi]` puoi copiare l'intera guida formattata negli appunti per incollarla in chat, su Discord o sul blocco note.

---

### 3. ⭐ Creatore & Gestore "Set Futuri"
- **Pianificazione libera**: crea in anticipo la combinazione dei sogni per qualsiasi pezzo di equipaggiamento (Spada, Elmo, Corazza, Gambali o Stivali).
- **Lista da zero**: quando crei un nuovo set, la lista parte completamente pulita per permetterti di selezionare fino a 12 incantesimi desiderati.
- **Salvataggio persistente**: assegna un nome al set (es. *Spada Dungeon Boss*, *Corazza PvP*, *Piccone Farm*) e salvalo in memoria locale.
- **Guida teorica immediata**: cliccando `⚡ Guida` su qualsiasi set salvato, la mod genera immediatamente tutti i passaggi di fusione teorici anche se non possiedi ancora tutti i libri nel tuo inventario.
- **God-Set inclusi**: include configurazioni God-Set preimpostate per spada e armatura pronte all'uso.

---

### 4. ⚠️ Sistema Anti-Errore e Rilevamento Conflitti
![Creatore Set Futuri e Anti-Conflitti](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_set_builder.png)
- **Triangolo di pericolo `⚠️`**: quando selezioni un incantesimo incompatibile con altri già scelti (es. *Affilatezza* vs *Anatema*, *Protezione* vs *Protezione dal fuoco*, ecc.), la mod mostra un triangolo di allerta e indica con precisione contro quale incantesimo va in conflitto.
- **Prevenzione sprechi**: impedisce di sprecare libri rari e preziosi in combinazioni non permesse dal gioco o dal regolamento del server.

---

### 5. 📌 HUD a Schermo in Tempo Reale (Zero Lag)
![HUD in Gioco](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_hud.png)
- **Fissa la tua lista a schermo**: cliccando su `📌 Pin HUD`, tutti gli incantesimi del set attivo o salvato compaiono in un box elegante nell'angolo in alto a sinistra dello schermo.
- **Feedback visivo intelligente**:
  - 🟢 **`✔ <Nome>` (Verde)**: Possiedi il libro incantato nella tua borsa, pronto per essere fuso.
  - 🔴 **`✖ <Nome>` (Rosso sbarrato)**: L'incantesimo è già stato applicato sull'arma che hai in mano, nell'incudine o sull'armatura indossata.
  - ⚪ **`• <Nome>` (Grigio)**: Incantesimo mancante, non ancora trovato o acquistato.
- **Rimozione automatica**: eliminando un set salvato, gli incantesimi vengono rimossi all'istante dall'HUD senza rimanere bloccati.
- **Fluidità a 240+ FPS**: grazie al sistema di caching a versioning atomico (aggiornamento leggero ogni 250ms), il rendering per frame richiede meno di 0,005 millisecondi, garantendo zero cali di framerate e massima fluidità in gioco.

---

### 6. 📖 Catalogo Completo degli Incantesimi (Vanilla & Custom)
![Catalogo Incantesimi](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_catalog.png)
- **Tutti gli incantesimi di Litness**: include l'intero archivio degli incantesimi Custom del server e quelli Vanilla di Minecraft.
- **Filtri rapidi**: ricerca testuale istantanea (con supporto per nomi italiani e alias inglesi) e filtri *Tutti / Vanilla / Custom*.
- **Schede informative**: visualizza rarità (*Comune, Non comune, Raro, Epico, Leggendario, Speciale*), descrizione completa, livello massimo, pezzi compatibili e costo base dell'incudine.

---

### 7. 🔮 Tooltip Arricchiti nell'Inventario (Lore & Info Live)
![Tooltip Lore e Conflitti](https://github.com/Infezione/litness-enchant-helper/releases/download/v1.0.0/preview_lore_tooltip.png)
- **Info immediate sui libri**: passando il cursore su qualsiasi libro incantato nella tua borsa o nelle casse, la mod arricchisce automaticamente la lore di gioco con:
  - ⚔️ **Icone strumenti compatibili**: visualizza subito su quali oggetti è possibile applicarlo (es. spade, asce, picconi, armature).
  - ⚡ **Livello Massimo**: indica il livello massimo raggiungibile dell'incantesimo (es. `[Max III]`).
  - ⚠️ **Rilevamento Conflitti**: segnala istantaneamente le incompatibilità con altri incantesimi (es. `※ Conflitti: Lifesteal`).
  - 💰 **Costo Base Incudine**: calcola in anticipo il costo netto in livelli di XP per applicare quel libro.

---

## 🎮 Controlli & Tasti Rapidi

| Tasto / Controllo | Azione |
| :--- | :--- |
| **`N`** | Apri la schermata principale (Catalogo / Guida Fusione / I Miei Set) |
| **`[📋 Guida Passi]`** | Tasto rapido in cima all'interfaccia dell'incudine per aprire la guida |
| **`[📋 Copia Passi]`** | Copia l'intera sequenza di fusione negli appunti |
| **`[📌 Pin HUD]`** | Mostra / nasconde il tracker del set a schermo |
| **`[Pen: +X]`** | Seleziona manualmente la penalità di lavoro precedente dell'oggetto (+0, +1, +3, +7, +15, +31) |
| **`[ESC]`** | Chiudi la schermata e torna al gioco |

---

## 📥 Installazione

### Metodo 1: Per Lunar Client (Consigliato)
1. Scarica il file **`LitnessEnchantHelper-1.0.0.jar`** dall'area [Releases](../../releases).
2. Premi sulla tastiera `Win + R`, incolla la seguente stringa e premi **Invio**:
   ```text
   %userprofile%\.lunarclient\profiles\26\mods\fabric-26.2\
   ```
3. Incolla il file `.jar` scaricato all'interno di questa cartella.
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
