import React from "react";
import heroImage from "./assets/images/hero_section.png";

const events = [
  {
    date: "24 May 2026",
    title: "Latte Art Workshop",
    time: "10:00 - 12:00 WIB",
    location: "Ruang Barista",
    description:
      "Belajar teknik latte art dasar bersama barista kami. Termasuk bahan dan tasting flight.",
  },
  {
    date: "01 Jun 2026",
    title: "Acoustic Night",
    time: "19:00 - 21:30 WIB",
    location: "Teras Sesua",
    description:
      "Malam santai dengan musik akustik, menu seasonal, dan suasana hangat bersama komunitas.",
  },
  {
    date: "15 Jun 2026",
    title: "Coffee Cupping Session",
    time: "15:00 - 16:30 WIB",
    location: "Bar Tasting",
    description:
      "Eksplorasi single origin pilihan kami dan pahami profil rasa dengan sesi cupping terarah.",
  },
];

function EventPage() {
  return (
    <section className="event-page">
      <header className="event-hero">
        <div className="event-hero-copy">
          <div className="section-label">Event</div>
          <div className="section-title">Agenda Komunitas Sesua</div>
          <p className="section-sub">
            Temukan rangkaian event bulanan kami, dari workshop kopi sampai
            live music. Reservasi tempatmu lebih awal agar tidak kehabisan.
          </p>

          <div className="event-meta">
            <div className="event-meta-card">
              <div className="event-meta-title">Lokasi</div>
              <div className="event-meta-value">Sesua Cafe, Bogor</div>
            </div>
            <div className="event-meta-card">
              <div className="event-meta-title">Kontak</div>
              <div className="event-meta-value">+62 812-3456-7890</div>
            </div>
            <div className="event-meta-card">
              <div className="event-meta-title">Jam Operasional</div>
              <div className="event-meta-value">08:00 - 22:00 WIB</div>
            </div>
          </div>
        </div>

        <div className="event-hero-image">
          <img src={heroImage} alt="Suasana event Sesua Cafe" />
        </div>
      </header>

      <div className="event-grid">
        {events.map((event) => (
          <article key={event.title} className="event-card">
            <div className="event-card-date">{event.date}</div>
            <div className="event-card-title">{event.title}</div>
            <div className="event-card-meta">
              <span>{event.time}</span>
              <span>{event.location}</span>
            </div>
            <p className="event-card-desc">{event.description}</p>
            <button type="button" className="btn-blue">Lihat Detail</button>
          </article>
        ))}
      </div>
    </section>
  );
}

export default EventPage;
