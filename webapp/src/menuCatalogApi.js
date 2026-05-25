import { buildApiUrl, getBearerToken, getOutletId } from "./serverConfig";

const CARD_ACCENTS = [
  "#f0e6d3",
  "#e8d5c4",
  "#d4e8f0",
  "#f5e6d0",
  "#e8f0e4",
  "#f0d5e8",
  "#f5ecd4",
  "#dce8f5",
];

const CARD_EMOJIS = ["☕", "🥛", "🧊", "🍵", "🍫", "🥤", "🫧", "💙"];

function formatNumberId(value) {
  return new Intl.NumberFormat("id-ID", {
    maximumFractionDigits: 0,
  }).format(Number.isFinite(value) ? Math.round(value) : 0);
}

function formatRupiah(value) {
  return `Rp ${formatNumberId(value)}`;
}

function parseEnvelope(data) {
  if (data && typeof data === "object" && ("data" in data || "error" in data)) {
    if (data.error) {
      throw new Error(data.error || "Server error");
    }
    return data.data;
  }
  return data;
}

function buildAuthHeaders(bearerToken) {
  const token = (bearerToken || "").trim();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

export async function fetchMenuItemsFromServer(outletId = getOutletId()) {
  const endpoint = `${buildApiUrl("/api/menu")}?outlet=${encodeURIComponent(outletId)}`;
  const response = await fetch(endpoint);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`);
  }
  const payload = parseEnvelope(body);
  const items = Array.isArray(payload) ? payload : payload?.items;
  if (!Array.isArray(items)) {
    throw new Error("Invalid menu payload");
  }
  return items.map((item, index) => {
    const category = (item.groupName || item.groupId || "Lainnya").trim() || "Lainnya";
    const code = (item.code || item.id || "").trim();
    const ingredients = code ? `Kode: ${code}` : "Racikan khas dari outlet.";
    const priceNumber = Number(item.price || 0);
    return {
      id: item.id || `menu_${index}`,
      name: (item.name || "Tanpa Nama").trim(),
      category,
      ingredients,
      priceNumber: Number.isFinite(priceNumber) ? priceNumber : 0,
      priceLabel: formatRupiah(Number.isFinite(priceNumber) ? priceNumber : 0),
      accent: CARD_ACCENTS[index % CARD_ACCENTS.length],
      emoji: CARD_EMOJIS[index % CARD_EMOJIS.length],
      imageUrl: item.imageUrl || item.image_url || null,
    };
  });
}

export async function fetchReservationsFromServer({
  outletId = getOutletId(),
  bearerToken = getBearerToken(),
  statuses = ["PENDING", "CONFIRMED", "SEATED"],
} = {}) {
  const statusParam = Array.isArray(statuses) ? statuses.filter(Boolean).join(",") : "";
  const endpoint =
    `${buildApiUrl("/api/reservations")}` +
    `?outlet=${encodeURIComponent(outletId)}` +
    (statusParam ? `&status=${encodeURIComponent(statusParam)}` : "");

  const response = await fetch(endpoint, {
    headers: {
      ...buildAuthHeaders(bearerToken),
    },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`);
  }
  const payload = parseEnvelope(body);
  if (!Array.isArray(payload)) {
    throw new Error("Invalid reservation payload");
  }
  return payload.map((item, index) => ({
    id: item.id || `res_${index}`,
    customerName: (item.customerName || item.customer_name || "Unknown").trim(),
    partySize: Number(item.partySize || item.party_size || 0),
    reservationAt: item.reservationAt || item.reservation_at || "",
    status: (item.status || "").toUpperCase(),
    note: item.note || "",
  }));
}

export async function submitFeedbackToServer({
  customerName,
  contact,
  rating,
  subject,
  message,
  outletId = getOutletId(),
  website = "",
}) {
  const payload = {
    customerName: (customerName || "").trim(),
    contact: (contact || "").trim() || null,
    rating: Number.isFinite(Number(rating)) ? Number(rating) : null,
    subject: (subject || "").trim() || null,
    message: (message || "").trim(),
    website: (website || "").trim(),
    outletId: (outletId || "").trim() || "default",
    customer_name: (customerName || "").trim(),
    outlet_id: (outletId || "").trim() || "default",
  };

  const response = await fetch(buildApiUrl("/api/feedback"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      data: payload,
      message: "Create feedback",
      error: null,
    }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`);
  }
  const parsed = parseEnvelope(body);
  return parsed || null;
}

export async function fetchFeedbackFromServer({
  outletId = getOutletId(),
  bearerToken = getBearerToken(),
  statuses = ["NEW", "REVIEWED"],
  limit = 100,
} = {}) {
  const statusParam = Array.isArray(statuses) ? statuses.filter(Boolean).join(",") : "";
  const endpoint =
    `${buildApiUrl("/api/feedback")}` +
    `?outlet=${encodeURIComponent(outletId)}` +
    (statusParam ? `&status=${encodeURIComponent(statusParam)}` : "") +
    `&limit=${encodeURIComponent(Math.max(1, Math.min(200, Number(limit) || 100)))}`;

  const response = await fetch(endpoint, {
    headers: {
      ...buildAuthHeaders(bearerToken),
    },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`);
  }
  const payload = parseEnvelope(body);
  if (!Array.isArray(payload)) {
    throw new Error("Invalid feedback payload");
  }
  return payload.map((item, index) => ({
    id: item.id || `fb_${index}`,
    customerName: (item.customerName || item.customer_name || "Guest").trim(),
    contact: (item.contact || "").trim(),
    rating: Number(item.rating || 0),
    subject: (item.subject || "").trim(),
    message: (item.message || "").trim(),
    status: (item.status || "").toUpperCase(),
    createdAt: item.createdAt || item.created_at || "",
  }));
}
