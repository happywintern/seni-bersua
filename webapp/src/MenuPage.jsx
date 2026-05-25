import React, { useEffect, useMemo, useState } from "react";
import { fetchMenuItemsFromServer } from "./menuCatalogApi";
import { getOutletId } from "./serverConfig";

const fallbackMenuItems = [
  { id: "fallback-1", name: "Signature Espresso", category: "Coffee", ingredients: "Espresso shot", priceLabel: "Rp 35.000", accent: "#f0e6d3", emoji: "☕" },
  { id: "fallback-2", name: "Caramel Latte", category: "Milk Based", ingredients: "Espresso + caramel", priceLabel: "Rp 42.000", accent: "#e8d5c4", emoji: "🥛" },
  { id: "fallback-3", name: "Iced Americano", category: "Coffee", ingredients: "Double espresso", priceLabel: "Rp 32.000", accent: "#d4e8f0", emoji: "🧊" },
];

function MenuPage() {
  const [activeFilter, setActiveFilter] = useState("Semua");
  const [menuItems, setMenuItems] = useState(fallbackMenuItems);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let active = true;
    async function loadMenu() {
      setLoading(true);
      setErrorMessage("");
      try {
        const items = await fetchMenuItemsFromServer(getOutletId());
        if (!active) return;
        setMenuItems(items.length > 0 ? items : fallbackMenuItems);
      } catch (error) {
        if (!active) return;
        setMenuItems(fallbackMenuItems);
        setErrorMessage(error?.message || "Menu server belum bisa dimuat.");
      } finally {
        if (active) setLoading(false);
      }
    }
    loadMenu();
    return () => {
      active = false;
    };
  }, []);

  const categories = useMemo(() => {
    const unique = new Set(menuItems.map((item) => item.category).filter(Boolean));
    return Array.from(unique);
  }, [menuItems]);

  const filters = useMemo(() => ["Semua", ...categories], [categories]);

  const visibleCategories = activeFilter === "Semua" ? categories : [activeFilter];

  return (
    <section className="menu-page">
      <div className="menu-filter-bar" aria-label="Filter menu">
        {filters.map((filter) => (
          <button
            key={filter}
            type="button"
            className={`menu-filter ${activeFilter === filter ? "is-active" : ""}`}
            onClick={() => setActiveFilter(filter)}
          >
            {filter}
          </button>
        ))}
      </div>

      {loading ? <p className="menu-server-note">Memuat menu dari server...</p> : null}
      {!loading && errorMessage ? <p className="menu-server-note menu-server-note-error">{errorMessage}</p> : null}

      <div className="menu-category-list">
        {visibleCategories.map((category) => {
          const categoryItems = menuItems.filter((item) => item.category === category);

          return (
            <div key={category} className="menu-grid-container">
              <div className="menu-grid-image">
                <div className="menu-grid-photo"></div>
                <div className="menu-grid-label">{category}</div>
              </div>

              <div className="menu-page-grid">
                {categoryItems.map((item) => (
                  <article key={item.id} className="menu-page-card">
                    <div className="menu-page-card-img">
                      {item.imageUrl ? (
                        <img src={item.imageUrl} alt={item.name} className="menu-page-card-photo" />
                      ) : (
                        <div className="coffee-placeholder" style={{ background: item.accent }}>{item.emoji}</div>
                      )}
                    </div>
                    <div className="menu-page-card-body">
                      <div className="menu-card-name">{item.name}</div>
                      <div className="menu-card-ingredients">{item.ingredients}</div>
                      <div className="menu-card-price">{item.priceLabel}</div>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

export default MenuPage;
