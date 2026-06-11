/**
 * Gestione lato cliente
 * Tutto dentro una IIFE
 *
 * Componenti (show() recupera i dati, update() costruisce il markup):
 *  - ListaProdotti       -> HOME: prodotti di primo livello (paginati 10/volta)
 *  - SceltaSku           -> configuratore: albero + tendine SKU + SALVA
 *  - DettaglioConfig     -> riepilogo configurazione salvata
 *  - LeMieConfigurazioni -> elenco configurazioni (elimina / clona / modifica)
 *  - PageOrchestrator    -> coordina i componenti e la navigazione
 *
 */
(function() {

    var orchestrator;
    var utenteLoggato;

    // prodotti composti senza padre
    function ListaProdotti(sezioneId, alertId) {
        this.sezione = document.getElementById(sezioneId);
        this.alert = document.getElementById(alertId);
        this.contenitore = document.getElementById("id_listaProdotti");
        this.paginazione = document.getElementById("id_paginazione");
        this.offset = 0;

        var self = this;

        this.show = function() {
            this.sezione.style.display = "block";
            this.offset = 0;
            this.caricaPagina();
        };

        this.hide = function() {
            this.sezione.style.display = "none";
        };

        // recupera la pagina corrente dal server con offset
        this.caricaPagina = function() {
            makeCall("GET", "GetProdottiPrimoLivello?offset=" + self.offset, null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    self.update(JSON.parse(req.responseText));
                } else {
                    self.alert.textContent = "Errore nel caricamento dei prodotti.";
                    self.alert.style.color = "red";
                }
            });
        };

        // costruisce le card dei prodotti e gestisce i bottoni 
        this.update = function(data) {
			// se null diventa []
            var prodotti = data.prodotti || [];
            self.contenitore.innerHTML = "";

            if (prodotti.length === 0) {
                self.contenitore.innerHTML = '<p class="empty-message">Nessun prodotto disponibile.</p>';
                self.paginazione.style.display = "none";
                return;
            }

            prodotti.forEach(function(p) {
                var card = document.createElement("div");
                card.className = "product-card";

                var titolo = document.createElement("h3");
                titolo.textContent = p.nome;
                card.appendChild(titolo);

                var desc = document.createElement("p");
                desc.className = "product-desc";
                desc.textContent = p.descrizione || "";
                card.appendChild(desc);

                var prezzo = document.createElement("p");
                prezzo.textContent = "Fascia di prezzo: " + p.prezzoMin + "€ - " + p.prezzoMax + "€";
                card.appendChild(prezzo);

                // il click sulla card apre la scelta sku
                card.addEventListener('click', function() {
                    orchestrator.apriScelta(p.codice, p.nome);
                });

                self.contenitore.appendChild(card);
            });

            // i bottoni compaiono solo se esiste un insieme precedente/successivo
            document.getElementById("id_btnPrec").style.display = data.haPrecedenti ? "" : "none";
            document.getElementById("id_btnSucc").style.display = data.haSuccessivi ? "" : "none";
            document.getElementById("id_paginaInfo").textContent =
                "Prodotti " + (self.offset + 1) + "–" + (self.offset + prodotti.length);
            self.paginazione.style.display = (data.haPrecedenti || data.haSuccessivi) ? "flex" : "none";
        };

        // bottoni di paginazione
        document.getElementById("id_btnPrec").addEventListener('click', function() {
            self.offset -= 10;
            if (self.offset < 0) self.offset = 0;
            self.caricaPagina();
        });
        document.getElementById("id_btnSucc").addEventListener('click', function() {
            self.offset += 10;
            self.caricaPagina();
        });
    }

    // pagina scelta sku
    function SceltaSku(sezioneId, alertId) {
        this.sezione = document.getElementById(sezioneId);
        this.alert = document.getElementById(alertId);
        this.titolo = document.getElementById("id_titoloProdotto");
        this.albero = document.getElementById("id_alberoConfig");
        this.inputNome = document.getElementById("id_nomeConfig");

		// albero del prodotto composto
        this.root = null;               
		// prodotto semplice - sku selezionata  
        this.selezioni = {};
		// se è null è nuova, altrimenti c'era già ed è stata modificata
        this.idConfigInModifica = null;   

        var self = this;

        // fa vedere l'albero e l'eventuali scelte che ersno state prese
        this.show = function(codiceRadice, nome, opts) {
            self.sezione.style.display = "block";
            self.titolo.textContent = nome || "";
            self.root = null;
            self.selezioni = {};
            self.idConfigInModifica = (opts && opts.idConfig) ? opts.idConfig : null;
            self.inputNome.value = "";
            self.albero.textContent = "Caricamento...";

            // carica l'albero del prodotto
            makeCall("GET", "GetAlberoProdotto?codice=" + encodeURIComponent(codiceRadice) + "&tipo=COMPOSTO", null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status !== 200) {
                    self.alert.textContent = "Errore nel caricamento del prodotto.";
                    self.alert.style.color = "red";
                    return;
                }
                self.root = JSON.parse(req.responseText);
                if (self.idConfigInModifica) {
                    self.caricaScelte(self.idConfigInModifica); // carica e poi disegna
                } else {
                    self.update();
                }
            });
        };

        this.hide = function() {
            self.sezione.style.display = "none";
        };

        // se è modifica carica le sku già scelte
        this.caricaScelte = function(idConfig) {
            makeCall("GET", "GetScelteConfigurazione?id=" + idConfig, null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    var dati = JSON.parse(req.responseText); // { nome, scelte: { codProd: codSku } }
                    self.selezioni = {};
                    var scelte = dati.scelte || {};
                    Object.keys(scelte).forEach(function(k) { self.selezioni[parseInt(k)] = scelte[k]; });
                    if (dati.nome) self.inputNome.value = dati.nome;
                }
                self.update();
            });
        };

        // disegna l'albero con click per espandere e tendine
        this.update = function() {
            self.albero.innerHTML = "";
            if (!self.root) return;
            self.renderNodo(self.root, self.albero, 1);
        };

        this.renderNodo = function(nodo, container, livello) {
			// se è composto fa div cliccabile con figli e rerunna renderNodo coi figli
            if (nodo.tipo === "COMPOSTO") {
                var wrap = document.createElement("div");
                if (livello === 1) {
                    // mostra figli subito
                    (nodo.figli || []).forEach(function(f) { self.renderNodo(f, wrap, livello + 1); });
                } else {
                    // da cliccare
                    var header = document.createElement("div");
                    header.className = "clickable-summary";
                    header.textContent = nodo.nome;
                    wrap.appendChild(header);

                    var figliDiv = document.createElement("div");
                    figliDiv.className = "tree-list-composto";
                    figliDiv.style.display = "none";
					// si apre e chiude a click
                    header.addEventListener('click', function() {
                        figliDiv.style.display = (figliDiv.style.display === "none") ? "block" : "none";
                    });
                    (nodo.figli || []).forEach(function(f) { self.renderNodo(f, figliDiv, livello + 1); });
                    wrap.appendChild(figliDiv);
                }
                container.appendChild(wrap);

			// se semplice fa venire le sku e la scelta
            } else if (nodo.tipo === "SEMPLICE") {
                var row = document.createElement("div");
                row.className = "sku-selection-row";

                var label = document.createElement("span");
                label.textContent = nodo.nome + ":";
                row.appendChild(label);

                var select = document.createElement("select");
                select.className = "styled-select";
                var opt0 = document.createElement("option");
                opt0.value = "";
                opt0.textContent = "-- scegli SKU --";
                select.appendChild(opt0);

                (nodo.skus || []).forEach(function(sku) {
                    var o = document.createElement("option");
                    o.value = sku.codice;
                    o.textContent = sku.nome + " (" + sku.prezzo + "€)";
                    if (self.selezioni[nodo.codice] === sku.codice) o.selected = true;
                    select.appendChild(o);
                });

				// salva la selezione
                select.addEventListener('change', function() {
                    if (select.value === "") delete self.selezioni[nodo.codice];
                    else self.selezioni[nodo.codice] = parseInt(select.value);
                });

                row.appendChild(select);
                container.appendChild(row);
            }
        };

        // prende i nomi dei prodotti semplici a cui manca la sku scelta
        this.sempliciMancanti = function(nodo, mancanti) {
            if (nodo.tipo === "SEMPLICE") {
                if (!self.selezioni[nodo.codice]) mancanti.push(nodo.nome);
            } else if (nodo.tipo === "COMPOSTO") {
                (nodo.figli || []).forEach(function(f) { self.sempliciMancanti(f, mancanti); });
            }
            return mancanti;
        };

        this.salva = function() {
            if (!self.root) return;
            var nome = self.inputNome.value.trim();
            if (nome === "") {
                self.alert.textContent = "Inserisci un nome per la configurazione.";
                self.alert.style.color = "red";
                return;
            }
            // controllo lato client per inserimento scelte per sku
            var mancanti = self.sempliciMancanti(self.root, []);
            if (mancanti.length > 0) {
                self.alert.textContent = "Scegli una SKU per: " + mancanti.join(", ");
                self.alert.style.color = "red";
                return;
            }

            var fd = new FormData();
            fd.append("nomeConfigurazione", nome);
            fd.append("codiceRadice", self.root.codice);
            if (self.idConfigInModifica) fd.append("idConfigInModifica", self.idConfigInModifica);
            Object.keys(self.selezioni).forEach(function(codProd) {
                fd.append("sku_" + codProd, self.selezioni[codProd]);
            });

            makeCall("POST", "SalvaConfigurazione", fd, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    var res = JSON.parse(req.responseText);
					// se passa va ad apri dettaglio
                    orchestrator.apriDettaglio(res.idConfig);
                } else {
                    self.alert.textContent = "Errore: " + req.responseText;
                    self.alert.style.color = "red";
                }
            });
        };

        document.getElementById("id_btnSalvaConfig").addEventListener('click', function() {
            self.salva();
        });
    }

    // dettaglio configurazione
    function DettaglioConfig(sezioneId, alertId) {
        this.sezione = document.getElementById(sezioneId);
        this.alert = document.getElementById(alertId);
        this.contenitore = document.getElementById("id_dettaglioConfig");

        var self = this;

        this.show = function(idConfig) {
            self.sezione.style.display = "block";
            self.contenitore.textContent = "Caricamento...";
            makeCall("GET", "GetDettaglioConfigurazione?id=" + idConfig, null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
					// mostra il dettaglio
                    self.update(JSON.parse(req.responseText));
                } else {
                    self.contenitore.textContent = "";
                    self.alert.textContent = "Errore nel caricamento del dettaglio.";
                    self.alert.style.color = "red";
                }
            });
        };

        this.hide = function() {
            self.sezione.style.display = "none";
        };

        this.update = function(data) {
            self.contenitore.innerHTML = "";

            var intestazione = document.createElement("p");
            intestazione.innerHTML = "<strong>" + data.nome + "</strong> — basata su " + data.prodottoRadice +
                                     " (creata il " + data.dataCreazione + ")";
            self.contenitore.appendChild(intestazione);

            var ul = document.createElement("ul");
            (data.dettagli || []).forEach(function(d) {
                var li = document.createElement("li");
                li.textContent = d.prodSemplice + " → " + d.skuNome + " (" + d.skuPrezzo + "€)";
                ul.appendChild(li);
            });
            self.contenitore.appendChild(ul);

            var totale = document.createElement("p");
            totale.innerHTML = "<strong>Prezzo totale: " + data.prezzoTotale + "€</strong>";
            self.contenitore.appendChild(totale);
        };
    }

    // le mie configurazioni
    function LeMieConfigurazioni(sezioneId, alertId) {
        this.sezione = document.getElementById(sezioneId);
        this.alert = document.getElementById(alertId);
        this.contenitore = document.getElementById("id_listaConfig");

        var self = this;

        this.show = function() {
            self.sezione.style.display = "block";
            self.contenitore.textContent = "Caricamento...";
            makeCall("GET", "GetConfigurazioni", null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    self.update(JSON.parse(req.responseText));
                } else {
                    self.contenitore.textContent = "";
                    self.alert.textContent = "Errore nel caricamento delle configurazioni.";
                    self.alert.style.color = "red";
                }
            });
        };

        this.hide = function() {
            self.sezione.style.display = "none";
        };

        this.update = function(lista) {
            self.contenitore.innerHTML = "";

            if (!lista || lista.length === 0) {
                self.contenitore.innerHTML = '<p class="empty-message">Non hai ancora salvato configurazioni.</p>';
                return;
            }

            var table = document.createElement("table");
            table.className = "result-table";
            table.innerHTML =
                "<thead><tr><th>Nome</th><th>Prodotto</th><th>Creata</th>" +
                "<th>Ultima modifica</th><th>Totale</th><th>Azioni</th></tr></thead>";
            var tbody = document.createElement("tbody");

            lista.forEach(function(c) {
                var tr = document.createElement("tr");

                [c.nome, c.nomeRadice, c.dataCreazione,
                 (c.dataUltimaModifica || "-"), (c.prezzoTotale + "€")].forEach(function(val) {
                    var td = document.createElement("td");
                    td.textContent = val;
                    tr.appendChild(td);
                });

				// azioni sono modifica, clona, elimina
                var tdAzioni = document.createElement("td");

                var btnMod = document.createElement("button");
                btnMod.textContent = "Modifica";
				// va in scelta sku e inserisce le scelte già fatte in configurazione
                btnMod.addEventListener('click', function() {
                    orchestrator.apriScelta(c.codiceRadice, c.nomeRadice, { idConfig: c.id });
                });
                tdAzioni.appendChild(btnMod);

                var btnClona = document.createElement("button");
                btnClona.textContent = "Clona";
                btnClona.addEventListener('click', function() {
                    var fd = new FormData();
                    fd.append("idConfig", c.id);
                    makeCall("POST", "ClonaConfigurazione", fd, function(req) {
                        if (req.readyState !== XMLHttpRequest.DONE) return;
                        if (req.status === 200) self.show();
                        else { self.alert.textContent = "Errore: " + req.responseText; self.alert.style.color = "red"; }
                    });
                });
                tdAzioni.appendChild(btnClona);

                var btnCanc = document.createElement("button");
                btnCanc.textContent = "Cancella";
                btnCanc.className = "btn-elimina";
                btnCanc.addEventListener('click', function() {
                    if (!confirm("Eliminare la configurazione \"" + c.nome + "\"?")) return;
                    var fd = new FormData();
                    fd.append("idConfig", c.id);
                    makeCall("POST", "EliminaConfigurazione", fd, function(req) {
                        if (req.readyState !== XMLHttpRequest.DONE) return;
                        if (req.status === 200) self.show();
                        else { self.alert.textContent = "Errore: " + req.responseText; self.alert.style.color = "red"; }
                    });
                });
                tdAzioni.appendChild(btnCanc);

                tr.appendChild(tdAzioni);
                tbody.appendChild(tr);
            });

            table.appendChild(tbody);
            self.contenitore.appendChild(table);
        };
    }

    // orchestrator
    function PageOrchestrator() {
        var alertGlobalId = "id_alertGlobal";

        this.listaProdotti = new ListaProdotti("sezioneHome", alertGlobalId);
        this.sceltaSku = new SceltaSku("sezioneScelta", alertGlobalId);
        this.dettaglio = new DettaglioConfig("sezioneDettaglio", alertGlobalId);
        this.leMie = new LeMieConfigurazioni("sezioneConfig", alertGlobalId);

        var self = this;

        this.start = function() {
            // controllo di sicurezza lato client
            var sessionUtente = sessionStorage.getItem('utente');
            if (!sessionUtente) { window.location.href = "index.html"; return; }
            utenteLoggato = JSON.parse(sessionUtente);
            if (utenteLoggato.ruolo !== "CLIENTE") { window.location.href = "index.html"; return; }

            // saluto con nome e cognome
            document.getElementById("id_welcomeMessage").textContent =
                "Ciao, " + utenteLoggato.nome + " " + utenteLoggato.cognome;

            // navigazione
            document.getElementById("id_btnShowCatalogo").addEventListener('click', function() { self.mostraHome(); });
            document.getElementById("id_btnShowConfig").addEventListener('click', function() { self.mostraConfigurazioni(); });

            document.getElementById("id_logoutBtn").addEventListener('click', function() {
                makeCall("GET", "Logout", null, function(req) {
                    if (req.readyState === XMLHttpRequest.DONE) {
						// rimuove utente dalla sessione
                        sessionStorage.removeItem('utente');
                        window.location.href = "index.html";
                    }
                });
            });

            // vista di default è dei prodotti
            this.mostraHome();
        };

        // metodi di navigazione, usati anche dai componenti
        this.mostraHome = function() {
            self.nascondiTutto();
            self.listaProdotti.show();
        };
        this.apriScelta = function(codiceRadice, nome, opts) {
            self.nascondiTutto();
            self.sceltaSku.show(codiceRadice, nome, opts);
        };
        this.apriDettaglio = function(idConfig) {
            self.nascondiTutto();
            self.dettaglio.show(idConfig);
        };
        this.mostraConfigurazioni = function() {
            self.nascondiTutto();
            self.leMie.show();
        };

        this.nascondiTutto = function() {
            self.listaProdotti.hide();
            self.sceltaSku.hide();
            self.dettaglio.hide();
            self.leMie.hide();
            document.getElementById("id_alertGlobal").textContent = "";
        };
    }

	// quando la pagina carica la prima volta fa load e starta l'orchestrator
    window.addEventListener("load", function() {
        orchestrator = new PageOrchestrator();
        orchestrator.start();
    });

})();
