# CLIMA ONE 0.5 — Controlli sicuri e modalità TECNICO (18 ottobre 2026)

## Già confermato sul pilota
- Ricerca del Midea net_ac_4120 a 192.168.0.223, V3.
- Bootstrap da account Midea necessario una volta in questa versione; token/key sono salvati nel Keystore del telefono.
- Alla riapertura le chiavi locali funzionano e la lettura via TCP LAN 6444 restituisce dati reali.
- Frequenza compressore, target, rpm equivalenti e potenza sono valori *decodificati*, non ancora validati con strumenti esterni.

## Nuove funzioni implementate v0.5
1. Pulsanti con ripple Android, feedback tattile e colori differenziati, minimo 56dp, bordi con contrasto.
2. Controllo standard ventola interna Auto / 20 / 40 / 60 / 80 / 100% in raffrescamento, riscaldamento e ventilazione **se il firmware accetta quel comando**. Nessun aumento oltre 100% di fabbrica.
3. ECO+ e ECO++ software: in COOL alzano setpoint di +1/+2 °C e applicano ECO standard, in HEAT abbassano di -1/-2 °C, con ventola AUTO e Turbo spento. L'utente conferma prima di inviare; la risposta viene riletta via LAN. Non è un limite fisso al numero di giri del compressore.
4. CSV di 100 campioni durante la sessione, prodotto da letture LAN autenticate. Non contiene segreti e marca la potenza come stimata dal protocollo.
5. Diagnostica tecnico e scheda copiabile/condivisibile.

## Funzioni volutamente NON abilitate
- BreezeleSS durante HEAT, fino a verifica di B5 e comportamento hardware; attualmente tasto informativo.
- Riduzione della frequenza minima impostata di fabbrica del compressore, controllo diretto della ventola esterna, scritture firmware/protezioni. Non disponibili attraverso i comandi standard di questo progetto, non vanno simulati né forzati.
- GEAR 50% / 75%, iECO e profili B1: possono esistere su modelli Midea compatibili ma servono identificazione capacità (B5), formato dei comandi e test in campo prima di attivazione.

## Percorso tecnico successivo
1. Misurare la reazione della ventola interna a 20/40/60/80/100 via LAN, confermare sul climatizzatore.
2. Controllare se ECO nativo viene effettivamente letto e mantenuto.
3. Decodificare in sola lettura la capability list B5 del pilota; non esporre switch non confermati.
4. Registrare trend, temperature scambiatore, flusso e diagnostica errori soltanto se i campi sono supportati e validati.
5. Introdurre firma APK persistente conservata in segreti GitHub per aggiornamenti in-place. Gli APK debug firmati dal runner potrebbero richiedere disinstallazione (perdita di chiavi locali), quindi non eliminare l'app installata finché non si è pronti a riautenticarsi.
6. Rendere i display affidabili con test su hardware reale e strumenti.
