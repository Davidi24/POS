-- Admin Hub → Settings → Inventory: stock worth at least this much (at cost, in the restaurant's currency) counts as
-- valuable on Inventory → Overview's "Most valuable stock" (agreed with the user, 2026-10-30: 100 by default).
alter table settings add column valuable_stock_from numeric(12, 2) not null default 100;
alter table settings add constraint ck_settings_valuable_stock_from check (valuable_stock_from >= 0 and valuable_stock_from <= 1000000);
