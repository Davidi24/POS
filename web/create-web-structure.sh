#!/bin/bash

# ─────────────────────────────────────────────
# POS Web Monorepo — Scaffold Script
# Run this from inside your /web folder:
#   chmod +x create-web-structure.sh
#   ./create-web-structure.sh
# ─────────────────────────────────────────────

set -e

echo "🚀 Scaffolding POS web monorepo..."

# ─────────────────────────────────────────────
# HELPER
# ─────────────────────────────────────────────
mkf() { mkdir -p "$(dirname "$1")" && touch "$1"; }

# ─────────────────────────────────────────────
# ROOT FILES
# ─────────────────────────────────────────────
cat > package.json << 'EOF'
{
  "name": "pos-web",
  "private": true,
  "scripts": {
    "dev": "turbo run dev",
    "build": "turbo run build",
    "lint": "turbo run lint",
    "typecheck": "turbo run typecheck"
  },
  "devDependencies": {
    "turbo": "latest"
  },
  "packageManager": "pnpm@9.0.0"
}
EOF

cat > pnpm-workspace.yaml << 'EOF'
packages:
  - "apps/*"
  - "packages/*"
  - "tooling/*"
EOF

cat > turbo.json << 'EOF'
{
  "$schema": "https://turbo.build/schema.json",
  "tasks": {
    "build": {
      "dependsOn": ["^build"],
      "outputs": [".next/**", "dist/**"]
    },
    "dev": {
      "cache": false,
      "persistent": true
    },
    "lint": {},
    "typecheck": {
      "dependsOn": ["^build"]
    }
  }
}
EOF

cat > tsconfig.json << 'EOF'
{
  "compilerOptions": {
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  }
}
EOF

# ─────────────────────────────────────────────
# TOOLING
# ─────────────────────────────────────────────
mkdir -p tooling/eslint-config tooling/tsconfig

cat > tooling/eslint-config/package.json << 'EOF'
{
  "name": "@pos/eslint-config",
  "version": "0.0.1",
  "private": true,
  "files": ["base.js", "next.js"]
}
EOF
touch tooling/eslint-config/base.js
touch tooling/eslint-config/next.js

cat > tooling/tsconfig/package.json << 'EOF'
{
  "name": "@pos/tsconfig",
  "version": "0.0.1",
  "private": true,
  "files": ["base.json", "next.json"]
}
EOF

cat > tooling/tsconfig/base.json << 'EOF'
{
  "$schema": "https://json.schemastore.org/tsconfig",
  "compilerOptions": {
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "target": "ES2017"
  }
}
EOF

cat > tooling/tsconfig/next.json << 'EOF'
{
  "$schema": "https://json.schemastore.org/tsconfig",
  "extends": "./base.json",
  "compilerOptions": {
    "plugins": [{ "name": "next" }],
    "lib": ["dom", "dom.iterable", "esnext"],
    "module": "esnext",
    "jsx": "preserve",
    "allowJs": true,
    "incremental": true
  },
  "exclude": ["node_modules"]
}
EOF

# ─────────────────────────────────────────────
# PACKAGES
# ─────────────────────────────────────────────

# -- api-client --
mkdir -p packages/api-client/src/{auth,restaurant,menu,tables,reservations,settings,devices,customers,users,roles,orders,inventory,payments,storefront,generated}
touch packages/api-client/src/http.ts

cat > packages/api-client/package.json << 'EOF'
{
  "name": "@pos/api-client",
  "version": "0.0.1",
  "private": true,
  "main": "./src/index.ts",
  "scripts": {
    "typecheck": "tsc --noEmit"
  },
  "devDependencies": {
    "@pos/tsconfig": "workspace:*",
    "typescript": "latest"
  }
}
EOF

cat > packages/api-client/tsconfig.json << 'EOF'
{
  "extends": "@pos/tsconfig/base.json",
  "compilerOptions": {
    "outDir": "dist"
  },
  "include": ["src"]
}
EOF

touch packages/api-client/src/index.ts

# -- types --
mkdir -p packages/types/src/{auth,restaurant,menu,tables,reservations,settings,devices,customers,users,roles,common}

cat > packages/types/package.json << 'EOF'
{
  "name": "@pos/types",
  "version": "0.0.1",
  "private": true,
  "main": "./src/index.ts",
  "devDependencies": {
    "@pos/tsconfig": "workspace:*",
    "typescript": "latest"
  }
}
EOF

cat > packages/types/tsconfig.json << 'EOF'
{
  "extends": "@pos/tsconfig/base.json",
  "include": ["src"]
}
EOF

touch packages/types/src/index.ts

# -- ui --
mkdir -p packages/ui/src/components/{button,modal,table,form,badge,toast,skeleton}
mkdir -p packages/ui/src/theme/tokens

cat > packages/ui/package.json << 'EOF'
{
  "name": "@pos/ui",
  "version": "0.0.1",
  "private": true,
  "main": "./src/index.ts",
  "peerDependencies": {
    "react": "^18",
    "react-dom": "^18"
  },
  "devDependencies": {
    "@pos/tsconfig": "workspace:*",
    "typescript": "latest"
  }
}
EOF

cat > packages/ui/tsconfig.json << 'EOF'
{
  "extends": "@pos/tsconfig/next.json",
  "include": ["src"]
}
EOF

touch packages/ui/src/index.ts
touch packages/ui/src/theme/global.css

# -- auth --
mkdir -p packages/auth/src/{web-auth,device-auth,token-storage,session,permissions}

cat > packages/auth/package.json << 'EOF'
{
  "name": "@pos/auth",
  "version": "0.0.1",
  "private": true,
  "main": "./src/index.ts",
  "dependencies": {
    "@pos/api-client": "workspace:*",
    "@pos/types": "workspace:*"
  },
  "devDependencies": {
    "@pos/tsconfig": "workspace:*",
    "typescript": "latest"
  }
}
EOF

cat > packages/auth/tsconfig.json << 'EOF'
{
  "extends": "@pos/tsconfig/next.json",
  "include": ["src"]
}
EOF

touch packages/auth/src/index.ts

# -- utils --
mkdir -p packages/utils/src/{currency,date,validation,pagination}

cat > packages/utils/package.json << 'EOF'
{
  "name": "@pos/utils",
  "version": "0.0.1",
  "private": true,
  "main": "./src/index.ts",
  "devDependencies": {
    "@pos/tsconfig": "workspace:*",
    "typescript": "latest"
  }
}
EOF

cat > packages/utils/tsconfig.json << 'EOF'
{
  "extends": "@pos/tsconfig/base.json",
  "include": ["src"]
}
EOF

touch packages/utils/src/index.ts

# ─────────────────────────────────────────────
# APP FACTORY
# Creates a Next.js app with all its folders
# Usage: create_app <app-name> <description>
# ─────────────────────────────────────────────
create_app() {
  local APP=$1
  local DESC=$2

  echo "  → Creating app: $APP"

  # Create the app directory first
  mkdir -p "apps/$APP"

  # package.json
  cat > "apps/$APP/package.json" << EOF
{
  "name": "@pos/$APP",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "dev": "next dev --turbo",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@pos/api-client": "workspace:*",
    "@pos/auth": "workspace:*",
    "@pos/types": "workspace:*",
    "@pos/ui": "workspace:*",
    "@pos/utils": "workspace:*",
    "next": "latest",
    "react": "^18",
    "react-dom": "^18"
  },
  "devDependencies": {
    "@pos/eslint-config": "workspace:*",
    "@pos/tsconfig": "workspace:*",
    "typescript": "latest"
  }
}
EOF

  # tsconfig.json
  cat > "apps/$APP/tsconfig.json" << EOF
{
  "extends": "@pos/tsconfig/next.json",
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
EOF

  # next.config.ts
  cat > "apps/$APP/next.config.ts" << EOF
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
EOF

  # public folder (Next.js static assets)
  mkdir -p "apps/$APP/public"

  # src/app base files
  mkdir -p "apps/$APP/src/app"

  cat > "apps/$APP/src/app/layout.tsx" << EOF
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
EOF

  cat > "apps/$APP/src/app/page.tsx" << EOF
export default function Page() {
  return <div>${DESC}</div>;
}
EOF

  # layouts folder
  mkdir -p "apps/$APP/src/layouts"
}

# ─────────────────────────────────────────────
# CREATE APPS
# ─────────────────────────────────────────────
echo ""
echo "📦 Creating apps..."

create_app "admin"      "Admin Portal"
create_app "pos"        "POS Terminal"
create_app "kds"        "Kitchen Display System"
create_app "storefront" "Customer Storefront"
create_app "superadmin" "Super Admin Portal"

# ─────────────────────────────────────────────
# ADMIN — app routes + features
# ─────────────────────────────────────────────
echo "  → Scaffolding admin routes and features..."

# Route groups
for route in restaurant menu tables reservations settings devices customers users roles orders inventory payments reports; do
  mkf "apps/admin/src/app/(dashboard)/$route/page.tsx"
done
mkf "apps/admin/src/app/(dashboard)/layout.tsx"
mkf "apps/admin/src/app/(auth)/login/page.tsx"
mkf "apps/admin/src/app/(auth)/layout.tsx"

# Layouts
for layout in AdminShell BranchScopedLayout AuthLayout; do
  mkf "apps/admin/src/layouts/$layout/index.tsx"
done

# Features
mkdir -p apps/admin/src/features/auth
mkdir -p apps/admin/src/features/restaurant/{profile,branches,registration,tax}
mkdir -p apps/admin/src/features/menu/{menus,sections,items,variants,option-groups,option-items}
mkdir -p apps/admin/src/features/tables/{categories,layout,map}
mkdir -p apps/admin/src/features/reservations/{calendar,list,detail,availability,deposits}
mkdir -p apps/admin/src/features/settings/{general,business-hours,special-hours,order-rules,receipt,reservation-rules,templates}
mkdir -p apps/admin/src/features/devices/{pairing,assignments,printers}
mkdir -p apps/admin/src/features/customers/{list,detail}
mkdir -p apps/admin/src/features/users/{list,detail}
mkdir -p apps/admin/src/features/roles/{list,editor}

# ─────────────────────────────────────────────
# POS — app routes + features
# ─────────────────────────────────────────────
echo "  → Scaffolding pos routes and features..."

mkf "apps/pos/src/app/(auth)/login/page.tsx"
mkf "apps/pos/src/app/(auth)/layout.tsx"
for route in terminal tables orders; do
  mkf "apps/pos/src/app/(terminal)/$route/page.tsx"
done
mkf "apps/pos/src/app/(terminal)/layout.tsx"

for layout in POSShell TableMapShell AuthLayout; do
  mkf "apps/pos/src/layouts/$layout/index.tsx"
done

mkdir -p apps/pos/src/features/{auth,catalog,cart,checkout,tables,orders,receipts,customers}

# ─────────────────────────────────────────────
# KDS — app routes + features
# ─────────────────────────────────────────────
echo "  → Scaffolding kds routes and features..."

mkf "apps/kds/src/app/(auth)/login/page.tsx"
mkf "apps/kds/src/app/(auth)/layout.tsx"
for route in board stations; do
  mkf "apps/kds/src/app/(board)/$route/page.tsx"
done
mkf "apps/kds/src/app/(board)/layout.tsx"

for layout in KDSShell AuthLayout; do
  mkf "apps/kds/src/layouts/$layout/index.tsx"
done

mkdir -p apps/kds/src/features/{auth,tickets,stations}

# ─────────────────────────────────────────────
# STOREFRONT — app routes + features
# ─────────────────────────────────────────────
echo "  → Scaffolding storefront routes and features..."

mkf "apps/storefront/src/app/[restaurantSlug]/menu/page.tsx"
mkf "apps/storefront/src/app/[restaurantSlug]/reserve/page.tsx"
mkf "apps/storefront/src/app/[restaurantSlug]/layout.tsx"

mkf "apps/storefront/src/layouts/StorefrontShell/index.tsx"

mkdir -p apps/storefront/src/features/{menu,reservations}

# ─────────────────────────────────────────────
# SUPERADMIN — app routes + features
# ─────────────────────────────────────────────
echo "  → Scaffolding superadmin routes and features..."

mkf "apps/superadmin/src/app/(auth)/login/page.tsx"
mkf "apps/superadmin/src/app/(auth)/layout.tsx"
for route in restaurants users roles; do
  mkf "apps/superadmin/src/app/(dashboard)/$route/page.tsx"
done
mkf "apps/superadmin/src/app/(dashboard)/layout.tsx"

for layout in SuperAdminShell AuthLayout; do
  mkf "apps/superadmin/src/layouts/$layout/index.tsx"
done

mkdir -p apps/superadmin/src/features/auth
mkdir -p apps/superadmin/src/features/restaurants/{list,detail,registration-review}
mkdir -p apps/superadmin/src/features/users
mkdir -p apps/superadmin/src/features/roles

# ─────────────────────────────────────────────
# DONE
# ─────────────────────────────────────────────
echo ""
echo "✅ Done! Structure created."
echo ""
echo "Next steps:"
echo "  1. cd into web/"
echo "  2. pnpm install"
echo "  3. pnpm dev"
