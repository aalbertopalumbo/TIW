CREATE DATABASE IF NOT EXISTS TIW;
USE TIW;


CREATE TABLE IF NOT EXISTS Utente (
    username varchar(50) NOT NULL primary key,
    nome varchar(50) NOT NULL,
    cognome varchar(50) NOT NULL,
    password varchar(50) NOT NULL,
    ruolo ENUM('FORNITORE', 'CLIENTE') NOT NULL 
);

CREATE TABLE IF NOT EXISTS Prodotto (
	codice int NOT NULL primary key,
	nome varchar (50) NOT NULL 
);

CREATE TABLE IF NOT EXISTS ProdottoComposto (
	codice int NOT NULL primary key,
	descrizione varchar (100),
	p_min DECIMAL(10,2) NOT NULL CHECK (p_min >= 0),
	p_max DECIMAL(10,2) NOT NULL,
    CONSTRAINT chk_prezzi CHECK (p_max >= p_min),
	foreign key (codice) REFERENCES PRODOTTO (codice)
	ON DELETE CASCADE ON UPDATE CASCADE
);
    
CREATE TABLE IF NOT EXISTS ProdottoSemplice (
	codice int NOT NULL primary key,
	foreign key (codice) REFERENCES PRODOTTO (codice)
	ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Composizione (
	codice_padre int NOT NULL,
	codice_figlio int NOT NULL primary key,
	foreign key (codice_padre)  REFERENCES PRODOTTOCOMPOSTO (codice)
	ON DELETE CASCADE ON UPDATE CASCADE,
	foreign key (codice_figlio) REFERENCES PRODOTTO (codice)
	ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Sku (
	codice int NOT NULL primary key,
	nome varchar (50) NOT NULL,
	foto varchar (255),
	descrizione_tecnica varchar (100),
	prezzo int NOT NULL CHECK (prezzo >= 0)
);

CREATE TABLE IF NOT EXISTS Realizzazione (
	cod_prod_s int NOT NULL,
	cod_sku int NOT NULL,
	primary key (cod_prod_s, cod_sku),
	foreign key (cod_prod_s) REFERENCES ProdottoSemplice (codice)
	ON UPDATE CASCADE ON DELETE CASCADE,
	foreign key (cod_sku) REFERENCES Sku (codice)
	ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Configurazione (
	id int AUTO_INCREMENT NOT NULL primary key,
	nome varchar (50) NOT NULL,
	data_creazione DATE NOT NULL,
	data_ultima_modifica DATE,
	prezzo_totale DECIMAL(10,2) NOT NULL CHECK (prezzo_totale >= 0),
	username_cliente varchar (50) NOT NULL,
	cod_prodotto_radice int NOT NULL,
	foreign key (username_cliente) REFERENCES Utente (username)
	ON UPDATE CASCADE ON DELETE CASCADE,
	foreign key (cod_prodotto_radice) REFERENCES ProdottoComposto (codice)
	ON UPDATE CASCADE ON DELETE CASCADE
);
