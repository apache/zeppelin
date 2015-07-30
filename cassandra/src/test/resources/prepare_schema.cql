CREATE KEYSPACE IF NOT EXISTS zeppelin
    WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}
    AND DURABLE_WRITES=false;

CREATE TABLE IF NOT EXISTS zeppelin.artists(
    name text PRIMARY KEY,
    born text,
    died text,
    country text,
    gender text,
    type text,
    styles list<text>
);

CREATE TABLE IF NOT EXISTS zeppelin.ts(
 key text PRIMARY KEY,
 val text
);

CREATE TABLE IF NOT EXISTS zeppelin.prepared(
 key text PRIMARY KEY,
 val text
);

CREATE TYPE IF NOT EXISTS zeppelin.address(
 street_number int,
 street_name text,
 zip_code int,
 country text,
 extra_info list<text>,
 phone_numbers map<text,bigint>

);


CREATE TABLE IF NOT EXISTS zeppelin.users(
 login text,
 firstname text,
 lastname text,
 age int,
 deceased boolean,
 last_update timestamp,
 addresses frozen<address>,
 location frozen<tuple<text,bigint,text>>,
 PRIMARY KEY(login)
);

CREATE KEYSPACE IF NOT EXISTS samples
    WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}
    AND DURABLE_WRITES=false;

CREATE KEYSPACE IF NOT EXISTS live_data
    WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}
    AND DURABLE_WRITES=false;

CREATE TYPE IF NOT EXISTS live_data.address (
    number int,
    street text,
    zip int,
    city text,
    country text
);

CREATE TABLE IF NOT EXISTS live_data.complex_table(
    pk1 uuid,
    pk2 int,
    my_static1 text static,
    my_static2 text static,
    clustering1 timestamp,
    clustering2 int,
    clustering3 text,
    indexed1 text,
    indexed2 int,
    simple double,
    my_list list<text>,
    my_udt_list frozen<list<address>>,
    my_udt frozen<address>,
    my_map map<int,text>,
    key_indexed_map map<int,text>,
    entries_indexed_map map<int,text>,
    PRIMARY KEY((pk1,pk2),clustering1, clustering2, clustering3)
) WITH CLUSTERING ORDER BY (clustering1 DESC, clustering2 ASC, clustering3 DESC);

CREATE INDEX IF NOT EXISTS pk2idx ON live_data.complex_table(pk2);
CREATE INDEX IF NOT EXISTS clustering2idx ON live_data.complex_table(clustering2);
CREATE INDEX IF NOT EXISTS idx1 ON live_data.complex_table(indexed1);
CREATE INDEX IF NOT EXISTS idx2 ON live_data.complex_table(indexed2);
CREATE INDEX IF NOT EXISTS keys_map_idx ON live_data.complex_table(KEYS(key_indexed_map));




CREATE TABLE IF NOT EXISTS live_data.sensor_data (
    sensor_id uuid,
    month int,
    provider text static,
    model_number text static,
    characteristics map<text, text> static,
    date timestamp,
    value double,
    PRIMARY KEY((sensor_id, month), date)
) WITH CLUSTERING ORDER BY (date DESC);


CREATE TYPE IF NOT EXISTS live_data.geolocation (
    latitude double,
    longitude double
);

CREATE TABLE IF NOT EXISTS live_data.stations (
    station_id uuid,
    sensors frozen<map<uuid,geolocation>>,
    PRIMARY KEY (station_id)
);