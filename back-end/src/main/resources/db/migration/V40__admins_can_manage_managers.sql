-- Owner/Co-Owner switch that lets Admins create, edit and delete Managers. Off for every existing restaurant.
ALTER TABLE settings
    ADD COLUMN admins_can_manage_managers boolean NOT NULL DEFAULT false;
