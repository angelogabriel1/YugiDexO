class SseHub {
  clients = new Map();

  subscribe(username, response) {
    const key = username.toLowerCase();
    const listeners = this.clients.get(key) ?? new Set();
    listeners.add(response);
    this.clients.set(key, listeners);
    response.write(`event: connected\ndata: ${JSON.stringify({ username: key })}\n\n`);
    return () => {
      listeners.delete(response);
      if (!listeners.size) this.clients.delete(key);
    };
  }

  publish(username, payload = 'reload') {
    const listeners = this.clients.get(username.toLowerCase()) ?? [];
    for (const response of listeners) response.write(`data: ${payload}\n\n`);
  }
}

export const sseHub = new SseHub();
