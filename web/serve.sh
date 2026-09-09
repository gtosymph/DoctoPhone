#!/usr/bin/env bash
# Lance un serveur HTTP local pour tester Health Analyzer — Web.
# Le port 8000 est nécessaire : les Web Workers et l'accès aux fichiers en
# `webkitdirectory` échouent sous `file://`.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-8000}"

echo "Health Analyzer — Web"
echo "Serveur local sur http://localhost:${PORT}"
echo "Arrêtez avec Ctrl+C."
echo

# Le serveur refuse la mise en cache. Sans cela, le navigateur garde d'anciens scripts
# après une modification, et l'on teste une version qui n'est plus celle du disque.
python3 - "$DIR" "$PORT" <<'PY'
import functools, http.server, socketserver, sys

directory, port = sys.argv[1], int(sys.argv[2])


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    """Sert les fichiers du dossier web, en interdisant toute mise en cache."""

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, fmt, *args):
        # Le journal par défaut noie la console sous une ligne par fichier servi.
        if not args or not str(args[0]).startswith("GET"):
            super().log_message(fmt, *args)


socketserver.TCPServer.allow_reuse_address = True
handler = functools.partial(NoCacheHandler, directory=directory)
with socketserver.TCPServer(("", port), handler) as server:
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServeur arrêté.")
PY
