# REA come plugin MCP privato per CLIMA ONE (ChatGPT)

Questa integrazione **non** installa REA dentro l'APK Android e **non** apre un server pubblico. Prepara il collegamento fra REA, eseguito sul **tuo computer/server**, e ChatGPT tramite il [Secure MCP Tunnel ufficiale](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels).

> **Stato:** configurazione pronta nel repository, ma **non connessa** a ChatGPT. Per completare servono un computer che esegua REA, un tunnel autorizzato nel tuo account OpenAI Platform e il permesso **Add custom MCP server** in ChatGPT. Nessun account viene collegato automaticamente dal repository GitHub.

## 1. Che cosa può fare per CLIMA ONE

- Analizzare con JADX, tramite REA, **APK Android che hai diritto di analizzare**, identificando manifest, classi, funzioni e relazioni senza scambiare ipotesi per prove.
- Leggere acquisizioni di traffico autorizzate e confrontarle con il decoder Midea di CLIMA ONE (per esempio B5 capability, B1/B0, C1/1–3/7).
- Ricercare e documentare comandi *nativi* del firmware senza fingere che una scrittura di registro abbia funzionato: verifica sempre lo stato riletto e, se possibile, l'effetto fisico.
- **Non** consente automaticamente di comandare il climatizzatore a distanza, autenticarsi con SmartHome o scavalcare i limiti del compressore: per quelli rimane necessario il software CLIMA ONE con un dispositivo reale e credenziali legittime.

## 2. Requisiti sul computer che esegue REA

- Node.js **24.x >= 24.11**, consigliata la versione 24 LTS; alternative 22.x >=22.19 o 26+.
- npm, Java **JDK 17+**; per la decompilazione Android serve anche il motore **JADX headless** richiesto dalla documentazione REA.
- REA rilasciato tramite npm, versione verificata per questa integrazione: **6.7.0** (nessun pacchetto copiato o mantenuto in questo repository).
- Il programma [`tunnel-client` di OpenAI](https://github.com/openai/tunnel-client/releases/latest).
- OpenAI Platform: **tunnel_id** e una runtime key con permesso **Tunnels Read + Use**; per creare il tunnel servono **Tunnels Read + Manage**.
- ChatGPT: disponibilità di **Plugins → + → Add custom MCP server** e diritto di aggiungere un server. La disponibilità dipende dal piano e dalle impostazioni del workspace.

### Installazione locale

Da terminale sul computer **su cui vuoi fare analisi**:

```bash
node --version
java -version
npm install --global rea-agents@6.7.0
rea --version
rea doctor --json
```

Su Linux/macOS puoi prima eseguire il controllo senza modifiche:

```bash
bash integrations/rea/check-requisites.sh
```

Se usi Claude Code, Codex o altri agent che supportano MCP locale, puoi anche lanciare `rea setup`; per ChatGPT tramite tunnel **non è richiesto** l'installer dell'agente: il tunnel avvia direttamente il server REA in modalità stdio con `rea mcp`.

### APK Android, JADX

Per la decompilazione statica Android segui **solo le istruzioni e gli SHA-256 pubblicati nel progetto REA**:
https://github.com/morluto/rea/blob/main/docs/android-analysis.md

Non scaricare eseguibili/JAR da mirror o link casuali. La presenza di Java da sola **non** garantisce che l'analizzatore APK sia pronto: `rea doctor --json` e la documentazione Android riportano le dipendenze mancanti.

## 3. Creare e collegare il Secure MCP Tunnel (consigliato)

1. Da OpenAI Platform, apri **Tunnel settings** e crea un tunnel privato. Associalo all'organizzazione Platform **e** al workspace ChatGPT giusti.
2. Installa `tunnel-client` dalla [release ufficiale](https://github.com/openai/tunnel-client/releases/latest) sullo **stesso computer** su cui REA è disponibile.
3. Accertati che `rea --version` funzioni nello stesso ambiente e che `rea mcp` sia eseguibile.
4. Configura il tunnel sul computer. **Non inserire chiavi o token in GitHub o nella chat.** Fornisci `CONTROL_PLANE_API_KEY` al processo attraverso un gestore di segreti o una variabile d'ambiente privata.

```bash
# ID di esempio, NON funzionante: sostituiscilo sul tuo computer.
tunnel-client init \
  --sample sample_mcp_stdio_local \
  --profile clima-one-rea \
  --tunnel-id TUNNEL_ID_DEL_TUO_ACCOUNT \
  --mcp-command "rea mcp"

tunnel-client doctor --profile clima-one-rea --explain
tunnel-client run --profile clima-one-rea
```

L'ultima riga deve **rimanere in esecuzione** affinché ChatGPT possa raggiungere REA. Il collegamento verso OpenAI è HTTPS **in uscita** e non richiede porte pubbliche in ingresso.

5. Da ChatGPT sul web: **Plugins → + → Add custom MCP server → Connection: Tunnel**. Seleziona il tuo tunnel o incolla *solo nell'interfaccia prevista* il tunnel ID, configura l'autenticazione richiesta, conferma l'avviso di rischio, poi crea e installa il plugin.
6. Verifica che compaiano realmente gli strumenti REA ed esegui una semplice ricerca sulle capacità disponibili **prima** di caricare qualsiasi APK. Se gli strumenti non appaiono, controlla `tunnel-client doctor`, associazione workspace/organizzazione e permessi. Non affermare di aver collegato il plugin senza una chiamata riuscita da ChatGPT.

La procedura ufficiale OpenAI è qui:
- https://developers.openai.com/api/docs/guides/secure-mcp-tunnels
- https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt

## 4. Protezione e limiti

- **Mai** esporre `rea mcp` come servizio HTTP pubblico privo di autenticazione. REA lavora sui file/processi a cui il suo utente locale ha accesso; il tunnel deve restare associato solo agli account autorizzati.
- Esegui REA come utente senza privilegi amministrativi, preferibilmente su computer o VM dedicata; concedi accesso solo ai campioni che vuoi analizzare.
- Non caricare nel repository pubblico account SmartHome, token/key Midea, dump di memoria, HAR contenenti sessioni, firmware proprietari o APK privati. Il workflow GitHub verifica **solo** la CLI REA; non raccoglie i dati del climatizzatore.
- **REA non è presente attualmente nel catalogo plugin del tuo account** come plugin pronto: questa configurazione prepara un server MCP **privato**, non installa un plugin immediatamente utilizzabile in questa conversazione.
- Gli sviluppatori devono consultare i limiti legali e la licenza del software analizzato; preferire campioni propri o esplicitamente autorizzati.
- Con una macchina collegata, comandi legati a compressore/inverter/fan esterna devono rimanere nelle funzioni ufficiali e con verifica fisica delle protezioni.

## 5. Stato di verifica del repository

Il workflow `.github/workflows/rea-mcp-check.yml` usa Node 24 e verifica che la versione REA indicata sia effettivamente installabile e invocabile su GitHub Actions. **Non** crea un tunnel: un runner GitHub termina alla fine del job, e non può mantenere un MCP server locale costantemente disponibile per ChatGPT.

**Non inviare credenziali qui.** Una volta che sul tuo computer compaiono `rea --version` e `tunnel-client doctor --profile clima-one-rea --explain` senza errori, la parte finale è aggiungere il tunnel come plugin nell'interfaccia ChatGPT, se il tuo account è abilitato.
