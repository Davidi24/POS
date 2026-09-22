alter table menus
    add column available_from_date date,
    add column available_until_date date;

alter table menus
    add constraint chk_menus_available_date_range
        check (
            (available_from_date is null and available_until_date is null)
            or (
                available_from_date is not null
                and available_until_date is not null
                and available_from_date <= available_until_date
            )
        );
