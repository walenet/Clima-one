# CLIMA ONE — Architettura accoppiamento e controllo (18/10/2026)

## Obiettivo
Un'app Android autonoma che scopre, associa e controlla climatizzatori Midea e OEM compatibili, senza obbligare l'utente a installare SmartHome, Home Assistant, un computer o un gateway. Supporto iOS come fase successiva. Nessun codice di questa roadmap dimostra già un pairing V3 pienamente offline.

## Distinzione non negoziabile
- **Senza app del produttore:** obiettivo primario. CLIMA ONE gestisce onboarding, eventuale primo recupero credenziali, controllo.
- **Senza cloud del produttore, neppure la prima volta:** *non garantito* per firmware V3 originali. La connessione V3 richiede token e key che i software di riferimento ottengono normalmente da API cloud Midea. Le chiavi non sono ricavabili dal solo indirizzo IP e dalla risposta di discovery.
- **Solo rete locale dopo la prima autorizzazione:** realizzabile con token/key validi conservati nel Keystore; richiede prove hardware e può decadere se le chiavi vengono invalidate/reset.

## Percorsi utente
1. **Climatizzatore già connesso al Wi-Fi (caso pilota: net_ac_4120, V3, 192.168.0.223).** Ricerca UDP 6445/20086 -> identificazione ID, tipo, protocollo -> tentativo di ottenere credenziali V3 con procedura automatica gestita nell'app, senza richiedere SmartHome, usando solo API e autorizzazioni legittime supportate -> handshake LAN TCP 6444 -> query stato -> capability probing -> pannello. Se il recupero credenziali fallisce, mostra motivazione vera e propone importazione protetta di token/key o opzioni esplicite. NON simulare letture.
2. **Nuovo climatizzatore.** Wizard BLE se dispositivo/modulo espone protocollo di provisioning compatibile; altrimenti modalità AP Wi-Fi del climatizzatore con Android WifiNetworkSpecifier + provisioning protocol da validare per specifico modulo. Non resettare dispositivi già collegati. Verificare effettivo join a rete 2,4 GHz. La configurazione Wi-Fi non elimina da sola l'autenticazione V3.
3. **V1/V2 supportati.** Comunicazione LAN diretta se modello/protocollo supporta.
4. **V3 cloudless rigoroso.** Percorso di ricerca separato, non promesso come universale; BLE control soltanto su hardware compatibile oppure moduli alternativi con consenso proprietario.

## Strati software
- DeviceDiscovery: UDP Wi-Fi e (opzionale) BLE scanner con permessi Android moderni.
- ProvisioningManager: AP/BLE onboarding, permessi, ripristino Wi-Fi del telefono, timeouts, no storage password Wi-Fi in log.
- AuthManager: V3 bootstrap scelto consapevolmente, credentials encrypted Android Keystore; mai credenziali o token in screenshot/log/export non criptato.
- MideaLanTransport: gestione sessioni/handshake, verifica integrità, timeout, keepalive e reconnect.
- FeatureDiscovery: capabilities, distinguere supported / unsupported / non validated.
- Telemetry: only real returned measurements; units and units-source; no invented W/Hz/kWh.
- UI: discovery, onboarding, dashboard, settings, capability cards, diagnostics, event history.
- Tests: fixture protocol V2/V3; Android 16 instrumented; pairing test sui modelli pilota e OEM. Disable firmware-write and inverter-limit-change.

## Fasi
- P0: validare discovery Midea V3 e versioni hardware (FATTO sul pilota).
- P1: implementare e testare bootstrap V3 senza app SmartHome; se possibile primo key retrieval trasparente e dichiarato; handshake verificato e stato reale.
- P2: diagnostica e controls standard protetti; riconnessione, capability detection.
- P3: onboarding AP/BLE con verifiche modello per modello; nessuna procedura di reset distruttiva automatica.
- P4: funzioni evolute (consumi e tariffe, grafici, automazioni locali, ECO/Breeze se supportati).
- P5: plugin OEM e aggiornamenti firmati dell'app; nessuna scrittura firmware split.

## Criterio di qualità
Un APK che compila non basta. Richiedere test su telefono reale, unità hardware reali, pairing riuscito, query reale, comando standard e read-back di conferma. Dichiarare compatibilità in base a modelli testati, non solo etichetta OEM.

## Riferimenti tecnici
- https://pypi.org/project/msmart-ng/
- https://github.com/midea-lan/midea-local
- https://github.com/fiam/home-assistant-midea-connect
- https://github.com/dooblpls/klima-ble
- https://www.home-assistant.io/integrations/midea
