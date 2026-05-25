import React from "react";
import heroImage from "./assets/images/hero_section.png";


function AboutPage() {
  return (
    <section className="about-page">
      <div className="about-page-header">
        <div className="section-label" style={{ textAlign: 'center' }}>
          Tentang Kami
        </div>
      </div>

      <div className="about-page-hero">
        <div className="about-page-copy">
          <p>
            Sesua Cafe lahir dari keinginan menghadirkan tempat yang ramah untuk bertemu,
            bercerita, dan menikmati cangkir kopi yang terasa dekat dengan keseharian.
          </p>
        </div>
        <div className="about-page-image">
          <img src={heroImage} alt="Suasana Sesua Cafe" />
        </div>
      </div>

      <div className="about-page-hero about-page-hero-reverse">
        <div className="about-page-image">
          <img src={heroImage} alt="Ruang nyaman Sesua Cafe" />
        </div>
        <div className="about-page-copy">
          <div className="section-label">Cerita Sesua</div>
          <p>
            Kami ingin setiap kunjungan terasa seperti jeda kecil yang menyenangkan,
            dari aroma kopi yang hangat sampai ruang yang nyaman untuk berbagi cerita.
          </p>
        </div>
      </div>

    </section>
  );
}

export default AboutPage;
