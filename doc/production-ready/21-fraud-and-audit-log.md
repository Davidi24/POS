# 21. Fraud detection & Audit log

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Fraud Detection (Overview, Alerts, Activity, Rules), Settings → Fraud checks, Admin Hub → Audit log.
- Server: `/fraud/*` (FRAUD_READ, FRAUD_REVIEW), `audit_logs`.

## How it is now
- Fraud rules include closing as unpaid, many voids, voids after the kitchen; thresholds are settings.
- Inventory item changes are written into `audit_logs`.

## Rule for every step before this one
- Each service writes an audit entry for every change it makes (who, what, before → after).

## This step
- Check nothing is missing; finish the Audit log page (filters, search, details, download).

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
