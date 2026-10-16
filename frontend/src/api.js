const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

async function request(path, options = {}, allowRetry = true) {
  const token = window.localStorage.getItem("accessToken");
  const headers = {
    ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401 && allowRetry && window.localStorage.getItem("refreshToken")) {
    const refreshed = await tryRefreshTokens();
    if (refreshed) {
      return request(path, options, false);
    }
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message ?? `Request failed with ${response.status}`);
  }

  return response.status === 204 ? null : response.json();
}

async function tryRefreshTokens() {
  const refreshToken = window.localStorage.getItem("refreshToken");
  if (!refreshToken) return false;

  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;
    const data = await response.json();
    window.localStorage.setItem("accessToken", data.accessToken);
    window.localStorage.setItem("refreshToken", data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

export function clearSession() {
  window.localStorage.removeItem("accessToken");
  window.localStorage.removeItem("refreshToken");
  window.localStorage.removeItem("user");
}

export function storeSession(result) {
  window.localStorage.setItem("accessToken", result.accessToken);
  window.localStorage.setItem("refreshToken", result.refreshToken);
  if (result.user) {
    window.localStorage.setItem("user", JSON.stringify(result.user));
  }
}

export async function getCurrentUser() {
  return request("/api/auth/me");
}

export async function refreshToken() {
  const refreshed = await tryRefreshTokens();
  if (!refreshed) {
    throw new Error("Session expired. Please log in again.");
  }
  return JSON.parse(window.localStorage.getItem("user") ?? "null");
}

export async function login({ email, password }) {
  const result = await request("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  storeSession(result);
  return result;
}

export async function register({ email, password }) {
  const result = await request("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  storeSession(result);
  return result;
}

export async function listDocuments() {
  const documents = await request("/api/documents");
  return documents.map(normalizeDocument);
}

export async function uploadDocument(file) {
  const formData = new FormData();
  formData.append("file", file);

  return request("/api/documents", {
    method: "POST",
    body: formData,
  }).then(normalizeDocument);
}

export async function deleteDocument(documentId) {
  return request(`/api/documents/${documentId}`, {
    method: "DELETE",
  });
}

export async function renameDocument(documentId, filename) {
  return request(`/api/documents/${documentId}`, {
    method: "PATCH",
    body: JSON.stringify({ filename }),
  }).then(normalizeDocument);
}

export async function reindexDocument(documentId) {
  return request(`/api/documents/${documentId}/reindex`, {
    method: "POST",
  }).then(normalizeDocument);
}

export async function downloadDocument(documentId, filename) {
  const token = window.localStorage.getItem("accessToken");
  const response = await fetch(`${API_BASE_URL}/api/documents/${documentId}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (response.status === 401) {
    const refreshed = await tryRefreshTokens();
    if (refreshed) {
      return downloadDocument(documentId, filename);
    }
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message ?? `Download failed with ${response.status}`);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const anchor = window.document.createElement("a");
  anchor.href = url;
  anchor.download = filename ?? "document";
  window.document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(url);
}

export async function askQuestion(question, options = {}) {
  const { tool = "rag", documentIds = [], conversationId } = options;
  return request("/api/chat", {
    method: "POST",
    body: JSON.stringify({ question, tool, documentIds, conversationId }),
  });
}

export async function askQuestionStream(question, options = {}) {
  const { tool = "rag", documentIds = [], conversationId, onToken } = options;
  const body = JSON.stringify({ question, tool, documentIds, conversationId });

  const post = () => {
    const token = window.localStorage.getItem("accessToken");
    return fetch(`${API_BASE_URL}/api/chat/query/stream`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body,
    });
  };

  let response = await post();
  if (response.status === 401 && window.localStorage.getItem("refreshToken")) {
    const refreshed = await tryRefreshTokens();
    if (refreshed) {
      response = await post();
    }
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message ?? `Request failed with ${response.status}`);
  }
  if (!response.body) {
    throw new Error("Streaming is not supported by this browser.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let finalPayload = null;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    for (const frame of frames) {
      let eventName = "message";
      let data = "";
      for (const line of frame.split("\n")) {
        if (line.startsWith("event:")) eventName = line.slice("event:".length).trim();
        else if (line.startsWith("data:")) data += line.slice("data:".length).trim();
      }
      if (!data) continue;
      let payload;
      try {
        payload = JSON.parse(data);
      } catch {
        continue;
      }
      if (eventName === "token" && typeof payload === "string") {
        onToken?.(payload);
      } else if (eventName === "token" && payload.content) {
        onToken?.(payload.content);
      } else if (eventName === "done") {
        finalPayload = payload;
      } else if (eventName === "error") {
        throw new Error(payload.detail ?? "Streaming failed");
      }
    }
  }

  if (!finalPayload) {
    throw new Error("Stream ended without a final answer");
  }
  return finalPayload;
}

export async function listConversations() {
  return request("/api/chat/conversations");
}

export async function getConversation(conversationId) {
  return request(`/api/chat/conversations/${conversationId}`);
}

export async function createConversation(title = "New conversation") {
  return request("/api/chat/conversations", {
    method: "POST",
    body: JSON.stringify({ title }),
  });
}

export async function deleteConversation(conversationId) {
  return request(`/api/chat/conversations/${conversationId}`, {
    method: "DELETE",
  });
}

export async function searchDocuments({ query, documentId, contentType, limit = 8 }) {
  return request("/api/search", {
    method: "POST",
    body: JSON.stringify({
      query,
      documentId: documentId || null,
      contentType: contentType || null,
      limit,
    }),
  });
}

export async function getPageChunks(documentId, page) {
  return request(`/api/documents/${documentId}/chunks?page=${page}`);
}

export async function getDocumentTables(documentId) {
  return request(`/api/documents/${documentId}/tables`);
}

export async function searchImages({ query = "", documentId, limit = 12 } = {}) {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  if (documentId) params.set("documentId", String(documentId));
  return request(`/api/images/search?${params.toString()}`);
}

export async function fetchImageObjectUrl(imageId) {
  const token = window.localStorage.getItem("accessToken");
  const response = await fetch(`${API_BASE_URL}/api/images/${imageId}/content`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`Image load failed with ${response.status}`);
  }
  return window.URL.createObjectURL(await response.blob());
}

export async function listConversationHistory() {
  return request("/api/chat/history");
}

function normalizeDocument(document) {
  return {
    ...document,
    uploadTime: document.uploadTime ?? document.upload_time ?? document.uploadedAt,
    status: document.status ?? (document.chunks > 0 ? "indexed" : "indexing"),
    chunks: document.chunks ?? 0,
    contentType: document.contentType ?? "application/octet-stream",
    errorMessage: document.errorMessage ?? "",
  };
}
