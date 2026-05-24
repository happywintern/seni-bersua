const STORAGE_API_BASE_URL = "sucash_web_api_base_url";
const STORAGE_OUTLET_ID = "sucash_web_outlet_id";
const DEFAULT_OUTLET_ID = "default";

function normalizeBaseUrl(value) {
  return (value || "").trim().replace(/\/+$/, "");
}

export function getApiBaseUrl() {
  const fromStorage = typeof window !== "undefined"
    ? window.localStorage.getItem(STORAGE_API_BASE_URL)
    : "";
  const fromEnv = import.meta.env.VITE_API_BASE_URL || "";
  const base = normalizeBaseUrl(fromStorage || fromEnv);
  if (base) return base;
  if (typeof window !== "undefined") return normalizeBaseUrl(window.location.origin);
  return "";
}

export function setApiBaseUrl(value) {
  if (typeof window === "undefined") return;
  const normalized = normalizeBaseUrl(value);
  if (normalized) {
    window.localStorage.setItem(STORAGE_API_BASE_URL, normalized);
  } else {
    window.localStorage.removeItem(STORAGE_API_BASE_URL);
  }
}

export function getOutletId() {
  if (typeof window === "undefined") return DEFAULT_OUTLET_ID;
  return (window.localStorage.getItem(STORAGE_OUTLET_ID) || DEFAULT_OUTLET_ID).trim() || DEFAULT_OUTLET_ID;
}

export function setOutletId(value) {
  if (typeof window === "undefined") return;
  const normalized = (value || "").trim() || DEFAULT_OUTLET_ID;
  window.localStorage.setItem(STORAGE_OUTLET_ID, normalized);
}

export function getWebServerSettings() {
  return {
    apiBaseUrl: getApiBaseUrl(),
    outletId: getOutletId(),
  };
}

export function buildApiUrl(path) {
  const safePath = path.startsWith("/") ? path : `/${path}`;
  return `${getApiBaseUrl()}${safePath}`;
}
