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

TRUNCATE zeppelin.ts;

TRUNCATE zeppelin.prepared;


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

TRUNCATE zeppelin.users;

-- for formatting test
create type if not exists zeppelin.tudt (
  id int,
  t text,
  lst frozen<list<int>>
);

create table if not exists zeppelin.test_format (
  id int primary key,
  text text,
  date date,
  timestamp timestamp,
  time time,
  uuid uuid,
  tuuid timeuuid,
  m1 map<int, text>,
  m2 frozen<map<text,int>>,
  l1 list<int>,
  l2 frozen<list<int>>,
  s1 set<int>,
  s2 frozen<set<text>>,
  udt zeppelin.tudt,
  ludt1 list<frozen<zeppelin.tudt>>,
  ludt2 frozen<list<zeppelin.tudt>>,
  blob blob,
  bool boolean,
  dec decimal,
  double double,
  float float,
  bigint bigint,
  smallint smallint,
  tinyint tinyint,
  varint varint,
  ascii ascii,
  tuple tuple<int, text, float>,
  varchar varchar,
  nullval text
);

TRUNCATE zeppelin.test_format;

insert into zeppelin.test_format(id, text, date, timestamp, time, uuid, tuuid, m1, m2, l1, l2, s1, s2,
  udt, ludt1, ludt2, blob, bool, dec, double, float, bigint, smallint, tinyint, varint, ascii, tuple, varchar)
  values (1, 'text', '2019-01-29', '2020-06-16T23:59:59.123Z', '04:05:00.234', 123e4567-e89b-12d3-a456-426655440000,
  now(), {1:'m1', 2:'m2'}, {'m1':1, 'm2':2}, [1,2,3], [1,2,3], {1,2,3}, {'1','2','3'},
  {id: 1, t: 'text', lst: [1,2,3]}, [{id: 1, t: 'text', lst: [1,2,3]}, {id: 2, t: 'text2'}],
  [{id: 1, t: 'text', lst: [1,2,3]}, {id: 2, t: 'text2'}], bigintAsBlob(1024), true, 123562352352.0,
  10.0153423453425634653463466346543, 20.0303443, 123562352352, 10000, 10,
  124325345634643900999999, 'ascii', (1, 'text', 10.0), 'varchar русский'); 

-- other tests

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

-- insert data
TRUNCATE zeppelin.artists;

INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Bogdan Raczynski','1977-01-01','Poland',null,'Male',['Dance', 'Electro'],'Person');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Krishna Das','1947-05-31','USA',null,'Male',['Unknown'],'Person');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Sheryl Crow','1962-02-11','USA',null,'Female',['Classic', 'Rock', 'Country', 'Blues', 'Pop', 'Folk'],'Person');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Doof','1968-08-31','United Kingdom',null,null,['Unknown'],'Person');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('House of Large Sizes','1986-01-01','USA','2003',null,['Unknown'],'Group');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Fanfarlo','2006-01-01','United Kingdom',null,null,['Rock', 'Indie', 'Pop', 'Classic'],'Group');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Jeff Beck','1944-06-24','United Kingdom',null,'Male',['Rock', 'Pop', 'Classic'],'Person');
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('Los Paranoias',null,'Unknown',null,null,['Unknown'],null);
INSERT INTO zeppelin.artists(name,born,country,died,gender,styles,type) VALUES('…And You Will Know Us by the Trail of Dead','1994-01-01','USA',null,null,['Rock', 'Pop', 'Classic'],'Group');

