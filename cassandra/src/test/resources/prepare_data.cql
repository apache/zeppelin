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

CREATE TABLE IF NOT EXISTS zeppelin.ts(
 key text PRIMARY KEY,
 val text
);

TRUNCATE zeppelin.ts;

CREATE TABLE IF NOT EXISTS zeppelin.prepared(
 key text PRIMARY KEY,
 val text
);

TRUNCATE zeppelin.prepared;

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

TRUNCATE zeppelin.users;
