"""Static server for the viewer, plus a POST /save endpoint so renders land on disk.

    python serve.py <viewer-dir> [port]

The canvas keeps `preserveDrawingBuffer`, so the page can hand back a finished PNG. Pulling it out
through the browser tool as a base64 string does work but costs ~385 KB of round-trip per image;
posting it to the server that is already running costs nothing and puts the file straight where the
next step wants it.
"""

import base64
import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from functools import partial


class Handler(SimpleHTTPRequestHandler):
    def do_POST(self):
        if not self.path.startswith('/save'):
            self.send_error(404)
            return
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length).decode('utf-8', 'replace')
        name = self.path.partition('?')[2] or 'render.png'
        name = os.path.basename(name) or 'render.png'
        if not name.endswith('.png'):
            name += '.png'
        data = body.split(',', 1)[1] if ',' in body else body
        out = os.path.join(self.directory, 'shots', name)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, 'wb') as fh:
            fh.write(base64.b64decode(data))
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.end_headers()
        self.wfile.write(out.encode())

    def log_message(self, *args):
        pass                                        # the browser tool is noisy enough already


if __name__ == '__main__':
    directory = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8731
    server = ThreadingHTTPServer(('127.0.0.1', port), partial(Handler, directory=directory))
    print('serving %s on http://127.0.0.1:%d  (POST /save?name.png to store a render)'
          % (directory, port))
    server.serve_forever()
