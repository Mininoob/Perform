import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import { site } from "@/lib/site";
import "./globals.css";

const literata = localFont({
  src: [
    { path: "./fonts/Literata.woff", style: "normal", weight: "200 900" },
    { path: "./fonts/Literata-Italic.woff", style: "italic", weight: "200 900" },
  ],
  variable: "--font-literata",
  display: "swap",
  preload: true,
});

export const metadata: Metadata = {
  metadataBase: new URL(site.url),
  title: {
    default: "Preform: your books, held like books",
    template: "%s · Preform",
  },
  description: site.description,
  applicationName: "Preform",
  keywords: ["ebook reader", "foldable", "Galaxy Fold", "EPUB", "PDF", "Android", "open source", "ebook library"],
  openGraph: {
    type: "website",
    title: "Preform: your books, held like books",
    description: site.description,
    url: site.url,
    siteName: "Preform",
  },
  twitter: {
    card: "summary_large_image",
    title: "Preform: your books, held like books",
    description: site.description,
  },
};

export const viewport: Viewport = {
  themeColor: "#7a1f2b",
  colorScheme: "dark light",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={literata.variable}>
      <body id="top">{children}</body>
    </html>
  );
}
