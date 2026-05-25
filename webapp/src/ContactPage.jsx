import React from "react";

function ContactIcon({ type }) {
  if (type === "email") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4.5 5h15A2.5 2.5 0 0 1 22 7.5v9A2.5 2.5 0 0 1 19.5 19h-15A2.5 2.5 0 0 1 2 16.5v-9A2.5 2.5 0 0 1 4.5 5Zm0 2c-.1 0-.2.01-.3.04L12 12.1l7.8-5.06a1 1 0 0 0-.3-.04h-15Zm15 10a.5.5 0 0 0 .5-.5V9.08l-7.46 4.84a1 1 0 0 1-1.08 0L4 9.08v7.42a.5.5 0 0 0 .5.5h15Z" />
      </svg>
    );
  }

  if (type === "phone") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6.62 10.79c1.44 2.83 3.76 5.15 6.59 6.59l2.2-2.2a1.5 1.5 0 0 1 1.5-.36c1.65.55 3.02.78 4.09.78a1 1 0 0 1 1 1V21a1 1 0 0 1-1 1C10.51 22 2 13.49 2 3a1 1 0 0 1 1-1h4.4a1 1 0 0 1 1 1c0 1.07.23 2.44.78 4.09a1.5 1.5 0 0 1-.36 1.5l-2.2 2.2Z" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm0 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm1 4v4.1l3.2 1.9-1 1.72-4.2-2.5V8h2Z" />
    </svg>
  );
}

function ContactPage() {
  return (
    <section className="contact-page">
      <div className="contact-panel">
        <div className="contact-info">
          <div className="section-label">Kontak Kami</div>
          <div className="contact-list">
            <div className="contact-item">
              <span className="contact-icon"><ContactIcon type="email" /></span>
              <a href="mailto:hello@sesuacafe.com">hello@sesuacafe.com</a>
            </div>
            <div className="contact-item">
              <span className="contact-icon"><ContactIcon type="phone" /></span>
              <a href="tel:+6281234567890">+62 812-3456-7890</a>
            </div>
            <div className="contact-item">
              <span className="contact-icon"><ContactIcon type="clock" /></span>
              <span>Senin - Minggu, 08.00 - 22.00 WIB</span>
            </div>
          </div>

          <div className="contact-socials">
            <a href="https://wa.me/" aria-label="WhatsApp" className="social-link">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12.04 2C6.56 2 2.1 6.32 2.1 11.64c0 1.7.46 3.35 1.33 4.8L2 22l5.74-1.38a10.2 10.2 0 0 0 4.3.95c5.48 0 9.94-4.32 9.94-9.64S17.52 2 12.04 2Zm0 17.92c-1.4 0-2.77-.35-3.98-1.02l-.28-.16-3.4.82.84-3.22-.18-.3a8.04 8.04 0 0 1-1.29-4.4c0-4.41 3.72-8 8.29-8s8.29 3.59 8.29 8-3.72 8.28-8.29 8.28Zm4.55-5.98c-.25-.12-1.47-.7-1.7-.78-.23-.08-.4-.12-.57.12-.17.25-.65.78-.8.94-.14.17-.29.19-.54.07-.25-.12-1.06-.38-2.01-1.2a7.43 7.43 0 0 1-1.39-1.67c-.15-.25-.02-.38.11-.5.12-.12.25-.3.38-.44.13-.14.17-.25.25-.42.08-.17.04-.31-.02-.43-.06-.12-.57-1.33-.78-1.82-.21-.48-.42-.41-.57-.42h-.49c-.17 0-.44.06-.67.31-.23.25-.88.84-.88 2.04 0 1.2.9 2.36 1.02 2.53.12.17 1.77 2.62 4.29 3.67.6.25 1.07.4 1.44.51.6.18 1.15.16 1.58.1.48-.07 1.47-.58 1.68-1.14.21-.56.21-1.04.15-1.14-.06-.1-.23-.16-.48-.28Z" />
              </svg>
            </a>
            <a href="https://instagram.com/" aria-label="Instagram" className="social-link">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7.8 2h8.4A5.8 5.8 0 0 1 22 7.8v8.4a5.8 5.8 0 0 1-5.8 5.8H7.8A5.8 5.8 0 0 1 2 16.2V7.8A5.8 5.8 0 0 1 7.8 2Zm0 2A3.8 3.8 0 0 0 4 7.8v8.4A3.8 3.8 0 0 0 7.8 20h8.4a3.8 3.8 0 0 0 3.8-3.8V7.8A3.8 3.8 0 0 0 16.2 4H7.8Zm4.2 3.35A4.65 4.65 0 1 1 7.35 12 4.65 4.65 0 0 1 12 7.35Zm0 2A2.65 2.65 0 1 0 14.65 12 2.65 2.65 0 0 0 12 9.35ZM17 6.75a1.15 1.15 0 1 1-1.15 1.15A1.15 1.15 0 0 1 17 6.75Z" />
              </svg>
            </a>
            <a href="https://tiktok.com/" aria-label="TikTok" className="social-link">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M15.5 2c.28 2.38 1.58 3.8 3.9 3.95v3.08a7.35 7.35 0 0 1-3.86-1.13v6.3c0 4.03-2.42 6.6-6.16 6.6A5.68 5.68 0 0 1 3.6 15.1c0-3.5 2.77-6 6.34-5.72v3.22c-1.64-.25-3.02.64-3.02 2.5a2.37 2.37 0 0 0 2.46 2.45c1.72 0 2.7-1.02 2.7-3.3V2h3.42Z" />
              </svg>
            </a>
            <a href="mailto:hello@sesuacafe.com" aria-label="Email" className="social-link">
              <ContactIcon type="email" />
            </a>
          </div>
        </div>

        <div className="contact-map-side">
          <iframe
            title="Lokasi Sesua Cafe"
            src="https://www.google.com/maps?q=Desa%20Ciawi%20Bogor%20Indonesia&output=embed"
            loading="lazy"
            referrerPolicy="no-referrer-when-downgrade"
          />
          <p>Desa Ciawi, kec. Ciawi kab. Bogor depan cempaka 58 B kode pos 16720 nomor 68 rt.05 rw 04, Bogor, Indonesia 16720</p>
        </div>
      </div>
    </section>
  );
}

export default ContactPage;
