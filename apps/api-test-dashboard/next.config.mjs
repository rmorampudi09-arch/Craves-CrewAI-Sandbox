/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: true,
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: "https://api.craves.in/api/v1/:path*"
      }
    ];
  }
};

export default nextConfig;
