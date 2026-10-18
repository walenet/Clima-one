package com.climaone.app.core;

/**
 * Standard-command setpoint profiles. Never writes a compressor frequency,
 * outdoor fan speed, service register or firmware override.
 */
public final class SafeEco {
    private SafeEco() {}

    public static MideaLan.State plan(MideaLan.State actual, int level) {
        if (actual == null) throw new IllegalArgumentException("Prima leggi lo stato reale");
        if (level != 1 && level != 2) throw new IllegalArgumentException("Livello ECO non valido");
        if (!actual.power) throw new IllegalArgumentException("Prima accendi il climatizzatore");
        if (actual.mode != 2 && actual.mode != 4)
            throw new IllegalArgumentException("ECO+ disponibile solo in Raffrescamento o Riscaldamento");
        MideaLan.State result = actual.copy();
        // Reduce heating/cooling demand, never bypass native inverter protections.
        if (result.mode == 2) {
            result.setpoint = Math.min(30, Math.max(17, result.setpoint + level));
            result.eco = true; // Standard Midea ECO only in cooling mode.
        } else {
            result.setpoint = Math.max(18, Math.min(30, result.setpoint - level));
            // Do not force cooling-specific ECO onto heating firmware.
        }
        result.fan = 102; // Standard AUTO fan
        result.turbo = false;
        return result;
    }

    public static boolean indoorFanAdjustable(MideaLan.State actual) {
        return actual != null && actual.power &&
               (actual.mode == 2 || actual.mode == 4 || actual.mode == 5);
    }

    public static MideaLan.State withIndoorFan(MideaLan.State actual, int percent) {
        if (!indoorFanAdjustable(actual))
            throw new IllegalArgumentException("Regolazione ventilatore disponibile in Freddo, Caldo o Ventilazione con macchina accesa");
        if (percent != 20 && percent != 40 && percent != 60 &&
            percent != 80 && percent != 100 && percent != 102)
            throw new IllegalArgumentException("Valore ventilazione non standard");
        MideaLan.State out = actual.copy();
        out.fan = percent;
        return out;
    }
}
