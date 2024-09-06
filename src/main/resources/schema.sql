CREATE TABLE meas (
    id SERIAL NOT NULL PRIMARY KEY,
    time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    label VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    value DOUBLE PRECISION
);

CREATE INDEX meas_time_idx ON meas (time);

CREATE INDEX meas_label_idx ON meas (label);

CREATE INDEX meas_time_label_idx ON meas (time, label);

CREATE UNIQUE INDEX meas_time_label_name_idx ON meas (time, label, name);