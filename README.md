# CLIMA ONE

Progetto Android sperimentale per comunicazione LAN con climatizzatori compatibili Midea V2/V3, con interfaccia utente e modalità tecnico.

## Integrazione REA (Reverse Engineer Anything)

**[Configurazione REA come server MCP privato per ChatGPT](integrations/rea/README.md)**

Il progetto contiene:
- istruzioni in italiano per installare REA **sul proprio computer/server**;
- setup tramite **Secure MCP Tunnel** OpenAI, senza esporre porte di analisi;
- script read-only per controllare i prerequisiti;
- test MCP reale (initialize + tools/list) su GitHub Actions;
- precauzioni per chiavi V3, account SmartHome, APK e acquisizioni diagnostiche.

**Stato:** l'integrazione è predisposta e il test automatico MCP locale è passato; per renderla disponibile come plugin nel proprio ChatGPT bisogna aggiungere un tunnel personale nell'interfaccia Plugins, con le autorizzazioni richieste. Non è un plugin già installato in ChatGPT e non è un componente dell'APK.

## Sicurezza e limiti

La decodifica LAN e le funzioni HVAC avanzate sono sperimentali. Misure di compressore, potenza e ventilazione vanno confrontate con gli strumenti prima di qualsiasi decisione tecnica. Non caricare password, token SmartHome, chiavi V3 o registrazioni di rete con credenziali su GitHub.

Per collaborare allo sviluppo consultare il codice Android in [`app/`](app/) e gli strumenti separati in [`integrations/`](integrations/).
