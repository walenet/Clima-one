# CLIMA ONE — Priorità: piattaforma per tecnici HVAC

Aggiornato: 18 ottobre 2026.

## Il posizionamento
CLIMA ONE nasce con due modalità:
- **Cliente:** gestione semplice dei propri climatizzatori compatibili.
- **TECNICO (priorità di sviluppo):** diagnostica guidata, monitoraggio, acquisizione prove, storico, report. Accesso esplicito e solo operazioni di lettura durante il primo sviluppo.

## Funzioni tecnico, in ordine di realizzazione

### P0: pairing e comunicazione verificata
- Rilevamento Midea V2/V3, ID di rete, indirizzo IP locale, versione protocollo.
- V3 con token/key validi ottenuti in modo lecito e chiaro all'utente, possibilmente senza app SmartHome; cloudless assoluto **non garantito** con firmware V3 originali.
- Handshake LAN e lettura confermata da climatizzatore reale; errori di autenticazione/timeout distinguibili.
- Test hardware pilota Midea BreezeleSS+ identificato come net_ac_4120 protocollo V3. La corrispondenza modulo↔modello non può essere automaticamente dedotta dal nome rete.

### P1: scheda diagnostica e prova di funzionamento
- Dati modulo (IP, protocollo) prima dell'autenticazione, **senza** inventare il modello.
- Dopo lettura autentica: on/off, modalità, setpoint, aria ambiente, temperatura esterna quando presenti.
- Sensori optional solo dopo decodifica **specifica e verificata**: Hz compressore, Hz obiettivo, rpm ventilatore, W, kWh, temperatura batteria, scarico, corrente assorbita, passi valvola elettronica e flag filtrazione. Le voci non esposte devono apparire come "non disponibile".
- Scheda tecnica condivisibile, identità e credenziali segrete mai incluse.
- Ogni parametro dovrà riportare unità di misura, origine, timestamp e stato di validazione.

### P2: registrazione, confronto, allarmi
- Campionamento periodico con intervalli configurabili e limiti per non sovraccaricare il climatizzatore.
- Trend di temperature, potenza, frequenza e ventilazione; esportazione CSV.
- Rilevamento di valori inaspettati come **avviso da verificare**, non diagnosi automatica definitiva.
- Registro errori solo dove il firmware lo espone effettivamente; nessun codice guasto inventato.
- Supporto multi-unità, confronto prestazioni, rapporti sopralluogo, note tecnico/cliente salvate localmente e condivise esplicitamente.

### P3: strumentazione esterna e assistenza avanzata
- Interoperabilità documentata con strumenti Bluetooth/USB o API autorizzate (es. sonde termometriche, manifold, pinze amperometriche) soltanto per dispositivi e protocolli realmente supportati.
- Dati di pressione, SH/SC, carica refrigerante **non ricavati dal Wi-Fi** se non forniti da sensori reali affidabili.
- Rapporto lavoro separato da adempimenti FGAS e certificazioni legali: i dati da LAN non costituiscono documentazione normativa sostitutiva.
- Libreria schede/codici guasto validata per modello e revisione firmware.

### P4: funzioni intelligenti sicure
- Diagnostica predittiva quando esistono dati sufficienti e modelli validati.
- Stima resa termica/COP solo con sonde e misure necessarie; senza dati sufficienti l'app si astiene.
- Funzioni remote autorizzate e audit operazioni.

## Cose che CLIMA ONE non farà implicitamente
- Nessuna modifica firmware, tarature di fabbrica, limiti di protezione inverter, rpm/Hz forzati, pressostati e sicurezze.
- Nessuna pretesa di compatibilità di tutte le macchine OEM solo perché condividono Midea come fornitore.
- Nessun valore inventato o etichettato come misura se è una stima.
- Nessuna memorizzazione in chiaro di password, token e key. I report non contengono credenziali.

## Implementazione attuale
- **Realizzato nel sorgente:** ricerca LAN, schermata TECNICO, identificazione protocollo, scheda con campi assenti espliciti, esportazione testuale solo su scelta dell'utente, prima decodifica sperimentale di alcuni gruppi telemetrici.
- **Da testare su unità reale:** V3 autenticato, lettura di stato, accuratezza delle metriche specialistiche, azioni controllo.
- **Non realizzato:** trend CSV, codice guasto validato, integrazione strumenti esterni, diagnostica certificata, provisioning universale senza cloud.
