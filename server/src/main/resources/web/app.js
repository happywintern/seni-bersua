const { useEffect, useMemo, useState } = React;

const path = window.location.pathname;
const search = new URLSearchParams(window.location.search);
const tableMatch = path.match(/^\/t\/([^/]+)$/);
const isTablePage = Boolean(tableMatch);
const isDashboardPage = path === "/dashboard";
const isAdminPage = path === "/admin";
const tableUuid = tableMatch ? tableMatch[1] : "";
const initialOutletId = search.get("outlet") || "default";
const NEVER_PAIRING_EXPIRY = "9999-12-31T23:59:59Z";
const idNumberFormatter = new Intl.NumberFormat("id-ID", {
  maximumFractionDigits: 0,
});

function normalizeWholeNumber(value) {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? Math.round(numeric) : 0;
}

function formatNumber(value) {
  return idNumberFormatter.format(normalizeWholeNumber(value));
}

function formatRp(value) {
  return `Rp ${formatNumber(value)}`;
}

function formatDateTimeReadable(value) {
  if (!value || typeof value !== "string") return "-";
  const match = value.trim().match(/^(\d{4})-(\d{2})-(\d{2})(?:[Tt\s](\d{2}):(\d{2}))?/);
  if (!match) return value;
  const [, year, month, day, hour, minute] = match;
  const shortYear = year.slice(-2);
  if (hour && minute) {
    return `${day}-${month}-${shortYear} ${hour}:${minute}`;
  }
  return `${day}-${month}-${shortYear}`;
}

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function paymentConfirmationLabel(value) {
  if (!value) return "-";
  if (value === "CASHIER") return "At Cashier";
  if (value === "QRIS") return "QRIS";
  return value;
}

function categoryFromItem(item) {
  const groupName = (item?.groupName || "").trim();
  if (groupName.length > 0) return groupName;

  const groupId = (item?.groupId || "").trim();
  if (groupId.length > 0) return groupId;
  return "Kategori";
}

function lineModifierSignature(modifiers) {
  return modifiers
    .map((mod) => mod.optionId)
    .sort((a, b) => a.localeCompare(b))
    .join("|");
}

function lineModifierSummary(modifiers) {
  if (!modifiers || modifiers.length === 0) return "";
  return modifiers.map((mod) => mod.optionName).join(", ");
}

function createCatalogModel(raw) {
  const items = Array.isArray(raw?.items) ? raw.items : [];
  const groups = Array.isArray(raw?.modifierGroups) ? raw.modifierGroups : [];
  const links = Array.isArray(raw?.productModifierLinks) ? raw.productModifierLinks : [];

  const groupMap = new Map(groups.map((group) => [group.id, group]));
  const linkMap = new Map();
  links.forEach((link) => {
    linkMap.set(link.itemId, Array.isArray(link.modifierGroupIds) ? link.modifierGroupIds : []);
  });

  return { items, groups, links, groupMap, linkMap };
}

async function api(path, options = {}) {
  const { bearerToken, headers: customHeaders, ...restOptions } = options;
  const headers = {
    "Content-Type": "application/json",
    ...(customHeaders || {}),
  };
  if (typeof bearerToken === "string" && bearerToken.trim().length > 0) {
    headers.Authorization = `Bearer ${bearerToken.trim()}`;
  }
  const response = await fetch(path, {
    headers,
    ...restOptions,
  });
  const contentType = response.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");
  const body = isJson ? await response.json() : await response.text();
  const isApiRoute = path.startsWith("/api/");

  if (isJson && body && typeof body === "object" && ("data" in body || "error" in body)) {
    if (!response.ok || body.error) {
      throw new Error(body.error || body.message || `HTTP ${response.status}`);
    }
    return body.data;
  }

  if (isApiRoute) {
    const text = typeof body === "string" ? body : JSON.stringify(body);
    throw new Error(`Invalid API envelope response: ${text || `HTTP ${response.status}`}`);
  }

  if (!response.ok) {
    const text = typeof body === "string" ? body : JSON.stringify(body);
    throw new Error(text || `HTTP ${response.status}`);
  }
  return body;
}

function HomePage() {
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(true);
  const [seeding, setSeeding] = useState(false);
  const [message, setMessage] = useState("");

  async function refreshTables() {
    setLoading(true);
    try {
      const data = await api(`/api/tables?outlet=${encodeURIComponent(initialOutletId)}`);
      setTables(Array.isArray(data) ? data : []);
      setMessage("");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refreshTables();
  }, []);

  async function seedTables() {
    setSeeding(true);
    setMessage("");
    try {
      const seeded = await api("/api/tables/seed", {
        method: "POST",
        body: JSON.stringify({
          data: { count: 10, outlet_id: initialOutletId },
          message: "Seed 10 tables",
          error: null,
        }),
      });
      setTables(Array.isArray(seeded) ? seeded : []);
      setMessage("10 table QR targets are ready.");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setSeeding(false);
    }
  }

  return (
    <main className="container">
      <section className="hero">
        <h1>SuCash</h1>
        <p>Table ordering gateway for QR menu and cashier workflow.</p>
        <div className="subtext">Total table target: {formatNumber(tables.length)}</div>
        <div className="hero-links">
          <a href="/dashboard" className="btn">Open Cashier Dashboard</a>
          <a href="/admin" className="btn">Open Server Admin</a>
          <button className="btn" onClick={seedTables} disabled={seeding}>
            {seeding ? "Seeding..." : "Generate 10 Tables"}
          </button>
          <button className="btn" onClick={refreshTables} disabled={loading}>
            {loading ? "Loading..." : "Refresh Tables"}
          </button>
        </div>
        {message ? <p className="message">{message}</p> : null}
      </section>

      <section className="card">
        <h2>Seeded Table QR Targets</h2>
        <p>Each row is a table UUID route (`/t/{'{'}uuid{'}'}`).</p>
        {loading ? <p>Loading tables...</p> : (
          <ul className="list table-list">
            {tables.map((table) => (
              <li key={table.uuid} className="list-row table-row">
                <div>
                  <strong>{table.name}</strong>
                  <div className="subtext">{table.uuid}</div>
                  <div className="subtext">Outlet: {table.outletId || table.outlet_id || initialOutletId}</div>
                </div>
                <a
                  className="btn btn-primary"
                  href={`/t/${table.uuid}?outlet=${encodeURIComponent(table.outletId || table.outlet_id || initialOutletId)}`}
                >
                  Open Order
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

function TableOrderingPage() {
  const [table, setTable] = useState(null);
  const [catalog, setCatalog] = useState(createCatalogModel({}));
  const [query, setQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState("Semua Produk");
  const [outletId, setOutletId] = useState(initialOutletId);
  const [cart, setCart] = useState([]);
  const [showCart, setShowCart] = useState(false);
  const [paymentConfirmation, setPaymentConfirmation] = useState("CASHIER");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [customizeState, setCustomizeState] = useState(null);
  const hasQuery = query.trim().length > 0;

  async function loadAll() {
    try {
      let scopedOutletId = outletId;
      const resolvedScope = await api(`/api/tables/${tableUuid}/outlet`);
      const resolvedOutletId = resolvedScope?.outletId || resolvedScope?.outlet_id;
      if (resolvedOutletId && resolvedOutletId !== outletId) {
        scopedOutletId = resolvedOutletId;
        setOutletId(resolvedOutletId);
      }
      const [tableData, catalogData] = await Promise.all([
        api(`/api/tables/${tableUuid}?outlet=${encodeURIComponent(scopedOutletId)}`),
        api(`/api/menu/catalog?outlet=${encodeURIComponent(scopedOutletId)}`),
      ]);
      setTable(tableData);
      setCatalog(createCatalogModel(catalogData));
      setMessage("");
    } catch (error) {
      setCatalog(createCatalogModel({}));
      setMessage(error.message);
    }
  }

  useEffect(() => {
    loadAll();
  }, [outletId]);

  const categories = useMemo(() => {
    const values = new Set(["Semua Produk"]);
    catalog.items.forEach((item) => values.add(categoryFromItem(item)));
    return Array.from(values);
  }, [catalog.items]);

  const filteredItems = useMemo(() => {
    return catalog.items.filter((item) => {
      const category = categoryFromItem(item);
      const categoryOk = activeCategory === "Semua Produk" || category === activeCategory;
      const queryOk =
        query.trim().length === 0 ||
        item.name.toLowerCase().includes(query.trim().toLowerCase()) ||
        item.id.toLowerCase().includes(query.trim().toLowerCase());
      return categoryOk && queryOk;
    });
  }, [catalog.items, activeCategory, query]);

  const cartCount = useMemo(
    () => cart.reduce((sum, line) => sum + line.qty, 0),
    [cart]
  );

  const subtotal = useMemo(
    () => cart.reduce((sum, line) => sum + line.unitPrice * line.qty, 0),
    [cart]
  );

  function qtyForItem(itemId) {
    return cart
      .filter((line) => line.itemId === itemId)
      .reduce((sum, line) => sum + line.qty, 0);
  }

  function getGroupsForItem(itemId) {
    const groupIds = catalog.linkMap.get(itemId) || [];
    return groupIds
      .map((groupId) => catalog.groupMap.get(groupId))
      .filter(Boolean);
  }

  function defaultSelections(item) {
    const state = {};
    getGroupsForItem(item.id).forEach((group) => {
      const options = Array.isArray(group.options) ? group.options : [];
      const defaultOption = options.find((option) => option.isDefault) || options[0];
      if (defaultOption && (group.isRequired || group.selectionType === "SINGLE")) {
        state[group.id] = [defaultOption.id];
      }
    });
    return state;
  }

  function selectedModifiersFromCustom(custom) {
    if (!custom) return [];
    return custom.groups.flatMap((group) => {
      const selectedIds = custom.selections[group.id] || [];
      const options = Array.isArray(group.options) ? group.options : [];
      return selectedIds
        .map((optionId) => options.find((option) => option.id === optionId))
        .filter(Boolean)
        .map((option) => ({
          optionId: option.id,
          optionName: option.name,
          groupId: group.id,
          groupName: group.name,
          priceDelta: option.priceDelta || 0,
        }));
    });
  }

  function openCustomizer(item, source = "add", line = null) {
    const groups = getGroupsForItem(item.id);
    if (groups.length === 0 && source === "add") {
      addDirect(item);
      return;
    }
    const existingSelections = {};
    if (line) {
      line.modifiers.forEach((modifier) => {
        existingSelections[modifier.groupId] = existingSelections[modifier.groupId] || [];
        existingSelections[modifier.groupId].push(modifier.optionId);
      });
    }
    const startingSelections = Object.keys(existingSelections).length > 0
      ? existingSelections
      : defaultSelections(item);

    setCustomizeState({
      mode: line ? "edit" : "add",
      source,
      item,
      lineId: line?.id || null,
      groups,
      qty: line?.qty || 1,
      selections: startingSelections,
    });
  }

  function closeCustomizer() {
    setCustomizeState(null);
  }

  function toggleOption(group, optionId) {
    setCustomizeState((prev) => {
      if (!prev) return prev;
      const current = prev.selections[group.id] || [];
      const has = current.includes(optionId);
      const maxSelection = Math.max(1, Number(group.maxSelection || 1));
      let next;
      if ((group.selectionType || "").toUpperCase() === "SINGLE") {
        next = has ? [] : [optionId];
      } else if (has) {
        next = current.filter((id) => id !== optionId);
      } else if (current.length >= maxSelection) {
        next = [...current.slice(1), optionId];
      } else {
        next = [...current, optionId];
      }
      return {
        ...prev,
        selections: {
          ...prev.selections,
          [group.id]: next,
        },
      };
    });
  }

  function setCustomizerQty(nextQty) {
    setCustomizeState((prev) => {
      if (!prev) return prev;
      return { ...prev, qty: Math.max(1, nextQty) };
    });
  }

  function addDirect(item) {
    const signature = `${item.id}::`;
    setCart((prev) => {
      const index = prev.findIndex((line) => line.signature === signature);
      if (index >= 0) {
        const copy = [...prev];
        copy[index] = { ...copy[index], qty: copy[index].qty + 1 };
        return copy;
      }
      return [
        ...prev,
        {
          id: `line_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          itemId: item.id,
          itemName: item.name,
          unitPrice: item.price,
          qty: 1,
          modifiers: [],
          modifierSummary: "",
          signature,
        },
      ];
    });
  }

  function decrementItem(item) {
    setCart((prev) => {
      const targetIndex = prev.findIndex((line) => line.itemId === item.id);
      if (targetIndex < 0) return prev;
      const copy = [...prev];
      const target = copy[targetIndex];
      if (target.qty <= 1) {
        copy.splice(targetIndex, 1);
      } else {
        copy[targetIndex] = { ...target, qty: target.qty - 1 };
      }
      return copy;
    });
  }

  function applyCustomizer() {
    if (!customizeState) return;

    const modifiers = selectedModifiersFromCustom(customizeState);
    const missingRequiredGroup = customizeState.groups.some((group) => {
      if (!group.isRequired) return false;
      const selected = customizeState.selections[group.id] || [];
      return selected.length === 0;
    });
    if (missingRequiredGroup) {
      setMessage("Lengkapi modifier yang wajib dipilih.");
      return;
    }

    const signature = `${customizeState.item.id}::${lineModifierSignature(modifiers)}`;
    const unitPrice = customizeState.item.price + modifiers.reduce((sum, mod) => sum + Number(mod.priceDelta || 0), 0);
    const modifierSummary = lineModifierSummary(modifiers);

    setCart((prev) => {
      if (customizeState.mode === "edit" && customizeState.lineId) {
        return prev.map((line) => {
          if (line.id !== customizeState.lineId) return line;
          return {
            ...line,
            unitPrice,
            qty: customizeState.qty,
            modifiers,
            modifierSummary,
            signature,
          };
        });
      }

      const existingIndex = prev.findIndex((line) => line.signature === signature);
      if (existingIndex >= 0) {
        const copy = [...prev];
        copy[existingIndex] = {
          ...copy[existingIndex],
          qty: copy[existingIndex].qty + customizeState.qty,
        };
        return copy;
      }
      return [
        ...prev,
        {
          id: `line_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          itemId: customizeState.item.id,
          itemName: customizeState.item.name,
          unitPrice,
          qty: customizeState.qty,
          modifiers,
          modifierSummary,
          signature,
        },
      ];
    });

    setMessage("");
    closeCustomizer();
  }

  function updateLineQty(lineId, nextQty) {
    setCart((prev) => {
      if (nextQty <= 0) {
        return prev.filter((line) => line.id !== lineId);
      }
      return prev.map((line) => line.id === lineId ? { ...line, qty: nextQty } : line);
    });
  }

  function openEditLine(line) {
    const item = catalog.items.find((menu) => menu.id === line.itemId);
    if (!item) return;
    openCustomizer(item, "edit", line);
  }

  async function checkout() {
    if (cart.length === 0) {
      setMessage("Keranjang masih kosong.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      const payload = {
        customerUuid: tableUuid,
        outlet_id: outletId,
        paymentConfirmation: paymentConfirmation || null,
        note: null,
        items: cart.map((line) => ({
          menuId: line.itemId,
          qty: line.qty,
          note: line.modifierSummary || null,
          modifiers: line.modifiers.map((modifier) => ({
            optionId: modifier.optionId,
            optionName: modifier.optionName,
          })),
        })),
      };
      const created = await api("/api/orders", {
        method: "POST",
        body: JSON.stringify({
          data: payload,
          message: "Create order request",
          error: null,
        }),
      });
      setMessage(`Pesanan ${created.id.slice(0, 8)} berhasil dibuat. Silakan tunggu.`);
      setCart([]);
      setShowCart(false);
      setConfirmVisible(false);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  const customTotal = customizeState
    ? (customizeState.item.price +
        selectedModifiersFromCustom(customizeState).reduce((sum, mod) => sum + Number(mod.priceDelta || 0), 0)) *
      customizeState.qty
    : 0;

  return (
    <main className="container table-container">
      <section className="table-shell">
        <header className="table-header">
          <input
            className="search-input"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Cari Menu"
          />
          <button className="icon-btn" type="button" onClick={loadAll} aria-label="Refresh">↻</button>
          <button className="icon-btn cart-btn" type="button" onClick={() => setShowCart((prev) => !prev)} aria-label="Open cart">
            🛒
            <span className="badge">{cartCount}</span>
          </button>
        </header>

        <div className="category-row">
          {categories.map((category) => (
            <button
              key={category}
              className={`chip-btn ${activeCategory === category ? "chip-active" : "chip-muted"}`}
              onClick={() => setActiveCategory(category)}
            >
              {category}
            </button>
          ))}
        </div>

        {!showCart ? (
          <section className="product-grid">
            {filteredItems.length === 0 ? (
              <div className="empty-state">
                <strong>{hasQuery ? "Menu Tidak Ditemukan" : "Menu Belum Tersedia"}</strong>
                <p className="subtext">
                  {hasQuery
                    ? "Maaf, menu yang Anda cari tidak tersedia. Silakan periksa kembali kata kunci pencarian Anda."
                    : "Maaf, menu untuk outlet ini belum tersedia. Silakan coba lagi beberapa saat."}
                </p>
              </div>
            ) : filteredItems.map((item) => {
              const qty = qtyForItem(item.id);
              const hasModifier = getGroupsForItem(item.id).length > 0;
              return (
                <article className="product-card" key={item.id}>
                  <div className="product-thumb">
                    <span className="product-cat">{categoryFromItem(item)}</span>
                  </div>
                  <div className="product-body">
                    <h3>{item.name}</h3>
                    <p className="subtext">{item.id.toUpperCase()}</p>
                    <p className="price-text">{formatRp(item.price)}</p>
                    <p className="subtext">Stok: 50</p>
                    {qty <= 0 ? (
                      <button className="btn btn-primary btn-fill" onClick={() => openCustomizer(item, "add")}>
                        {hasModifier ? "Pilih" : "Tambah"}
                      </button>
                    ) : (
                      <div className="qty-pill">
                        <button className="btn btn-small" onClick={() => decrementItem(item)}>-</button>
                        <span>{qty}</span>
                        <button className="btn btn-small" onClick={() => openCustomizer(item, "add")}>+</button>
                      </div>
                    )}
                  </div>
                </article>
              );
            })}
          </section>
        ) : (
          <section className="cart-screen">
            <header className="cart-head">
              <div>
                <div className="subtext">Pesanan Saat Ini</div>
                <strong>#{tableUuid.slice(0, 2)}</strong>
              </div>
              <div className="subtext">{table?.name || "No Meja"}</div>
            </header>
            <h2>Item Dipilih</h2>
            <div className="cart-list-wrap">
              {cart.length === 0 ? (
                <p className="subtext">Belum ada item dipilih.</p>
              ) : (
                <ul className="list cart-list">
                  {cart.map((line) => (
                    <li key={line.id} className="cart-item">
                      <div className="cart-item-thumb" />
                      <div className="cart-item-body">
                        <strong>{line.itemName}</strong>
                        {line.modifierSummary ? <p className="subtext">{line.modifierSummary}</p> : null}
                        <p className="subtext">{formatRp(line.unitPrice)} x {line.qty}</p>
                        <strong className="price-text">{formatRp(line.unitPrice * line.qty)}</strong>
                      </div>
                      <div className="cart-item-actions">
                        <button className="btn btn-small" onClick={() => openEditLine(line)}>✎</button>
                        <button className="btn btn-small" onClick={() => updateLineQty(line.id, line.qty - 1)}>-</button>
                        <span>{line.qty}</span>
                        <button className="btn btn-small" onClick={() => updateLineQty(line.id, line.qty + 1)}>+</button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <section className="summary-card">
              <h3>Ringkasan Pembayaran</h3>
              <div className="summary-row"><span>Item ({cartCount})</span><span>{formatRp(subtotal)}</span></div>
              <div className="summary-row"><span>Diskon</span><span>{formatRp(0)}</span></div>
              <div className="summary-row total"><span>Total</span><strong>{formatRp(subtotal)}</strong></div>

              <h3>Metode Pembayaran</h3>
              <div className="actions">
                <button
                  className={`btn btn-small ${paymentConfirmation === "CASHIER" ? "btn-primary" : ""}`}
                  onClick={() => setPaymentConfirmation("CASHIER")}
                >
                  Bayar di Kasir
                </button>
                <button
                  className={`btn btn-small ${paymentConfirmation === "QRIS" ? "btn-primary" : ""}`}
                  onClick={() => setPaymentConfirmation("QRIS")}
                >
                  QRIS
                </button>
              </div>
              <button className="btn btn-primary btn-fill" disabled={busy || cart.length === 0} onClick={() => setConfirmVisible(true)}>
                {busy ? "Memproses..." : "Buat Pesanan"}
              </button>
              {cart.length === 0 ? (
                <p className="subtext">Pilih minimal 1 item dulu untuk membuat pesanan.</p>
              ) : null}
            </section>
          </section>
        )}

        {message ? <p className="message">{message}</p> : null}
      </section>

      {customizeState ? (
        <div className="modal-overlay" onClick={closeCustomizer}>
          <div className="sheet-card" onClick={(event) => event.stopPropagation()}>
            <div className="sheet-head">
              <h2>{customizeState.mode === "edit" ? "edit" : "Customize"}</h2>
              <button className="icon-btn" onClick={closeCustomizer}>✕</button>
            </div>
            {customizeState.groups.map((group) => (
              <section key={group.id} className="modifier-group">
                <h3>{group.name}</h3>
                <div className="chip-wrap">
                  {(group.options || []).map((option) => {
                    const selected = (customizeState.selections[group.id] || []).includes(option.id);
                    const delta = Number(option.priceDelta || 0);
                    return (
                      <button
                        key={option.id}
                        className={`chip-btn ${selected ? "chip-active" : "chip-muted"}`}
                        onClick={() => toggleOption(group, option.id)}
                      >
                        {option.name}
                        {delta > 0 ? ` + ${formatRp(delta)}` : ""}
                      </button>
                    );
                  })}
                </div>
              </section>
            ))}
            <div className="sheet-footer">
              <div>
                <strong>{formatRp(customTotal)}</strong>
                <div className="qty-pill compact">
                  <button className="btn btn-small" onClick={() => setCustomizerQty(customizeState.qty - 1)}>-</button>
                  <span>{customizeState.qty}</span>
                  <button className="btn btn-small" onClick={() => setCustomizerQty(customizeState.qty + 1)}>+</button>
                </div>
              </div>
              <button className="btn btn-primary" onClick={applyCustomizer}>
                {customizeState.mode === "edit" ? "Simpan" : "Masukkan Keranjang"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {confirmVisible ? (
        <div className="modal-overlay" onClick={() => setConfirmVisible(false)}>
          <div className="confirm-card" onClick={(event) => event.stopPropagation()}>
            <h3>Apakah sudah benar pesanan yang dibuat?</h3>
            <p className="confirm-description">
              Pesanan yang sudah diproses dan dibayar tidak dapat diubah atau dibatalkan. Lanjutkan?
            </p>
            <div className="actions confirm-actions">
              <button className="btn chip-muted" onClick={() => setConfirmVisible(false)}>Tidak</button>
              <button className="btn btn-primary" onClick={checkout}>Ya</button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}

function DashboardPage() {
  const [orders, setOrders] = useState([]);
  const [recap, setRecap] = useState(null);
  const [recapRange, setRecapRange] = useState("TODAY");
  const [anchorDate, setAnchorDate] = useState(todayIsoDate());
  const [customFromDate, setCustomFromDate] = useState(todayIsoDate());
  const [customToDate, setCustomToDate] = useState(todayIsoDate());
  const [useCustomDate, setUseCustomDate] = useState(false);
  const [outletId, setOutletId] = useState(initialOutletId);
  const [message, setMessage] = useState("");
  const [doneConfirmOrderId, setDoneConfirmOrderId] = useState(null);

  async function loadOrders() {
    try {
      const data = await api(`/api/orders?status=NEW,ACCEPTED,PREPARING,SERVED&outlet=${encodeURIComponent(outletId)}`);
      setOrders(Array.isArray(data) ? data : []);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function loadRecap() {
    try {
      const params = new URLSearchParams({
        range: useCustomDate ? "ALL" : recapRange,
        date: anchorDate,
        outlet: outletId,
      });
      if (useCustomDate) {
        if (customFromDate) params.set("from", customFromDate);
        if (customToDate) params.set("to", customToDate);
      }
      const data = await api(`/api/recap/summary?${params.toString()}`);
      setRecap(data || null);
    } catch (error) {
      setMessage(error.message);
    }
  }

  const recapAnchorDate = recap?.anchorDate ?? recap?.anchor_date ?? "-";
  const recapTransaksiCount = recap?.transaksiCount ?? recap?.transaksi_count ?? 0;
  const recapGrossTotal = recap?.grossTotal ?? recap?.gross_total ?? 0;
  const recapTotalDiscount = recap?.totalDiscount ?? recap?.total_discount ?? 0;
  const recapAverageTicket = recap?.averageTicket ?? recap?.average_ticket ?? 0;
  const recapPaymentBreakdown = Array.isArray(recap?.paymentBreakdown)
    ? recap.paymentBreakdown
    : (Array.isArray(recap?.payment_breakdown) ? recap.payment_breakdown : []);

  useEffect(() => {
    loadOrders();
    loadRecap();
    const timer = setInterval(() => {
      loadOrders();
      loadRecap();
    }, 5000);
    return () => clearInterval(timer);
  }, [outletId, recapRange, anchorDate, useCustomDate, customFromDate, customToDate]);

  async function setStatus(orderId, status) {
    try {
      await api(`/api/orders/${orderId}/status`, {
        method: "POST",
        body: JSON.stringify({
          data: { status, outlet_id: outletId },
          message: "Update order status request",
          error: null,
        }),
      });
      await loadOrders();
    } catch (error) {
      setMessage(error.message);
    }
  }

  return (
    <main className="container">
      <section className="hero dashboard-hero">
        <div className="section-header">
          <div>
            <h1>Cashier Dashboard</h1>
            <p>Incoming orders, recap transaksi, dan angka rupiah yang lebih mudah dibaca.</p>
          </div>
          <div className="metric-chip">{formatNumber(orders.length)} order aktif</div>
        </div>
        <div className="dashboard-toolbar">
          <div className="filter-grid">
            <div className="field-block">
              <label className="label">Outlet</label>
              <input
                className="input"
                value={outletId}
                onChange={(e) => setOutletId(e.target.value || "default")}
                placeholder="default"
              />
            </div>
            <div className="field-block">
              <label className="label">Anchor Date</label>
              <input
                className="input"
                type="date"
                value={anchorDate}
                onChange={(e) => setAnchorDate(e.target.value || todayIsoDate())}
              />
            </div>
          </div>
          <div className="actions">
            {["TODAY", "WEEK", "MONTH", "ALL"].map((range) => (
              <button
                key={range}
                className={`btn btn-small ${recapRange === range ? "btn-primary" : ""}`}
                onClick={() => {
                  setUseCustomDate(false);
                  setRecapRange(range);
                }}
              >
                {range}
              </button>
            ))}
            <button
              className={`btn btn-small ${useCustomDate ? "btn-primary" : ""}`}
              onClick={() => setUseCustomDate(true)}
            >
              CUSTOM
            </button>
          </div>
          {useCustomDate ? (
            <div className="filter-grid">
              <div className="field-block">
                <label className="label">From</label>
                <input
                  className="input"
                  type="date"
                  value={customFromDate}
                  onChange={(e) => setCustomFromDate(e.target.value || todayIsoDate())}
                />
              </div>
              <div className="field-block">
                <label className="label">To</label>
                <input
                  className="input"
                  type="date"
                  value={customToDate}
                  onChange={(e) => setCustomToDate(e.target.value || todayIsoDate())}
                />
              </div>
            </div>
          ) : null}
          <p className="toolbar-note">
            Semua nominal dirapikan ke format Indonesia, misalnya Rp 125.000 dan 1.250 transaksi.
          </p>
        </div>
      </section>

      <section className="card">
        <div className="section-header">
          <div>
            <h2>Recap Summary</h2>
            {recap ? (
              <p className="subtext">Range: {recap.range} • Anchor: {recapAnchorDate}</p>
            ) : (
              <p className="subtext">Menyiapkan ringkasan transaksi.</p>
            )}
          </div>
          {recap ? <div className="metric-chip">{formatNumber(recapTransaksiCount)} transaksi</div> : null}
        </div>
        {recap ? (
          <>
            {useCustomDate ? (
              <div className="subtext">Custom: {customFromDate || "-"} → {customToDate || "-"}</div>
            ) : null}
            <div className="metrics-grid">
              <div className="metric-card">
                <div className="subtext">Transactions</div>
                <strong className="metric-value">{formatNumber(recapTransaksiCount)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Gross Total</div>
                <strong className="metric-value amount">{formatRp(recapGrossTotal)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Discount</div>
                <strong className="metric-value amount">{formatRp(recapTotalDiscount)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Average Ticket</div>
                <strong className="metric-value amount">{formatRp(recapAverageTicket)}</strong>
              </div>
            </div>

            <div className="dashboard-grid">
              <div className="panel-card">
                <h3>Payment Breakdown</h3>
                {recapPaymentBreakdown.length === 0 ? (
                  <p>No payment data.</p>
                ) : (
                  <ul className="list">
                    {recapPaymentBreakdown.map((row) => (
                      <li className="list-row" key={row.methodId || row.method_id || "method"}>
                        <span>{row.methodName || row.method_name} ({formatNumber(row.transactionCount || row.transaction_count || 0)})</span>
                        <strong className="amount">{formatRp(row.total)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="panel-card">
                <h3>Dashboard Notes</h3>
                <div className="insight-card">
                  <div className="insight-label">Readable Numbers</div>
                  <p>Semua angka sekarang ditampilkan dengan pemisah ribuan Indonesia supaya nominal besar lebih cepat dibaca kasir.</p>
                  <div className="subtext">Contoh: 1250000 menjadi {formatRp(1250000)} dan 1250 menjadi {formatNumber(1250)}.</div>
                </div>
              </div>
            </div>
          </>
        ) : (
          <p>Loading recap...</p>
        )}
      </section>

      <section className="card">
        <div className="section-header">
          <div>
            <h2>Orders</h2>
            <p className="subtext">Pantau order masuk dan ubah status tanpa pindah halaman.</p>
          </div>
          <div className="metric-chip">{formatNumber(orders.length)} order</div>
        </div>
        {orders.length === 0 ? <p>No orders yet.</p> : (
          <ul className="list">
            {orders.map((order) => (
              <li key={order.id} className="order-card">
                <div className="list-row">
                  <div>
                    <strong>{order.customerName}</strong>
                    <div className="subtext">Table: {order.customerUuid}</div>
                  </div>
                  <span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>
                </div>
                <div className="subtext">Order ID: {order.id}</div>
                <div className="subtext">
                  Waktu: {formatDateTimeReadable(order.createdAt || order.created_at || order.updatedAt || order.updated_at)}
                </div>
                {order.note ? <div className="subtext">Note: {order.note}</div> : null}
                <div className="subtext">
                  Payment Confirmation: {paymentConfirmationLabel(order.paymentConfirmation)}
                </div>
                <ul className="list compact">
                  {(Array.isArray(order.items) ? order.items : []).map((item) => (
                    <li key={item.id} className="list-row">
                      <div>
                        <span>{item.itemName} x{formatNumber(item.qty)}</span>
                        {item.note ? <div className="subtext">{item.note}</div> : null}
                      </div>
                      <span className="amount">{formatRp(item.lineTotal)}</span>
                    </li>
                  ))}
                </ul>
                <div className="checkout-row">
                  <strong>Total</strong>
                  <strong className="amount">{formatRp(order.total)}</strong>
                </div>
                <div className="actions">
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "ACCEPTED")}>Accept</button>
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "PREPARING")}>Preparing</button>
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "SERVED")}>Served</button>
                  <button className="btn btn-small" onClick={() => setDoneConfirmOrderId(order.id)}>Done</button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {message ? <p className="message">{message}</p> : null}
      </section>

      {doneConfirmOrderId ? (
        <div className="modal-overlay" onClick={() => setDoneConfirmOrderId(null)}>
          <div className="confirm-card" onClick={(event) => event.stopPropagation()}>
            <h3>Selesaikan pesanan ini?</h3>
            <p className="confirm-description">
              Setelah status menjadi Done, pesanan dianggap selesai dan tidak bisa kembali ke status sebelumnya.
            </p>
            <div className="actions confirm-actions">
              <button className="btn chip-muted" onClick={() => setDoneConfirmOrderId(null)}>Batal</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  const targetOrderId = doneConfirmOrderId;
                  setDoneConfirmOrderId(null);
                  await setStatus(targetOrderId, "DONE");
                }}
              >
                Ya, Done
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}

function AdminPage() {
  const [outletId, setOutletId] = useState(initialOutletId);
  const [outlets, setOutlets] = useState([]);
  const [tables, setTables] = useState([]);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [tablesMessage, setTablesMessage] = useState("");
  const [tableSeedCount, setTableSeedCount] = useState(10);
  const [tableSeedBusy, setTableSeedBusy] = useState(false);
  const [newOutletId, setNewOutletId] = useState("");
  const [newOutletName, setNewOutletName] = useState("");
  const [newOutletActive, setNewOutletActive] = useState(true);
  const [pairingCode, setPairingCode] = useState("");
  const [pairingExpiry, setPairingExpiry] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function loadOutlets() {
    setBusy(true);
    setMessage("");
    try {
      const data = await api("/api/outlets");
      setOutlets(Array.isArray(data) ? data : []);
      setMessage("Outlet list updated.");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function loadTablesByOutlet(targetOutletId = outletId) {
    const scopedOutletId = (targetOutletId || "").trim() || "default";
    setTablesLoading(true);
    setTablesMessage("");
    try {
      const data = await api(`/api/tables?outlet=${encodeURIComponent(scopedOutletId)}`);
      setTables(Array.isArray(data) ? data : []);
    } catch (error) {
      setTables([]);
      setTablesMessage(error.message);
    } finally {
      setTablesLoading(false);
    }
  }

  async function seedTablesForOutlet(targetOutletId = outletId) {
    const scopedOutletId = (targetOutletId || "").trim() || "default";
    const normalizedCount = Number(tableSeedCount);
    if (!Number.isFinite(normalizedCount) || normalizedCount <= 0) {
      setTablesMessage("Jumlah table harus lebih dari 0.");
      return;
    }
    setTableSeedBusy(true);
    setTablesMessage("");
    try {
      const seeded = await api("/api/tables/seed", {
        method: "POST",
        body: JSON.stringify({
          data: {
            outlet_id: scopedOutletId,
            count: Math.floor(normalizedCount),
          },
          message: "Web admin tables seed",
          error: null,
        }),
      });
      setTables(Array.isArray(seeded) ? seeded : []);
      setTablesMessage(`Berhasil generate ${Array.isArray(seeded) ? seeded.length : 0} table untuk outlet ${scopedOutletId}.`);
    } catch (error) {
      setTablesMessage(error.message);
    } finally {
      setTableSeedBusy(false);
    }
  }

  async function upsertOutlet() {
    const normalizedId = newOutletId.trim();
    const normalizedName = newOutletName.trim();
    if (!normalizedId || !normalizedName) {
      setMessage("Outlet ID and Outlet Name are required.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      const saved = await api("/api/outlets/upsert", {
        method: "POST",
        body: JSON.stringify({
          data: {
            outlet_id: normalizedId,
            name: normalizedName,
            active: newOutletActive,
          },
          message: "Web admin outlet upsert",
          error: null,
        }),
      });
      setOutletId(saved?.outletId || saved?.outlet_id || normalizedId);
      setNewOutletId("");
      setNewOutletName("");
      await loadOutlets();
      await loadTablesByOutlet(saved?.outletId || saved?.outlet_id || normalizedId);
      setMessage(`Outlet ${saved?.outletId || saved?.outlet_id || normalizedId} saved.`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function setOutletStatus(targetOutletId, active) {
    setBusy(true);
    setMessage("");
    try {
      await api(`/api/outlets/${encodeURIComponent(targetOutletId)}/status`, {
        method: "POST",
        body: JSON.stringify({
          data: {
            outlet_id: targetOutletId,
            active,
          },
          message: "Web admin outlet status update",
          error: null,
        }),
      });
      await loadOutlets();
      await loadTablesByOutlet(targetOutletId);
      setMessage(`Outlet ${targetOutletId} is now ${active ? "active" : "inactive"}.`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function createPairingCode() {
    setBusy(true);
    setMessage("");
    try {
      const generated = await api("/api/auth/pairing/create", {
        method: "POST",
        body: JSON.stringify({
          data: {
            role: "OWNER",
            outlet_id: outletId,
            ttl_seconds: 0,
          },
          message: "Web admin pairing code create",
          error: null,
        }),
      });
      setPairingCode(generated?.pairingCode || generated?.pairing_code || "");
      setPairingExpiry(generated?.expiresAt || generated?.expires_at || "");
      setMessage("Owner pairing code ready. This code is one-time and stays valid until redeemed.");
    } catch (error) {
      setMessage(error.message);
      setPairingCode("");
      setPairingExpiry("");
    } finally {
      setBusy(false);
    }
  }

  function copyPairingCode() {
    if (!pairingCode) return;
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(pairingCode);
      setMessage("Pairing code copied.");
    }
  }

  useEffect(() => {
    loadOutlets();
    loadTablesByOutlet(outletId);
  }, []);

  useEffect(() => {
    loadTablesByOutlet(outletId);
  }, [outletId]);

  return (
    <main className="container">
      <section className="hero dashboard-hero">
        <div className="section-header">
          <div>
            <h1>Server Admin</h1>
            <p>Provision outlet dan generate pairing code tanpa perlu terminal command.</p>
          </div>
          <div className="actions">
            <a href="/" className="btn">Home</a>
            <a href="/dashboard" className="btn">Dashboard</a>
          </div>
        </div>
        <div className="insight-card">
          <div className="insight-label">Quick Onboarding</div>
          <p>1) Create/activate outlet. 2) Generate owner pairing code. 3) Redeem pairing code from mobile.</p>
        </div>
      </section>

      <section className="card">
        <h2>Step 1 · Outlet Provisioning</h2>
        <label className="label">Outlet Scope</label>
        <input
          className="input"
          value={outletId}
          onChange={(event) => setOutletId(event.target.value || "default")}
          placeholder="default"
        />
        <div className="actions">
          <button className="btn" onClick={loadOutlets} disabled={busy}>Refresh Outlets</button>
          <button
            className="btn"
            onClick={() => loadTablesByOutlet(outletId)}
            disabled={tablesLoading}
          >
            {tablesLoading ? "Loading Tables..." : "Refresh Tables"}
          </button>
        </div>
        <div className="filter-grid">
          <div className="field-block">
            <label className="label">Outlet ID</label>
            <input
              className="input"
              value={newOutletId}
              onChange={(event) => setNewOutletId(event.target.value)}
              placeholder="main-branch"
            />
          </div>
          <div className="field-block">
            <label className="label">Outlet Name</label>
            <input
              className="input"
              value={newOutletName}
              onChange={(event) => setNewOutletName(event.target.value)}
              placeholder="Main Branch"
            />
          </div>
        </div>
        <label className="toggle-row">
          <input
            type="checkbox"
            checked={newOutletActive}
            onChange={(event) => setNewOutletActive(event.target.checked)}
          />
          <span>Set active immediately</span>
        </label>
        <div className="actions">
          <button className="btn btn-primary" onClick={upsertOutlet} disabled={busy}>Save Outlet</button>
        </div>

        <div className="list" style={{ marginTop: "14px" }}>
          {outlets.length === 0 ? (
            <div className="subtext">No outlets loaded yet.</div>
          ) : (
            outlets.map((outlet) => {
              const id = outlet.outletId || outlet.outlet_id;
              const isActive = Boolean(outlet.active);
              return (
                <div key={id} className="table-row">
                  <div className="list-row">
                    <div>
                      <strong>{outlet.name}</strong>
                      <div className="subtext">{id}</div>
                    </div>
                    <span className={`status ${isActive ? "status-accepted" : "status-cancelled"}`}>
                      {isActive ? "ACTIVE" : "INACTIVE"}
                    </span>
                  </div>
                  <div className="actions">
                    <button className="btn btn-small" onClick={() => setOutletId(id)}>Use Outlet</button>
                    {isActive ? (
                      <button className="btn btn-small" onClick={() => setOutletStatus(id, false)}>Deactivate</button>
                    ) : (
                      <button className="btn btn-small" onClick={() => setOutletStatus(id, true)}>Activate</button>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </section>

      <section className="card">
        <h2>Step 2 · Tables by Outlet</h2>
        <p className="subtext">Daftar tabel mengikuti Outlet Scope yang dipilih di atas.</p>
        <div className="filter-grid" style={{ marginTop: "10px" }}>
          <div className="field-block">
            <label className="label">Jumlah Table</label>
            <input
              className="input"
              type="number"
              min="1"
              max="500"
              value={tableSeedCount}
              onChange={(event) => setTableSeedCount(event.target.value)}
              placeholder="10"
            />
          </div>
        </div>
        <div className="actions">
          <button
            className="btn btn-primary"
            onClick={() => seedTablesForOutlet(outletId)}
            disabled={tableSeedBusy}
          >
            {tableSeedBusy ? "Generating..." : "Generate Tables for Outlet"}
          </button>
        </div>
        <div className="list" style={{ marginTop: "12px" }}>
          {tablesLoading ? (
            <div className="subtext">Loading tables...</div>
          ) : tables.length === 0 ? (
            <div className="subtext">No tables found for outlet: {(outletId || "").trim() || "default"}.</div>
          ) : (
            tables.map((table) => (
              <div key={table.uuid} className="table-row">
                <div className="list-row">
                  <div>
                    <strong>{table.name}</strong>
                    <div className="subtext">{table.uuid}</div>
                    <div className="subtext">Outlet: {table.outletId || table.outlet_id || outletId}</div>
                  </div>
                  <a
                    className="btn btn-small"
                    href={`/t/${table.uuid}?outlet=${encodeURIComponent(table.outletId || table.outlet_id || outletId)}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Open QR
                  </a>
                </div>
              </div>
            ))
          )}
        </div>
        {tablesMessage ? <p className="message">{tablesMessage}</p> : null}
      </section>

      <section className="card">
        <h2>Step 3 · Pairing Code Generation</h2>
        <p className="subtext">Pairing code dipakai untuk setup awal owner di mobile. Mobile hanya redeem, tidak generate.</p>
        <div className="filter-grid">
          <div className="field-block">
            <label className="label">Target Outlet</label>
            <input
              className="input"
              value={outletId}
              onChange={(event) => setOutletId(event.target.value || "default")}
              placeholder="default"
            />
          </div>
        </div>
        <p className="subtext">Role otomatis OWNER. TTL otomatis lifetime (sampai code ditebus sekali).</p>
        <div className="actions">
          <button className="btn btn-primary" onClick={createPairingCode} disabled={busy}>Generate Pairing Code</button>
        </div>
        {pairingCode ? (
          <div className="pairing-box">
            <div className="subtext">Code</div>
            <div className="pairing-code">{pairingCode}</div>
            <div className="subtext">
              Expires: {pairingExpiry === NEVER_PAIRING_EXPIRY ? "Never (until redeemed)" : (pairingExpiry || "-")}
            </div>
            <div className="actions">
              <button className="btn btn-small" onClick={copyPairingCode}>Copy</button>
            </div>
          </div>
        ) : null}
        {message ? <p className="message">{message}</p> : null}
      </section>
    </main>
  );
}

function App() {
  if (isAdminPage) return <AdminPage />;
  if (isDashboardPage) return <DashboardPage />;
  if (isTablePage) return <TableOrderingPage />;
  return <HomePage />;
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
