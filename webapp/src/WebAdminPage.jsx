import React, { useState } from "react";
import { fetchMenuItemsFromServer } from "./menuCatalogApi";
import { getWebServerSettings, setApiBaseUrl, setOutletId } from "./serverConfig";

function WebAdminPage() {
  const initial = getWebServerSettings();
  const [apiBaseUrl, setApiBaseUrlState] = useState(initial.apiBaseUrl);
  const [outletId, setOutletIdState] = useState(initial.outletId);
  const [message, setMessage] = useState("");
  const [testing, setTesting] = useState(false);
  const [previewItems, setPreviewItems] = useState([]);

  function handleSave(event) {
    event.preventDefault();
    setApiBaseUrl(apiBaseUrl);
    setOutletId(outletId);
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
          <div className="web-admin-actions">
            <button type="submit" className="btn-blue">Save Settings</button>
            <button type="button" className="btn-blue btn-secondary" onClick={handleTestConnection} disabled={testing}>
              {testing ? "Testing..." : "Test Product Sync"}
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
      </div>
    </section>
  );
}

export default WebAdminPage;
