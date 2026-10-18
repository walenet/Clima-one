package com.climaone.app.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Read-only connectivity checks for an already-configured Midea module.
 * An open TCP port is NOT equivalent to a cryptographically authenticated session.
 */
public final class DirectPairing {
    private DirectPairing() {}

    public static final class Result {
        public final boolean reachable;
        public final String description;
        public Result(boolean reachable, String description) {
            this.reachable = reachable;
            this.description = description;
        }
    }

    public static Result probe(MideaLan.Device device) {
        if (device == null || device.ip == null)
            return new Result(false, "Nessun climatizzatore selezionato.");
        if (!device.compatible())
            return new Result(false, "Tipo di climatizzatore non supportato da questa versione.");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(device.ip, device.port), 2500);
            return new Result(true, "Il modulo risponde su " + device.ip + ":" + device.port +
                    ". La connessione TCP e raggiungibile. Protocollo V" + device.version +
                    (device.version == 3
                    ? ": serve ancora un token e una chiave V3 validi per leggere dati e inviare comandi."
                    : ": ora e possibile tentare la lettura di stato."));
        } catch (IOException e) {
            return new Result(false, "La porta LAN " + device.port + " non risponde su " +
                    device.ip + ". Controlla Wi-Fi locale, isolamento dispositivi e IP. " +
                    e.getClass().getSimpleName());
        }
    }

    public static boolean hasCredentials(MideaLan.Device device) {
        if (device == null || device.version != 3) return device != null;
        try {
            return MideaLan.hex(device.token).length == 64 &&
                   MideaLan.hex(device.key).length == 32;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Returns real data only; never marks discovery or an open TCP port as a successful pairing. */
    public static MideaLan.State verifyRead(MideaLan.Device device) throws Exception {
        if (device == null) throw new IOException("Nessun climatizzatore selezionato.");
        if (device.version == 3 && !hasCredentials(device))
            throw new IOException("Il V3 richiede token e key validi; il rilevamento Wi-Fi non li fornisce.");
        return MideaLan.readState(device);
    }

    public static String explainFailure(Exception error) {
        if (error == null) return "Connessione non riuscita.";
        if (error instanceof java.net.SocketTimeoutException) return
                "Timeout: il modulo non ha risposto. Controlla la connessione Wi-Fi e riprova.";
        if (error instanceof java.net.ConnectException) return
                "Impossibile aprire la connessione LAN. Verifica IP e rete locale.";
        if (error instanceof SecurityException) return
                "Verifica crittografica fallita: token/key non validi o sessione incompatibile.";
        String msg = error.getMessage();
        if (msg == null || msg.length() == 0) return error.getClass().getSimpleName();
        // Keep credentials and passwords out of user-facing diagnostics.
        if (msg.length() > 180) msg = msg.substring(0, 180) + "...";
        return msg;
    }
}
