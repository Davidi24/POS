alter table menus
    add column all_filter_position integer
    check (all_filter_position >= 0);
