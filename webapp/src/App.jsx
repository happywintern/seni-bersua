import React, { useEffect, useMemo, useRef, useState } from "react";
import logoImage from "./assets/images/logo.png";
import rendaSesuaImage from "./assets/images/renda_sesua.png";
import rendaHorizontal from "./assets/images/renda_horizontal.png";
import MenuPage from "./MenuPage";
import AboutPage from "./AboutPage";
import ReservationPage from "./ReservationPage";
import ContactPage from "./ContactPage";
import WebAdminPage from "./WebAdminPage";
import { fetchMenuItemsFromServer } from "./menuCatalogApi";
import { getOutletId } from "./serverConfig";

const fallbackMenuItems = [
  {
    id: "fallback-home-1",
    name: "Signature Espresso",
    ingredients: "Espresso shot, susu segar, crema lembut",
    priceLabel: "Rp 35.000",
    accent: "#f0e6d3",
    emoji: "☕",
  },
  {
    id: "fallback-home-2",
    name: "Caramel Latte",
    ingredients: "Espresso, susu whole milk, sirup karamel",
    priceLabel: "Rp 42.000",
    accent: "#e8d5c4",
    emoji: "🥛",
  },
  {
    id: "fallback-home-3",
    name: "Iced Americano",
    ingredients: "Double espresso, air dingin, es batu",
    priceLabel: "Rp 32.000",
    accent: "#d4e8f0",
    emoji: "🧊",
  },
  {
    id: "fallback-home-4",
    name: "Vanilla Cappuccino",
    ingredients: "Espresso, foam susu, vanilla bean",
    priceLabel: "Rp 45.000",
    accent: "#f5e6d0",
    emoji: "🫧",
  },
  {
    id: "fallback-home-5",
    name: "Matcha Sesua",
    ingredients: "Matcha premium, susu oat, madu",
    priceLabel: "Rp 48.000",
    accent: "#e8f0e4",
    emoji: "🍵",
  },
  {
    id: "fallback-home-6",
    name: "Mocha Velvet",
    ingredients: "Espresso, dark chocolate, susu, whip",
    priceLabel: "Rp 50.000",
    accent: "#f0d5e8",
    emoji: "🍫",
  },
  {
    id: "fallback-home-7",
    name: "Cold Brew Classic",
    ingredients: "Kopi Arabica, diseduh 12 jam, es",
    priceLabel: "Rp 38.000",
    accent: "#f5ecd4",
    emoji: "🧇",
  },
  {
    id: "fallback-home-8",
    name: "Blue Sky Latte",
    ingredients: "Butterfly pea, lemon, susu segar",
    priceLabel: "Rp 43.000",
    accent: "#dce8f5",
    emoji: "💙",
  },
];

const bundles = [
  {
    badge: "Paling Populer",
    title: "Morning Starter Pack",
    description:
      "Mulai pagi Anda dengan sempurna - Signature Espresso pilihan barista kami, dipadu croissant butter hangat dan sepotong banana bread lembut. Energi terbaik untuk produktivitas maksimal.",
    price: "Rp 85.000",
    originalPrice: "Rp 110.000",
    image: rendaSesuaImage,
    accent: "linear-gradient(135deg, #f5e6d0, #e8d5c4)",
  },
  {
    badge: "Hemat 30%",
    title: "Afternoon Chill Bundle",
    description:
      "Nikmati sore santai bersama sahabat. Dua gelas Iced Americano segar, french fries crispy, dan dessert pudding coklat - sempurna untuk nongkrong atau work-from-cafe.",
    price: "Rp 110.000",
    originalPrice: "Rp 158.000",
    image: rendaSesuaImage,
    accent: "linear-gradient(135deg, #e8f0e4, #d4e8f0)",
  },
];

const stats = [
  { value: "5+", label: "Tahun Berdiri" },
  { value: "40+", label: "Menu Pilihan" },
  { value: "10K+", label: "Pelanggan Setia" },
];

function App() {
  const menuCarouselRef = useRef(null);
  const [menuItems, setMenuItems] = useState(fallbackMenuItems);
  const [menuLoadError, setMenuLoadError] = useState("");
  const isMenuPage = window.location.pathname === "/web/menu";
  const isAboutPage = window.location.pathname === "/web/about";
  const isReservationPage = window.location.pathname === "/web/reservasi";
  const isContactPage = window.location.pathname === "/web/kontak";
  const isWebAdminPage = window.location.pathname === "/web/admin";

  useEffect(() => {
    let active = true;
    async function loadMenuForHomepage() {
      try {
        const remoteMenu = await fetchMenuItemsFromServer(getOutletId());
        if (!active) return;
        if (remoteMenu.length > 0) {
          setMenuItems(remoteMenu.slice(0, 12));
          setMenuLoadError("");
        }
      } catch (error) {
        if (!active) return;
        setMenuItems(fallbackMenuItems);
        setMenuLoadError(error?.message || "Menu server belum tersedia.");
      }
    }
    loadMenuForHomepage();
    return () => {
      active = false;
    };
  }, []);

  const menuSubtitle = useMemo(() => {
    if (menuLoadError) {
      return "Menampilkan menu default sementara. Atur outlet di /web/admin agar sinkron dengan server.";
    }
    return "Nikmati keseimbangan rasa sempurna dalam setiap menu pilihan kami.";
  }, [menuLoadError]);

  function scrollCarousel(direction) {
    const element = menuCarouselRef.current;
    if (!element) return;
    element.scrollBy({ left: direction * 244, behavior: "smooth" });
  }

  function scrollToSection(sectionId) {
    const section = document.getElementById(sectionId);
    if (section) {
      section.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }

  function navigateToSection(sectionId) {
    if (isMenuPage || isAboutPage || isReservationPage || isContactPage || isEventPage) {
      window.location.href = `/#${sectionId}`;
      return;
    }
    scrollToSection(sectionId);
  }

  function navigateToMenuPage() {
    window.location.href = "/web/menu";
  }

  function navigateToAboutPage() {
    window.location.href = "/web/about";
  }

  function navigateToReservationPage() {
    window.location.href = "/web/reservasi";
  }

  function navigateToContactPage() {
    window.location.href = "/web/kontak";
  }

  function navigateToWebAdminPage() {
    window.location.href = "/web/admin";
  }

  return (
    <div className="page-shell">
      <nav className="topbar">
        <div className="logo-area">
          <div className="logo-mark">
            <img src={logoImage} alt="Sesua Cafe Logo" className="logo" />
          </div>
        </div>
        <div className="nav-links">
          <button type="button" onClick={() => navigateToSection("beranda")}>Beranda</button>
          <button type="button" onClick={navigateToMenuPage}>Menu</button>
          <button type="button" onClick={navigateToAboutPage}>Tentang Kami</button>
          <button type="button" onClick={navigateToReservationPage}>Reservasi</button>
          <button type="button" onClick={navigateToEventPage}>Event</button>
          <button type="button" onClick={navigateToContactPage}>Kontak Kami</button>
          <button type="button" onClick={navigateToWebAdminPage}>Admin</button>
        </div>
      </nav>

      <main>
        {isMenuPage ? (
          <MenuPage />
        ) : isAboutPage ? (
          <AboutPage />
        ) : isReservationPage ? (
          <ReservationPage />
        ) : isEventPage ? (
          <EventPage />
        ) : isContactPage ? (
          <ContactPage />
        ) : isWebAdminPage ? (
          <WebAdminPage />
        ) : (
          <>
            <section className="hero" id="beranda">
          
          <div className="hero-image-fallback" />
          
        </section>

     

        <section className="menu-section" id="menu">
          <div className="menu-header">
            <div className="section-label">Menu Pilihan</div>
            <div className="section-title">Eksplorasi Rasa di Setiap Cangkir</div>
            <div className="section-sub">{menuSubtitle}</div>
          </div>

          <div className="menu-carousel-wrap">
            <button type="button" className="carousel-btn prev" onClick={() => scrollCarousel(-1)} aria-label="Scroll menu ke kiri">
              &#8249;
            </button>
            <div className="menu-carousel" ref={menuCarouselRef}>
              {menuItems.map((item) => (
                <article key={item.id || item.name} className="menu-card">
                  <div className="menu-card-img">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt={item.name} className="menu-page-card-photo" />
                    ) : (
                      <div className="coffee-placeholder" style={{ background: item.accent }}>{item.emoji}</div>
                    )}
                  </div>
                  <div className="menu-card-body">
                    <div className="menu-card-name">{item.name}</div>
                    <div className="menu-card-ingredients">{item.ingredients}</div>
                    <div className="menu-card-price">{item.priceLabel || item.price}</div>
                  </div>
                </article>
              ))}
            </div>
            <button type="button" className="carousel-btn next" onClick={() => scrollCarousel(1)} aria-label="Scroll menu ke kanan">
              &#8250;
            </button>

          </div>

          <div className="menu-footer">
            <button type="button" className="btn-blue">Lihat di Menu →</button>
          </div>

          

        </section>
   <section className="split-image-section">
          <div className="split-image-panel split-image-left">
            <img src={rendaHorizontal} alt="" />
          </div>
          <div className="split-image-panel split-image-right">
            <img src={rendaHorizontal} alt="" />
          </div>
        </section>
        <section className="bundle-section" id="event">
          <div className="bundle-header">
            <div className="section-label">Promo Spesial</div>
            <div className="section-title">Temukan Favoritmu</div>
            <div className="section-sub">Pilih kategori menu kami untuk melihat detail racikan spesial yang kami siapkan khusus untukmu.</div>
          </div>

          {bundles.map((bundle) => (
            <article key={bundle.title} className="bundle-card">
              <div className="bundle-img">
                <img src={bundle.image} alt="" className="bundle-side-image" />
                <div className="bundle-img-placeholder" style={{ background: bundle.accent }}></div>
              </div>
              <div className="bundle-body">
                <span className="bundle-badge">{bundle.badge}</span>
                <div className="bundle-title">{bundle.title}</div>
                <div className="bundle-desc">{bundle.description}</div>
                <div className="bundle-footer">
                  <div>
                    <div className="bundle-price">
                      {bundle.price} <span>{bundle.originalPrice}</span>
                    </div>
                  </div>
                  <button type="button" className="btn-blue" onClick={() => scrollToSection("menu")}>Lihat di Menu →</button>
                </div>
              </div>
            </article>
          ))}
        </section>

        <section className="about-section" id="tentang">
          <div className="about-overlay" />
          <div className="about-content">
            <div className="section-label">Tentang Kami</div>
            <div className="section-title">Lebih dari Sekadar Kopi</div>
            <div className="section-sub">
              Sesua lahir dari kecintaan mendalam terhadap kopi dan keramahan. Kami percaya setiap cangkir adalah pengalaman - bukan sekadar minuman.
            </div>
            <div className="about-stats">
              {stats.map((stat) => (
                <div key={stat.label} className="stat-item">
                  <div className="stat-num">{stat.value}</div>
                  <div className="stat-label">{stat.label}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="cta-banner" id="reservasi">

          <div className="cta-overlay" />
          <div className="cta-content">
            
            <button type="button" className="btn-cta" onClick={navigateToReservationPage}>Buat Reservasi Sekarang</button>
          </div>
        </section>

        
          </>
        )}
      </main>

      <footer id="kontak" className="site-footer">
        <div className="footer-grid">
          <div className="footer-section footer-brand">
            <img src={logoImage} alt="Sesua Cafe Logo" className="footer-logo" />
            <p>Desa Ciawi, kec. Ciawi kab. Bogor depan cempaka 58 B kode pos 16720 nomor 68 rt.05 rw 04, Bogor, Indonesia 16720</p>
          </div>

          <div className="footer-section">
            <div className="footer-title">Kategori &amp; Product</div>
            <a href="/web/menu">Menu Kopi</a>
            <a href="/promo">Promo Spesial</a>
            <a href="/web/reservasi">Reservasi</a>
          </div>

          <div className="footer-section">
            <div className="footer-title">Informasi</div>
            <a href="/web/about">Tentang Kami</a>
            <a href="/web/event">Event</a>
            <a href="/web/kontak">Kontak Kami</a>
          </div>

          <div className="footer-section">
            <div className="footer-title">Social Media</div>
            <div className="footer-socials">
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
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4.5 5h15A2.5 2.5 0 0 1 22 7.5v9A2.5 2.5 0 0 1 19.5 19h-15A2.5 2.5 0 0 1 2 16.5v-9A2.5 2.5 0 0 1 4.5 5Zm0 2c-.1 0-.2.01-.3.04L12 12.1l7.8-5.06a1 1 0 0 0-.3-.04h-15Zm15 10a.5.5 0 0 0 .5-.5V9.08l-7.46 4.84a1 1 0 0 1-1.08 0L4 9.08v7.42a.5.5 0 0 0 .5.5h15Z" />
                </svg>
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
