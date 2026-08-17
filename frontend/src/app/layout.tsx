import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: "Haushaltsbuch",
  description: "Übersicht über Konten, Töpfe und Verbindlichkeiten",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="de">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
