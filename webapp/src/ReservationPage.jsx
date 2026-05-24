import React, { useState } from "react";
import { buildApiUrl, getOutletId } from "./serverConfig";

function ReservationPage() {
  const [isPopupOpen, setIsPopupOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [reservationCode, setReservationCode] = useState("");
  const [form, setForm] = useState({
    customerName: "",
    phone: "",
    partySize: "1",
    reservationDate: "",
    reservationTime: "",
    note: "",
  });

  const setField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  async function handleSubmit(event) {
    event.preventDefault();
    setErrorMessage("");

    const customerName = form.customerName.trim();
    const reservationDate = form.reservationDate.trim();
    const reservationTime = form.reservationTime.trim();
    if (!customerName || !reservationDate || !reservationTime) {
      setErrorMessage("Nama, tanggal, dan jam reservasi wajib diisi.");
      return;
    }

    const parsedPartySize = Number.parseInt(form.partySize, 10);
    const partySize = Number.isFinite(parsedPartySize) && parsedPartySize > 0 ? parsedPartySize : 1;
    const reservationAt = `${reservationDate}T${reservationTime}:00`;

    const payload = {
      customerName,
      phone: form.phone.trim() || null,
      partySize,
      reservationDate,
      reservationTime,
      reservationAt,
      note: form.note.trim() || null,
      outletId: getOutletId(),
      // Backward-compatible aliases (older server payload style).
      customer_name: customerName,
      party_size: partySize,
      reservation_date: reservationDate,
      reservation_time: reservationTime,
      reservation_at: reservationAt,
      outlet_id: getOutletId(),
    };

    setIsSubmitting(true);
    try {
      const response = await fetch(buildApiUrl("/api/reservations"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          data: payload,
          message: "Create reservation request",
          error: null,
        }),
      });
      const envelope = await response.json().catch(() => ({}));
      if (!response.ok || envelope?.error) {
        throw new Error(envelope?.error || envelope?.message || `HTTP ${response.status}`);
      }

      setReservationCode(envelope?.data?.id || "-");
      setIsPopupOpen(true);
      setForm({
        customerName: "",
        phone: "",
        partySize: "1",
        reservationDate: "",
        reservationTime: "",
        note: "",
      });
    } catch (error) {
      setErrorMessage(error?.message || "Gagal kirim reservasi. Coba lagi.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="reservation-page">
      <div className="reservation-card">
        <img
          className="reservasi-img"
          src="https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=400&q=80"
          alt="Cafe barista"
        />

        <form className="reservasi-form-side" onSubmit={handleSubmit}>
          <div className="form-title">Formulir Reservasi</div>
          <div className="form-desc">Reservasi untuk seluruh cafe. Data akan masuk ke server dan bisa dicek tim kasir.</div>

          <div className="form-row">
            <input
              className="f-input"
              type="text"
              placeholder="Nama pemesan"
              value={form.customerName}
              onChange={(event) => setField("customerName", event.target.value)}
              required
            />
            <input
              className="f-input"
              type="tel"
              placeholder="Nomor telepon"
              value={form.phone}
              onChange={(event) => setField("phone", event.target.value)}
            />
          </div>

          <div className="form-row">
            <div className="f-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
              <input
                type="number"
                placeholder="Jumlah orang"
                min="1"
                value={form.partySize}
                onChange={(event) => setField("partySize", event.target.value)}
              />
            </div>
            <div className="f-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                <rect x="3" y="4" width="18" height="18" rx="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              <input
                type="date"
                value={form.reservationDate}
                onChange={(event) => setField("reservationDate", event.target.value)}
                required
              />
            </div>
            <div className="f-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                <circle cx="12" cy="12" r="10" />
                <polyline points="12 6 12 12 16 14" />
              </svg>
              <input
                type="time"
                value={form.reservationTime}
                onChange={(event) => setField("reservationTime", event.target.value)}
                required
              />
            </div>
          </div>

          <div className="form-row single">
            <textarea
              className="f-input"
              placeholder="Catatan tambahan (opsional)"
              value={form.note}
              onChange={(event) => setField("note", event.target.value)}
            />
          </div>

          {errorMessage ? <div className="reservation-error">{errorMessage}</div> : null}

          <button type="submit" className="btn-kirim" disabled={isSubmitting}>
            {isSubmitting ? "Mengirim..." : "Kirim Reservasi"}
          </button>
        </form>
      </div>

      <div className={`popup-overlay ${isPopupOpen ? "show" : ""}`} id="popup">
        <div className="popup-box">
          <div className="popup-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="#22c55e" strokeWidth="2.5">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </div>
          <div className="popup-title">Reservasi Terkirim</div>
          <div className="popup-sub">Kode reservasi: <strong>{reservationCode}</strong>. Tim kasir akan follow up melalui kontak yang kamu isi.</div>
          <button type="button" className="popup-close" onClick={() => setIsPopupOpen(false)}>Tutup</button>
        </div>
      </div>
    </section>
  );
}

export default ReservationPage;
