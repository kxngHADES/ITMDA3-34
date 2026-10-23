import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Static export: `next build` writes the site to ./out for Cloudflare Pages
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
};

export default nextConfig;
