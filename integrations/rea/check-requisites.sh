#!/usr/bin/env bash
# Read-only prerequisite checker for the privately hosted REA stdio MCP server.
# It never installs software, opens network ports, or reads API credentials.
set -euo pipefail

if ! command -v node >/dev/null 2>&1; then
  echo "ERRORE: Node.js mancante. Installa Node 24.11+ (consigliato)." >&2
  exit 1
fi
if ! node -e '
const p=process.versions.node.split(".").map(Number);
if(!((p[0]===22&&p[1]>=19)||(p[0]===24&&p[1]>=11)||p[0]>=26))process.exit(1);
'; then
  echo "ERRORE: Node.js $(node --version) non supportato da REA." >&2
  echo "Richiesti: Node 22.19+, 24.11+ oppure 26+." >&2
  exit 1
fi
echo "OK: Node $(node --version)"

if command -v npm >/dev/null 2>&1; then
  echo "OK: npm $(npm --version)"
else
  echo "ERRORE: npm non trovato." >&2
  exit 1
fi

if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | head -1
  echo "NOTA: per l'analisi APK occorrono JDK 17+ e JADX headless verificato."
else
  echo "NOTA: JDK 17+ non trovato; serve per l'analisi APK."
fi

if command -v rea >/dev/null 2>&1; then
  echo "OK: REA $(rea --version)"
  echo "Puoi provare 'rea doctor --json' e il test MCP stdio."
else
  echo "REA non installato. Installazione manuale (versione fissata):"
  echo "  npm install --global rea-agents@6.7.0"
fi

if command -v tunnel-client >/dev/null 2>&1; then
  echo "OK: tunnel-client sul PATH"
else
  echo "tunnel-client non installato: https://github.com/openai/tunnel-client/releases/latest"
fi
if [[ "${OSTYPE:-}" == linux* ]] && ! command -v ps >/dev/null 2>&1; then
  echo "NOTA: REA su Linux richiede il comando ps (procps)."
fi
echo "Controllo locale completato. Nessuna modifica o richiesta di credenziali."
