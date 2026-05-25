import React, { useState } from "react";
import { submitFeedbackToServer } from "./menuCatalogApi";
import { getOutletId } from "./serverConfig";

function FeedbackPage() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [form, setForm] = useState({
    customerName: "",
    contact: "",
    rating: "5",
    subject: "",
    message: "",
    website: "",
  });

  function setField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSuccessMessage("");
    setErrorMessage("");

    const customerName = form.customerName.trim();
    const message = form.message.trim();
    if (!customerName || !message) {
      setErrorMessage("Nama dan pesan wajib diisi.");
      return;
    }

    const rating = Number.parseInt(form.rating, 10);
    const safeRating = Number.isFinite(rating) ? Math.max(1, Math.min(5, rating)) : 5;

    setIsSubmitting(true);
    try {
      const created = await submitFeedbackToServer({
        customerName,
        contact: form.contact,
        rating: safeRating,
        subject: form.subject,
        message,
        website: form.website,
        outletId: getOutletId(),
      });
      setSuccessMessage(
        `Terima kasih. Feedback kamu sudah terkirim (ID: ${created?.id || "-"}) dan akan ditinjau tim kami.`
      );
      setForm({
        customerName: "",
        contact: "",
        rating: "5",
        subject: "",
        message: "",
        website: "",
      });
    } catch (error) {
      setErrorMessage(error?.message || "Gagal kirim feedback.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="feedback-page">
      <div className="feedback-card">
        <div className="section-label">Feedback</div>
        <h2>Bantu Kami Meningkatkan Layanan</h2>
        <p className="feedback-subtitle">
          Ceritakan pengalaman kamu di outlet ini. Kami baca semua masukan dan tindak lanjuti secepatnya.
        </p>

        <form className="feedback-form" onSubmit={handleSubmit}>
          <div className="feedback-grid">
            <label>
              Nama
              <input
                type="text"
                maxLength={50}
                value={form.customerName}
                onChange={(event) => setField("customerName", event.target.value)}
                placeholder="Nama kamu"
                required
              />
            </label>
            <label>
              Kontak (opsional)
              <input
                type="text"
                maxLength={120}
                value={form.contact}
                onChange={(event) => setField("contact", event.target.value)}
                placeholder="Email / WhatsApp"
              />
            </label>
          </div>

          <div className="feedback-grid">
            <label>
              Rating
              <select value={form.rating} onChange={(event) => setField("rating", event.target.value)}>
                <option value="5">5 - Sangat puas</option>
                <option value="4">4 - Puas</option>
                <option value="3">3 - Cukup</option>
                <option value="2">2 - Kurang</option>
                <option value="1">1 - Buruk</option>
              </select>
            </label>
            <label>
              Subjek (opsional)
              <input
                type="text"
                maxLength={80}
                value={form.subject}
                onChange={(event) => setField("subject", event.target.value)}
                placeholder="Contoh: Pelayanan kasir"
              />
            </label>
          </div>

          <label>
            Pesan
            <textarea
              maxLength={1000}
              rows={5}
              value={form.message}
              onChange={(event) => setField("message", event.target.value)}
              placeholder="Tulis feedback kamu..."
              required
            />
          </label>

          <input
            type="text"
            className="feedback-honeypot"
            tabIndex={-1}
            autoComplete="off"
            value={form.website}
            onChange={(event) => setField("website", event.target.value)}
            placeholder="Leave this blank"
            aria-hidden="true"
          />

          {errorMessage ? <div className="feedback-error">{errorMessage}</div> : null}
          {successMessage ? <div className="feedback-success">{successMessage}</div> : null}

          <button type="submit" className="btn-kirim" disabled={isSubmitting}>
            {isSubmitting ? "Mengirim..." : "Kirim Feedback"}
          </button>
        </form>
      </div>
    </section>
  );
}

export default FeedbackPage;
