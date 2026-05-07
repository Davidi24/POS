alter table tables
    add column merged_into_table_id uuid;

alter table tables
    add constraint fk_tables_merged_into
        foreign key (merged_into_table_id) references tables(id);

alter table tables
    add constraint chk_tables_merged_into_not_self
        check (merged_into_table_id IS NULL OR merged_into_table_id <> id);

create index idx_tables_merged_into_table_id on tables (merged_into_table_id);
