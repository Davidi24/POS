alter table menus
    add column available_from time,
    add column available_until time,
    add column color varchar(20);
