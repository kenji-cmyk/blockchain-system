const api = {
  get: (path) => request(path),
  post: (path, body = {}) =>
    request(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    }),
  put: (path, body = {}) =>
    request(path, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    }),
  delete: (path) => request(path, { method: "DELETE" })
};

async function request(path, options = {}) {
  const response = await fetch(path, options);
  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok) {
    throw new Error(payload?.message || payload?.error || `Request failed with ${response.status}`);
  }
  return payload;
}

export { api };
