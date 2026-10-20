/** @type {import('next').NextConfig} */
const nextConfig = {
  // The app is a fully client-side single page app, so it can be exported as
  // plain static files and hosted on Cloudflare Pages (build output folder: out).
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
};

export default nextConfig;
