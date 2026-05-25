import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import MenuPage from "./MenuPage";
import AboutPage from "./AboutPage";
import ReservationPage from "./ReservationPage";
import ContactPage from "./ContactPage";
import "./styles.css";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
