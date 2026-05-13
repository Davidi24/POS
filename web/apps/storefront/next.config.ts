import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: [
    "@pos/ui",
    "@pos/auth",
    "@pos/api-client",
    "@pos/types",
    "@pos/utils",
  ],
};

export default nextConfig;
