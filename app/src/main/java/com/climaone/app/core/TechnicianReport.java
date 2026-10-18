package com.climaone.app.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Read-only technical record: LAN discovery metadata and measurements actually
 * returned by the unit. Never include tokens, keys, account or Wi-Fi passwords.
 */
public final class TechnicianReport {
    private TechnicianReport() {}

    private static String value(Double v, String unit) {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v))
            return "non disponibile / non verificato";
        return String.format(Locale.ITALY, "%.1f %s", v, unit);
    }
    private static String value(double v, String unit) {
        return value(Double.valueOf(v), unit);
    }
    private static String mode(int m) {
        switch (m) {
            case 1: return "Auto";
            case 2: return "Raffrescamento";
            case 3: return "Deumidificazione";
            case 4: return "Riscaldamento";
            case 5: return "Ventilazione";
            default: return "Codice modalita " + m;
        }
    }
    public static String build(MideaLan.Device dev, MideaLan.State state, long readTimestamp) {
        StringBuilder s = new StringBuilder();
        s.append("CLIMA ONE | SCHEDA TECNICA (ALPHA)\n");
        s.append("Generata: ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(new Date())).append("\n\n");
        s.append("IDENTIFICAZIONE DA RETE LOCALE\n");
        if (dev != null) {
            s.append("Identificativo rete: ").append(dev.name).append("\n");
            s.append("IP locale: ").append(dev.ip).append("\n");
            s.append("Protocollo: Midea V").append(dev.version).append("\n");
            s.append("Porta LAN: ").append(dev.port).append("\n");
        } else {
            s.append("Nessuna unita selezionata\n");
        }
        s.append("Marca commerciale / modello preciso / seriale: non identificabili dalla sola ricerca LAN\n\n");
        s.append("LETTURE DELL'UNITA\n");
        if (state == null) {
            s.append("Non disponibili: autenticazione e lettura di stato non completate.\n");
        } else {
            s.append("Lettura effettuata: ").append(readTimestamp > 0
                ? new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(new Date(readTimestamp))
                : "ora non registrata").append("\n");
            s.append("Stato: ").append(state.power ? "Acceso" : "Spento").append("\n");
            s.append("Modalita: ").append(mode(state.mode)).append("\n");
            s.append("Setpoint: ").append(value(state.setpoint, "°C")).append("\n");
            s.append("Temperatura ambiente: ").append(value(state.indoor, "°C")).append("\n");
            s.append("Temperatura esterna: ").append(value(state.outdoor, "°C")).append("\n");
            s.append("Ventilazione: ").append(state.fan == 102 ? "Automatica" : String.valueOf(state.fan) + " (codice protocollo)").append("\n");
            s.append("Frequenza compressore: ").append(value(state.compressorHz, "Hz")).append("\n");
            s.append("Frequenza obiettivo: ").append(value(state.compressorTargetHz, "Hz")).append("\n");
            s.append("Velocita ventilatore (interpretazione protocollo): ").append(value(state.fanRpm, "rpm equivalenti")).append("\n");
            s.append("Potenza (dato da protocollo, da validare): ").append(value(state.watts, "W")).append("\n");
            s.append("Energia (dato da protocollo, da validare): ").append(value(state.kwh, "kWh")).append("\n");
            s.append("Segnale filtro: ").append(state.filterAlert ? "Flag presente, da verificare" : "Flag non rilevato").append("\n");
        }
        s.append("\nDIAGNOSTICA NON DISPONIBILE AUTOMATICAMENTE\n");
        s.append("Pressioni refrigerante, surriscaldamento, sottoraffreddamento, carica, assorbimento elettrico certificato, perdite e codici errore non verificati.\n");
        s.append("Per diagnosi tecniche e sicurezza occorrono strumentazione adeguata, documentazione del modello e verifiche in campo.\n\n");
        s.append("AVVERTENZE\n");
        s.append("Report informativo di un software sperimentale, NON certificazione FGAS, NON rapporto manutenzione, NON diagnosi ufficiale del costruttore.\n");
        s.append("Nessun token o password incluso. Nessun comando di scrittura eseguito per produrre la scheda.\n");
        return s.toString();
    }
}
