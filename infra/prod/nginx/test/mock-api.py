import base64
import hashlib
import socket
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class MockApiHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path.startswith("/api/v1/notifications/match/stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(b"data: first\n\n")
            self.wfile.flush()
            time.sleep(2)
            self.wfile.write(b"data: second\n\n")
            self.wfile.flush()
            self.close_connection = True
            return

        if self.headers.get("Upgrade", "").lower() == "websocket":
            client_key = self.headers.get("Sec-WebSocket-Key", "")
            accept_source = f"{client_key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            accept_key = base64.b64encode(hashlib.sha1(accept_source.encode()).digest()).decode()
            self.send_response(101)
            self.send_header("Upgrade", "websocket")
            self.send_header("Connection", "Upgrade")
            self.send_header("Sec-WebSocket-Accept", accept_key)
            self.end_headers()
            self.wfile.flush()
            time.sleep(2)
            self.close_connection = True
            return

        body = f"Hostname: {socket.gethostname()}\n".encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *_args):
        return


ThreadingHTTPServer(("0.0.0.0", 8080), MockApiHandler).serve_forever()
