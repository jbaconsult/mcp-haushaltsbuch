import type { NextConfig } from "next";

const config: NextConfig = {
  // Erzeugt einen eigenständigen Server samt der tatsächlich benötigten
  // node_modules. Das Container-Image kommt damit ohne vollständige
  // Abhängigkeitsinstallation aus - siehe Dockerfile.
  output: "standalone",

  reactStrictMode: true,

  // Verrät die Next.js-Version nicht im Antwort-Header.
  poweredByHeader: false,
};

export default config;
