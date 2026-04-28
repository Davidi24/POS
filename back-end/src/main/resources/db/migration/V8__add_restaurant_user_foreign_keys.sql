alter table if exists restaurants
    add constraint fk_restaurants_owner_user
    foreign key (owner_id) references users(id);

alter table if exists restaurants
    add constraint fk_restaurants_created_by_user
    foreign key (created_by) references users(id);

alter table if exists restaurants
    add constraint fk_restaurants_updated_by_user
    foreign key (updated_by) references users(id);
