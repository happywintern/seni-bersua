import React, { useEffect, useState } from "react";
import { fetchFeedbackFromServer, fetchMenuItemsFromServer, fetchReservationsFromServer } from "./menuCatalogApi";
import { getWebServerSettings, setApiBaseUrl, setBearerToken, setOutletId } from "./serverConfig";

function WebAdminPage() {
  const initial = getWebServerSettings();
  const [apiBaseUrl, setApiBaseUrlState] = useState(initial.apiBaseUrl);
  const [outletId, setOutletIdState] = useState(initial.outletId);
  const [bearerToken, setBearerTokenState] = useState(initial.bearerToken || "");
  const [message, setMessage] = useState("");
  const [testing, setTesting] = useState(false);
  const [loadingReservations, setLoadingReservations] = useState(false);
  const [reservationStatusFilter, setReservationStatusFilter] = useState("PENDING,CONFIRMED,SEATED");
  const [previewItems, setPreviewItems] = useState([]);
  const [reservations, setReservations] = useState([]);
  const [feedbackRows, setFeedbackRows] = useState([]);
  const [hasLoadedReservations, setHasLoadedReservations] = useState(false);
  const [hasLoadedFeedback, setHasLoadedFeedback] = useState(false);
  const [loadingFeedback, setLoadingFeedback] = useState(false);
  const [feedbackStatusFilter, setFeedbackStatusFilter] = useState("NEW,REVIEWED");

  useEffect(() => {
    const hasBaseUrl = (apiBaseUrl || "").trim().length > 0;
    const hasOutlet = (outletId || "").trim().length > 0;
    const hasToken = (bearerToken || "").trim().length > 0;
    if (!hasBaseUrl || !hasOutlet || !hasToken) return;
    handleLoadReservations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSave(event) {
    event.preventDefault();
    setApiBaseUrl(apiBaseUrl);
    setOutletId(outletId);
    setBearerToken(bearerToken);
    setMessage("Pengaturan outlet + server tersimpan. Buka halaman Menu untuk cek hasil.");
  }

  async function handleTestConnection() {
    setTesting(true);
    setMessage("");
    try {
      setApiBaseUrl(apiBaseUrl);
      setOutletId(outletId);
      const items = await fetchMenuItemsFromServer((outletId || "").trim() || "default");
      setPreviewItems(items.slice(0, 8));
      setMessage(`Server terhubung. Produk outlet "${(outletId || "default").trim()}" terbaca: ${items.length} item.`);
    } catch (error) {
      setPreviewItems([]);
      setMessage(error?.message || "Gagal konek ke server.");
    } finally {
      setTesting(false);
    }
  }

  async function handleLoadReservations() {
    setLoadingReservations(true);
    setMessage("");
    try {
      setApiBaseUrl(apiBaseUrl);
      setOutletId(outletId);
      setBearerToken(bearerToken);
      const statuses = reservationStatusFilter
        .split(",")
        .map((it) => it.trim().toUpperCase())
        .filter(Boolean);
      const rows = await fetchReservationsFromServer({
        outletId: (outletId || "").trim() || "default",
        bearerToken,
        statuses: statuses.length > 0 ? statuses : undefined,
      });
      setReservations(rows);
      setHasLoadedReservations(true);
      setMessage(`Reservasi outlet "${(outletId || "default").trim()}" terbaca: ${rows.length} request.`);
    } catch (error) {
      setReservations([]);
      setHasLoadedReservations(true);
      setMessage(error?.message || "Gagal ambil reservasi dari server.");
    } finally {
      setLoadingReservations(false);
    }
  }

  async function handleLoadFeedback() {
    setLoadingFeedback(true);
    setMessage("");
    try {
      setApiBaseUrl(apiBaseUrl);
      setOutletId(outletId);
      setBearerToken(bearerToken);
      const statuses = feedbackStatusFilter
        .split(",")
        .map((it) => it.trim().toUpperCase())
        .filter(Boolean);
      const rows = await fetchFeedbackFromServer({
        outletId: (outletId || "").trim() || "default",
        bearerToken,
        statuses: statuses.length > 0 ? statuses : undefined,
      });
      setFeedbackRows(rows);
      setHasLoadedFeedback(true);
      setMessage(`Feedback outlet "${(outletId || "default").trim()}" terbaca: ${rows.length} data.`);
    } catch (error) {
      setFeedbackRows([]);
      setHasLoadedFeedback(true);
      setMessage(error?.message || "Gagal ambil feedback dari server.");
    } finally {
      setLoadingFeedback(false);
    }
  }

  return (
    <section className="web-admin-page">
      <div className="web-admin-card">
        <h2>Web Admin Settings</h2>
        <p className="web-admin-subtitle">
          Atur outlet/store yang dipakai halaman web ini. Setelah save, menu & reservasi otomatis pakai outlet tersebut.
        </p>

        <form className="web-admin-form" onSubmit={handleSave}>
          <label>
            API Base URL
            <input
              type="text"
              value={apiBaseUrl}
              onChange={(event) => setApiBaseUrlState(event.target.value)}
              placeholder="Contoh: http://10.0.2.2:8080"
            />
          </label>
          <label>
            Outlet / Store ID
            <input
              type="text"
              value={outletId}
              onChange={(event) => setOutletIdState(event.target.value)}
              placeholder="Contoh: default"
            />
          </label>
          <label>
            Bearer Token (Owner/Cashier)
            <input
              type="text"
              value={bearerToken}
              onChange={(event) => setBearerTokenState(event.target.value)}
              placeholder="Paste token untuk endpoint protected (reservations/orders)"
            />
          </label>
          <label>
            Reservation Status Filter (comma-separated)
            <input
              type="text"
              value={reservationStatusFilter}
              onChange={(event) => setReservationStatusFilter(event.target.value)}
              placeholder="Contoh: PENDING,CONFIRMED,SEATED"
            />
          </label>
          <label>
            Feedback Status Filter (comma-separated)
            <input
              type="text"
              value={feedbackStatusFilter}
              onChange={(event) => setFeedbackStatusFilter(event.target.value)}
              placeholder="Contoh: NEW,REVIEWED,RESOLVED"
            />
          </label>
          <div className="web-admin-actions">
            <button type="submit" className="btn-blue">Save Settings</button>
            <button type="button" className="btn-blue btn-secondary" onClick={handleTestConnection} disabled={testing}>
              {testing ? "Testing..." : "Test Product Sync"}
            </button>
            <button
              type="button"
              className="btn-blue btn-secondary"
              onClick={handleLoadReservations}
              disabled={loadingReservations}
            >
              {loadingReservations ? "Loading..." : "Load Reservation Requests"}
            </button>
            <button
              type="button"
              className="btn-blue btn-secondary"
              onClick={handleLoadFeedback}
              disabled={loadingFeedback}
            >
              {loadingFeedback ? "Loading..." : "Load Feedback"}
            </button>
          </div>
        </form>

        {message ? <p className="web-admin-message">{message}</p> : null}

        {previewItems.length > 0 ? (
          <div className="web-admin-preview">
            <h3>Preview Produk</h3>
            <ul>
              {previewItems.map((item) => (
                <li key={item.id}>
                  <span>{item.name}</span>
                  <strong>{item.priceLabel}</strong>
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        {reservations.length > 0 ? (
          <div className="web-admin-preview">
            <h3>Reservation Requests</h3>
            <p className="web-admin-subtitle">Source: live server API `/api/reservations` (scoped by outlet).</p>
            <ul>
              {reservations.map((reservation) => (
                <li key={reservation.id}>
                  <div>
                    <span>{reservation.customerName}</span>
                    <div className="web-admin-subrow">
                      {reservation.status} · {reservation.partySize} pax · {reservation.reservationAt || "-"}
                    </div>
                  </div>
                  <strong>{reservation.id}</strong>
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        {hasLoadedReservations && reservations.length === 0 ? (
          <div className="web-admin-preview">
            <h3>Reservation Requests</h3>
            <p className="web-admin-subtitle">Belum ada request reservasi dari server untuk outlet + status filter ini.</p>
          </div>
        ) : null}

        {feedbackRows.length > 0 ? (
          <div className="web-admin-preview">
            <h3>Feedback Requests</h3>
            <p className="web-admin-subtitle">Source: live server API `/api/feedback` (protected by bearer + outlet).</p>
            <ul>
              {feedbackRows.map((feedback) => (
                <li key={feedback.id}>
                  <div>
                    <span>{feedback.customerName}</span>
                    <div className="web-admin-subrow">
                      {feedback.status} · {feedback.rating > 0 ? `${feedback.rating}/5` : "No rating"}
                    </div>
                    <div className="web-admin-subrow">{feedback.subject || feedback.message}</div>
                  </div>
                  <strong>{feedback.id}</strong>
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        {hasLoadedFeedback && feedbackRows.length === 0 ? (
          <div className="web-admin-preview">
            <h3>Feedback Requests</h3>
            <p className="web-admin-subtitle">Belum ada feedback untuk outlet + status filter ini.</p>
          </div>
        ) : null}
      </div>
    </section>
  );
}

export default WebAdminPage;
