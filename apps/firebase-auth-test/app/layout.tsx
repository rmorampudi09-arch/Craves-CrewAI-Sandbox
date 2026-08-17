import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Craves Firebase Auth Test",
  description: "Developer-only Firebase Phone Auth to Craves Auth Service test page"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
