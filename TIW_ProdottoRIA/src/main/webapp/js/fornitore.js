/**
 * Gestione lato fornitore (single page).
 * Tutto dentro una IIFE per non sporcare lo scope globale.
 *
 * Componenti:
 *  - AlberoProdotto : editor riutilizzabile dell'albero di un prodotto (creazione E catalogo)
 *  - GestoreCreazione : le 3 form (SKU / semplice / composto) + costruzione albero composto
 *  - GestoreCatalogo : ricerca + modifica nel catalogo
 *  - PageOrchestrator : coordina i componenti
 *
 * Modello dati unico di un nodo (lo stesso che usano i servlet):
 *  COMPOSTO: { tipo:"COMPOSTO", codice, nome, descrizione, prezzoMin, prezzoMax, isNuovo, figli:[...] }
 *  SEMPLICE: { tipo:"SEMPLICE", codice, nome, isNuovo, skus:[ skuObj ] }
 *  skuObj  : { codice, nome, prezzo, descrizione, foto, isNuovo }
 */
(function() {

    var orchestrator;
    var utenteLoggato;

    // la modifica inline per sku (usata da crea sku e da catalogo) diversa da modifica nel prodotto composto perché qui on mouseleave fa direttamente modifica in db
    function campoSkuAutosave(codiceSku, campo, valore, numerico) {
        var span = document.createElement("span");
        span.className = "albero-campo";
        span.textContent = (valore !== undefined && valore !== null) ? valore : "";

        span.addEventListener("click", function() {
			// click su elemento già cliccato non fa nulla
            if (span.querySelector("input")) return; 
            var vecchio = span.textContent;
            span.textContent = "";

            var input = document.createElement("input");
            input.type = numerico ? "number" : "text";
            input.value = vecchio;
            span.appendChild(input);
            input.focus();

            input.addEventListener("mouseleave", function() {
                var nuovo = input.value.trim();
                if (nuovo === vecchio) { span.textContent = vecchio; return; }
                if (nuovo === "" || (numerico && (isNaN(Number(nuovo)) || Number(nuovo) < 0))) {
                    span.textContent = vecchio;
                    alert("Valore non valido. Modifica annullata.");
                    return;
                }

                var fd = new FormData();
                fd.append("codice", codiceSku);
                fd.append("campo", campo);
                fd.append("valore", nuovo);
                makeCall("POST", "UpdateSkuInline", fd, function(req) {
                    if (req.readyState === XMLHttpRequest.DONE) {
                        if (req.status === 200) {
                            span.textContent = nuovo;
                        } else {
                            span.textContent = vecchio;
                            alert("Errore di salvataggio: " + req.responseText);
                        }
                    }
                });
            });
        });
        return span;
    }


    // l'albero dei prodotti, usato sia in creazione che in catalogo
    function AlberoProdotto(displayEl, alertEl) {
        this.display = displayEl;
        this.alert = alertEl;
        this.root = null;
        this.nodiDaEliminare = [];       // [{tipo, codice}]
        this.relazioniDaEliminare = [];  // [{tipoRel, padre, figlio}]

        // undo/redo con lista singola e indice
        this.history = [];   // ogni cella è una fotografia dell'albero 
        this.indice = -1;    // posizione corrente nell'history

        var self = this;

        // si fa una copia per evitare di avere riferimenti diretti agli oggetti e di corrompere
        function clona(obj) {
            return (obj === null || obj === undefined) ? obj : JSON.parse(JSON.stringify(obj));
        }

        // barra undo/redo sopra all'albero
        this.toolbar = document.createElement("div");
        this.toolbar.className = "albero-toolbar";
        this.btnUndo = document.createElement("button");
        this.btnUndo.type = "button";
        this.btnUndo.textContent = "↶ Undo";
        this.btnUndo.addEventListener("click", function() { self.undo(); });
        this.btnRedo = document.createElement("button");
        this.btnRedo.type = "button";
        this.btnRedo.textContent = "↷ Redo";
        this.btnRedo.addEventListener("click", function() { self.redo(); });
        this.toolbar.appendChild(this.btnUndo);
        this.toolbar.appendChild(this.btnRedo);
        if (this.display && this.display.parentNode) {
            this.display.parentNode.insertBefore(this.toolbar, this.display);
        }

        // imposta la radice, azzera liste e riavvia la cronologia con lo stato iniziale
        this.setRoot = function(node) {
            self.root = node;
            self.nodiDaEliminare = [];
            self.relazioniDaEliminare = [];
            self.history = [];
            self.indice = -1;
            self.salvaStato(); // inizio stato appena salvato
        };

        // mette lo stato in history
        this.salvaStato = function() {
            // se faccio undo, azione, redo resettato
            self.history = self.history.slice(0, self.indice + 1);
            self.history.push({
                root: clona(self.root),
                nodi: clona(self.nodiDaEliminare),
                rel: clona(self.relazioniDaEliminare)
            });
            self.indice = self.history.length - 1;
            self.aggiornaBottoni();
        };

        // ripristina allo stato puntato
        this.ripristina = function(snap) {
            self.root = clona(snap.root);
            self.nodiDaEliminare = clona(snap.nodi);
            self.relazioniDaEliminare = clona(snap.rel);
            self.update();
            self.aggiornaBottoni();
        };

        // ogni azione che modifica chiama commit per aggiornare liste
        this.commit = function() {
            self.salvaStato();
            self.update();
        };

        this.undo = function() {
            if (self.indice > 0) {
                self.indice--;
                self.ripristina(self.history[self.indice]);
            }
        };

        this.redo = function() {
            if (self.indice < self.history.length - 1) {
                self.indice++;
                self.ripristina(self.history[self.indice]);
            }
        };

        // attiva e disattiva i bottoni
        this.aggiornaBottoni = function() {
            self.btnUndo.disabled = (self.indice <= 0);
            self.btnRedo.disabled = (self.indice >= self.history.length - 1);
        };

        this.aggiornaBottoni(); // all'avvio entrambi disabilitati

        // disegna tutto l'albero da zero
        this.update = function() {
            this.display.innerHTML = "";
            this.display.classList.add("albero-display");
            if (this.root) {
                this.renderNodo(this.root, this.display, 1, null, -1);
            }
        };

        // disegna un singolo nodo e si richiama sui figli
        this.renderNodo = function(nodo, container, livello, nodoPadre, idx) {
            var div = document.createElement("div");
            div.className = "albero-nodo" + (livello > 1 ? " figlio" : "");

            var riga = document.createElement("div");
            riga.className = "albero-riga";
            div.appendChild(riga);

            var testa = document.createElement("strong");
            testa.textContent = "[" + nodo.tipo + "] " + nodo.codice + " - ";
            riga.appendChild(testa);

            // attributi modificabili inline
            riga.appendChild(document.createTextNode("Nome: "));
            riga.appendChild(this.creaCampoEditabile(nodo, "nome", {}));

            if (nodo.tipo === "COMPOSTO") {
                riga.appendChild(document.createTextNode(" | Desc: "));
                riga.appendChild(this.creaCampoEditabile(nodo, "descrizione", {}));
                riga.appendChild(document.createTextNode(" | P.Min: "));
                riga.appendChild(this.creaCampoEditabile(nodo, "prezzoMin", { numerico: true }));
                riga.appendChild(document.createTextNode(" | P.Max: "));
                riga.appendChild(this.creaCampoEditabile(nodo, "prezzoMax", { numerico: true }));
            }

            // bottoni del nodo
            var bottoni = document.createElement("span");
            bottoni.className = "albero-bottoni";

            // area del menu +
            var menuArea = document.createElement("div");
            menuArea.className = "albero-menu";
            menuArea.style.display = "none";

            // + su composto entro i 4 livelli oppure su semplice
            if ((nodo.tipo === "COMPOSTO" && livello < 4) || nodo.tipo === "SEMPLICE") {
                var btnAdd = document.createElement("button");
                btnAdd.textContent = "+";
                btnAdd.title = "Aggiungi sottoprodotto / SKU";
                btnAdd.addEventListener("click", function() { self.mostraMenuAggiunta(nodo, menuArea); });
                bottoni.appendChild(btnAdd);
            }

            // - e -* solo dai figli in giù 
            if (livello > 1) {
                var btnRel = document.createElement("button");
                btnRel.textContent = "-";
                btnRel.title = "Rimuovi solo il legame col padre (il prodotto resta nel DB)";
                btnRel.addEventListener("click", function() {
                    if (nodo.isNuovo === false) {
						// toglie solo composizione
                        self.relazioniDaEliminare.push({ tipoRel: "COMPOSIZIONE", padre: nodoPadre.codice, figlio: nodo.codice });
                    }
                    nodoPadre.figli.splice(idx, 1);
					// salva per undo
                    self.commit();
                });
                bottoni.appendChild(btnRel);

                var btnDel = document.createElement("button");
                btnDel.textContent = "-*";
                btnDel.title = "Elimina il prodotto e i suoi sottoprodotti dal DB";
                btnDel.addEventListener("click", function() {
                    if (nodo.isNuovo === false) {
						// se non è un prodotto nuovo va in nodi da eliminare (altrimenti non serve e si toglie e basta)
                        self.nodiDaEliminare.push({ tipo: nodo.tipo, codice: nodo.codice });
                    }
                    nodoPadre.figli.splice(idx, 1);
                    self.commit();
                });
                bottoni.appendChild(btnDel);
            }

            riga.appendChild(bottoni);
            div.appendChild(menuArea);

            // figli dei composti (ricorsione)
            if (nodo.tipo === "COMPOSTO" && nodo.figli) {
                nodo.figli.forEach(function(figlio, i) {
                    self.renderNodo(figlio, div, livello + 1, nodo, i);
                });
            }

            // sku dei semplici
            if (nodo.tipo === "SEMPLICE") {
                if (!nodo.skus) nodo.skus = [];
                var ul = document.createElement("ul");
                ul.className = "albero-sku-list";
                nodo.skus.forEach(function(sku, i) {
                    ul.appendChild(self.renderSku(nodo, sku, i));
                });
                div.appendChild(ul);
            }

            container.appendChild(div);
        };

        // disegna una riga sku sotto un prodotto semplice
        this.renderSku = function(nodoSemplice, sku, idx) {
            var li = document.createElement("li");
            li.className = "albero-sku";

            var testa = document.createElement("strong");
            testa.textContent = "[" + sku.codice + "] ";
            li.appendChild(testa);

            li.appendChild(document.createTextNode("Nome: "));
            li.appendChild(this.creaCampoEditabile(sku, "nome", {}));
            li.appendChild(document.createTextNode(" | Prezzo: "));
            li.appendChild(this.creaCampoEditabile(sku, "prezzo", { numerico: true }));
            li.appendChild(document.createTextNode(" | Desc: "));
            li.appendChild(this.creaCampoEditabile(sku, "descrizione", {}));

            if (sku.isNuovo) {
                var tag = document.createElement("span");
                tag.className = "nuova";
                tag.textContent = " (NUOVA)";
                li.appendChild(tag);
            }

            // - rimuovi associazione prodotto-sku
            var btnRel = document.createElement("button");
            btnRel.textContent = "-";
            btnRel.title = "Rimuovi l'associazione tra prodotto e SKU";
            btnRel.addEventListener("click", function() {
				// impediamo anche lato client di togliere l'ultima sku
                if (!self.controllaUltimaSku(nodoSemplice)) return;
                if (sku.isNuovo === false) {
					// qui invece che composizione è realizzazione
                    self.relazioniDaEliminare.push({ tipoRel: "REALIZZAZIONE", padre: nodoSemplice.codice, figlio: sku.codice });
                }
                nodoSemplice.skus.splice(idx, 1);
                self.commit();
            });
            li.appendChild(btnRel);

            // -* elimina la sku dal db
            if (sku.isNuovo === false) {
                var btnDel = document.createElement("button");
                btnDel.textContent = "-*";
                btnDel.title = "Elimina la SKU dalla base di dati";
                btnDel.addEventListener("click", function() {
                    if (!self.controllaUltimaSku(nodoSemplice)) return;
                    self.nodiDaEliminare.push({ tipo: "SKU", codice: sku.codice });
                    nodoSemplice.skus.splice(idx, 1);
                    self.commit();
                });
                li.appendChild(btnDel);
            }

            return li;
        };

        // un prodotto semplice deve sempre avere almeno una sku
        this.controllaUltimaSku = function(nodoSemplice) {
            if (nodoSemplice.skus.length <= 1) {
                alert("Un prodotto semplice deve avere almeno una SKU associata.");
                return false;
            }
            return true;
        };

        // crea uno <span> cliccabile che diventa input, al mouseleave salva nel nodo locale
        // il salvataggio in db avviene al SALVA
        this.creaCampoEditabile = function(oggetto, prop, opts) {
            opts = opts || {};
            var span = document.createElement("span");
            span.className = "albero-campo";
            span.textContent = (oggetto[prop] !== undefined && oggetto[prop] !== null) ? oggetto[prop] : "";

            span.addEventListener("click", function() {
                if (span.querySelector("input")) return;
                var vecchio = oggetto[prop];
                span.textContent = "";

                var input = document.createElement("input");
                input.type = opts.numerico ? "number" : "text";
                if (opts.numerico && opts.intero) input.step = "1";
                input.value = vecchio;
                span.appendChild(input);
                input.focus();

                input.addEventListener("mouseleave", function() {
                    var v = input.value.trim();
                    var valido = true;
                    if (v === "") {
                        valido = false;
                    } else if (opts.numerico) {
                        var n = Number(v);
                        if (isNaN(n) || n < 0) valido = false;
                        if (opts.intero && !Number.isInteger(n)) valido = false;
                    }
                    var cambiato = false;
                    if (valido) {
                        var nuovo = opts.numerico ? Number(v) : v;
                        if (oggetto[prop] !== nuovo) {
                            oggetto[prop] = nuovo;
                            cambiato = true;
                        }
                    }
                    // in history solo se cambiato davvero, altrimenti solo redraw
                    if (cambiato) self.commit();
                    else self.update();
                });
            });
            return span;
        };

        // menu + dipende dal tipo del nodo
        this.mostraMenuAggiunta = function(nodo, menuArea) {
            menuArea.style.display = "block";

            if (nodo.tipo === "COMPOSTO") {
                menuArea.innerHTML =
                    '<p><strong>Aggiungi sottoprodotto a: ' + nodo.nome + '</strong></p>' +
                    '<button class="btn-comp">Sottoprodotto Composto</button> ' +
                    '<button class="btn-sempl">Sottoprodotto Semplice</button> ' +
                    '<button class="btn-annulla">Annulla</button>' +
                    '<div class="albero-menu-dettagli"></div>';
                var dett = menuArea.querySelector(".albero-menu-dettagli");
                menuArea.querySelector(".btn-annulla").addEventListener("click", function() { menuArea.style.display = "none"; });

                // nuovo sottoprodotto composto
                menuArea.querySelector(".btn-comp").addEventListener("click", function() {
                    dett.innerHTML =
                        '<input type="number" class="t-cod" placeholder="Codice"> ' +
                        '<input type="text" class="t-nome" placeholder="Nome"> ' +
                        '<input type="text" class="t-desc" placeholder="Descrizione"> ' +
                        '<input type="number" class="t-pmin" placeholder="P.Min"> ' +
                        '<input type="number" class="t-pmax" placeholder="P.Max"> ' +
                        '<button class="t-ok">OK</button>';
                    dett.querySelector(".t-ok").addEventListener("click", function() {
                        var cod = parseInt(dett.querySelector(".t-cod").value);
                        var nome = dett.querySelector(".t-nome").value.trim();
                        if (isNaN(cod) || nome === "") { alert("Codice e nome sono obbligatori."); return; }
                        nodo.figli.push({
                            tipo: "COMPOSTO", isNuovo: true, codice: cod, nome: nome,
                            descrizione: dett.querySelector(".t-desc").value.trim(),
                            prezzoMin: parseFloat(dett.querySelector(".t-pmin").value) || 0,
                            prezzoMax: parseFloat(dett.querySelector(".t-pmax").value) || 0,
                            figli: []
                        });
                        menuArea.style.display = "none";
                        self.commit();
                    });
                });

                // nuovo sottoprodotto semplice con scelta SKU esistenti
                menuArea.querySelector(".btn-sempl").addEventListener("click", function() {
                    self.caricaSkusEsistenti(function(skus) {
                        var checks = skus.map(function(s) {
                            return '<label><input type="checkbox" value="' + s.codice + '"> [' + s.codice + '] ' + s.nome + '</label>';
                        }).join("");
                        dett.innerHTML =
                            '<input type="number" class="t-cod" placeholder="Codice"> ' +
                            '<input type="text" class="t-nome" placeholder="Nome">' +
                            '<p>Associa SKU:</p>' +
                            '<div class="scroll-box">' + checks + '</div>' +
                            '<button class="t-ok">OK</button>';
                        dett.querySelector(".t-ok").addEventListener("click", function() {
                            var cod = parseInt(dett.querySelector(".t-cod").value);
                            var nome = dett.querySelector(".t-nome").value.trim();
                            if (isNaN(cod) || nome === "") { alert("Codice e nome sono obbligatori."); return; }
                            var scelte = Array.from(dett.querySelectorAll(".scroll-box input:checked"));
                            if (scelte.length === 0) { alert("Seleziona almeno una SKU."); return; }
                            var skusScelte = scelte.map(function(cb) {
                                var s = skus.find(function(x) { return x.codice === parseInt(cb.value); });
                                return { codice: s.codice, nome: s.nome, prezzo: s.prezzo, descrizione: s.descrizione, foto: s.foto, isNuovo: false };
                            });
                            nodo.figli.push({ tipo: "SEMPLICE", isNuovo: true, codice: cod, nome: nome, skus: skusScelte });
                            menuArea.style.display = "none";
                            self.commit();
                        });
                    });
                });

            } else if (nodo.tipo === "SEMPLICE") {
                menuArea.innerHTML =
                    '<p><strong>Aggiungi SKU a: ' + nodo.nome + '</strong></p>' +
                    '<button class="btn-nuova">Nuova SKU</button> ' +
                    '<button class="btn-esist">SKU Esistente</button> ' +
                    '<button class="btn-annulla">Annulla</button>' +
                    '<div class="albero-menu-dettagli"></div>';
                var dett2 = menuArea.querySelector(".albero-menu-dettagli");
                menuArea.querySelector(".btn-annulla").addEventListener("click", function() { menuArea.style.display = "none"; });

                // nuova SKU al prodotto semplice
                menuArea.querySelector(".btn-nuova").addEventListener("click", function() {
                    dett2.innerHTML =
                        '<input type="number" class="s-cod" placeholder="Codice"> ' +
                        '<input type="text" class="s-nome" placeholder="Nome"> ' +
                        '<input type="text" class="s-foto" placeholder="Foto URL"> ' +
                        '<input type="number" step="0.01" class="s-prezzo" placeholder="Prezzo"> ' +
                        '<input type="text" class="s-desc" placeholder="Descrizione"> ' +
                        '<button class="s-ok">Crea</button>';
                    dett2.querySelector(".s-ok").addEventListener("click", function() {
                        var cod = parseInt(dett2.querySelector(".s-cod").value);
                        var nome = dett2.querySelector(".s-nome").value.trim();
                        var prezzo = parseFloat(dett2.querySelector(".s-prezzo").value);
                        if (isNaN(cod) || nome === "" || isNaN(prezzo) || prezzo < 0) {
                            alert("Codice, nome e prezzo (>= 0) sono obbligatori.");
                            return;
                        }
                        if (nodo.skus.some(function(s) { return s.codice === cod; })) {
                            alert("Una SKU con questo codice è già presente.");
                            return;
                        }
                        nodo.skus.push({
                            codice: cod, nome: nome,
                            foto: dett2.querySelector(".s-foto").value,
                            prezzo: prezzo,
                            descrizione: dett2.querySelector(".s-desc").value,
                            isNuovo: true
                        });
                        menuArea.style.display = "none";
                        self.commit();
                    });
                });

                // scelta di una o piu' SKU esistenti (toglie quelle gia' associate)
                menuArea.querySelector(".btn-esist").addEventListener("click", function() {
                    self.caricaSkusEsistenti(function(skus) {
                        var disponibili = skus.filter(function(s) {
                            return !nodo.skus.some(function(x) { return x.codice === s.codice; });
                        });
                        if (disponibili.length === 0) {
                            dett2.innerHTML = '<p style="color:red;">Tutte le SKU sono gia' + "'" + ' associate a questo prodotto.</p>';
                            return;
                        }
                        var checks = disponibili.map(function(s) {
                            return '<label><input type="checkbox" value="' + s.codice + '"> [' + s.codice + '] ' + s.nome + '</label>';
                        }).join("");
                        dett2.innerHTML = '<div class="scroll-box">' + checks + '</div><button class="e-ok">Aggiungi selezionate</button>';
                        dett2.querySelector(".e-ok").addEventListener("click", function() {
                            var scelte = Array.from(dett2.querySelectorAll(".scroll-box input:checked"));
                            if (scelte.length === 0) return;
                            scelte.forEach(function(cb) {
                                var s = disponibili.find(function(x) { return x.codice === parseInt(cb.value); });
                                nodo.skus.push({ codice: s.codice, nome: s.nome, prezzo: s.prezzo, descrizione: s.descrizione, foto: s.foto, isNuovo: false });
                            });
                            menuArea.style.display = "none";
                            self.commit();
                        });
                    });
                });
            }
        };

        // carica l'elenco delle sku esistenti 
        this.caricaSkusEsistenti = function(cback) {
            makeCall("GET", "GetSkus", null, function(req) {
                if (req.readyState === XMLHttpRequest.DONE && req.status === 200) {
                    cback(JSON.parse(req.responseText));
                }
            });
        };

        // controlla che tutti i prodotti siano in regola prima di spedire al server
        this.validaNodo = function(nodo) {
            if (nodo.tipo === "COMPOSTO") {
                if (!nodo.figli || nodo.figli.length < 1) {
                    alert('Il prodotto composto [' + nodo.codice + '] "' + nodo.nome + '" deve avere almeno 1 sottoprodotto.');
                    return false;
                }
                for (var i = 0; i < nodo.figli.length; i++) {
                    if (!this.validaNodo(nodo.figli[i])) return false;
                }
            } else if (nodo.tipo === "SEMPLICE") {
                if (!nodo.skus || nodo.skus.length < 1) {
                    alert('Il prodotto semplice [' + nodo.codice + '] "' + nodo.nome + '" deve avere almeno 1 SKU.');
                    return false;
                }
            }
            return true;
        };

        // salva l'albero e le eliminazioni nel db
        this.salva = function(onSuccess) {
            if (!this.root) return;
            if (!this.validaNodo(this.root)) return;

            var fd = new FormData();
            fd.append("alberoJSON", JSON.stringify(this.root));
            fd.append("daEliminareJSON", JSON.stringify(this.nodiDaEliminare));
            fd.append("relazioniDaEliminareJSON", JSON.stringify(this.relazioniDaEliminare));

            makeCall("POST", "SaveAlberoComposto", fd, function(req) {
                if (req.readyState === XMLHttpRequest.DONE) {
                    if (req.status === 200) {
                        self.alert.textContent = "Salvataggio completato con successo!";
                        self.alert.style.color = "green";
                        if (onSuccess) onSuccess();
                    } else {
                        self.alert.textContent = "Errore salvataggio: " + req.responseText;
                        self.alert.style.color = "red";
                    }
                }
            });
        };
    }


    // gestore di creazione con form
    function GestoreCreazione(containerId, alertId) {
        this.container = document.getElementById(containerId);
        this.alertContainer = document.getElementById(alertId);
        this.menuSkus = document.getElementById("id_menuSkus");

        this.skusCache = [];
        this.prodottiCache = [];

        // editor dell'albero composto in costruzione
        this.albero = new AlberoProdotto(document.getElementById("id_alberoDisplay"), this.alertContainer);

        var self = this;

        this.show = function() {
            this.container.style.display = "flex";

            // mostra le tre form
            document.getElementById("id_formCreaSku").closest("div").style.display = "block";
            document.getElementById("id_formCreaSemplice").closest("div").style.display = "block";
            document.getElementById("id_formCreaComposto").closest("div").style.display = "block";

            // nascondi dettagli e costruttore albero
            document.getElementById("id_costruttoreAlbero").style.display = "none";
            document.getElementById("id_dettaglioNuovaSku").style.display = "none";
            document.getElementById("id_dettaglioNuovoSemplice").style.display = "none";

			// per i semplici
            this.loadSkus();
			// per i composti
            this.loadProdottiDisponibili();
        };

        this.hide = function() {
            this.container.style.display = "none";
            document.getElementById("id_costruttoreAlbero").style.display = "none";
        };

        // creazione sku con salvataggio immediato e dettaglio editabile
        document.getElementById("id_submitCreateSku").addEventListener('click', function(e) {
            var form = e.target.closest("form");
            if (!form.checkValidity()) { form.reportValidity(); return; }

            makeCall("POST", "CreateSku", form, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    var sku = JSON.parse(req.responseText);
                    self.alertContainer.textContent = "SKU " + sku.nome + " creata.";
                    self.alertContainer.style.color = "green";
                    self.mostraDettaglioSku(sku);
                    form.reset(); // svuotiamo solo se è andata a buon fine
                    self.loadSkus();
                } else {
                    self.alertContainer.textContent = req.responseText;
                    self.alertContainer.style.color = "red";
                }
            }, false); // in caso di errore non svuota la form
        });

        // mostra il pannello della sku appena creata, con campi a salvataggio immediato
        this.mostraDettaglioSku = function(sku) {
            document.getElementById("id_dettaglioNuovaSku").style.display = "block";
            document.getElementById("id_skuCodiceDisplay").textContent = sku.codice;
            this.popolaCampoAutosave("id_skuNomeDisplay", sku.codice, "nome", sku.nome, false);
            this.popolaCampoAutosave("id_skuFotoDisplay", sku.codice, "foto", sku.foto, false);
            this.popolaCampoAutosave("id_skuPrezzoDisplay", sku.codice, "prezzo", sku.prezzo, true);
            this.popolaCampoAutosave("id_skuDescrizioneDisplay", sku.codice, "descrizione", sku.descrizione, false);
        };

        this.popolaCampoAutosave = function(spanId, codice, campo, valore, numerico) {
            var span = document.getElementById(spanId);
            span.textContent = "";
            span.appendChild(campoSkuAutosave(codice, campo, valore, numerico));
        };

        // carica sku per il menu del prodotto semplice
        this.loadSkus = function() {
            makeCall("GET", "GetSkus", null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE || req.status !== 200) return;
                self.skusCache = JSON.parse(req.responseText);
                self.menuSkus.innerHTML = "";
                self.skusCache.forEach(function(sku) {
                    var label = document.createElement("label");
                    var cb = document.createElement("input");
                    cb.type = "checkbox";
                    cb.name = "skuScelti";
                    cb.value = sku.codice;
                    label.appendChild(cb);
                    label.appendChild(document.createTextNode(" [" + sku.codice + "] " + sku.nome + " - " + sku.prezzo + "€"));
                    self.menuSkus.appendChild(label);
                });
            });
        };

        // carica i prodotti disponibili come sottoprodotti
        this.loadProdottiDisponibili = function() {
            makeCall("GET", "GetProdottiDisponibili", null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE || req.status !== 200) return;
                self.prodottiCache = JSON.parse(req.responseText);
                var menu = document.getElementById("id_menuProdottiDisponibili");
                menu.innerHTML = "";
                self.prodottiCache.forEach(function(prod) {
                    var label = document.createElement("label");
                    var cb = document.createElement("input");
                    cb.type = "checkbox";
                    cb.name = "prodottiScelti";
                    cb.value = prod.codice;
                    label.appendChild(cb);
                    label.appendChild(document.createTextNode(" [" + prod.codice + "] " + prod.nome + " (" + prod.tipo + ")"));
                    menu.appendChild(label);
                });
            });
        };

        // creazione prodotto semplice salvato subito sul db
        document.getElementById("id_submitCreateSemplice").addEventListener('click', function(e) {
            var form = e.target.closest("form");
            if (!form.checkValidity()) { form.reportValidity(); return; }

            makeCall("POST", "CreateProdottoSemplice", form, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    var prod = JSON.parse(req.responseText);
                    self.alertContainer.textContent = "Prodotto semplice creato con successo!";
                    self.alertContainer.style.color = "green";

                    document.getElementById("id_dettaglioNuovoSemplice").style.display = "block";
                    document.getElementById("id_sempliceCodiceDisplay").textContent = prod.codice;
                    document.getElementById("id_sempliceNomeDisplay").textContent = prod.nome;
                    var ul = document.getElementById("id_sempliceSkusDisplay");
                    ul.innerHTML = "";
                    (prod.skus || []).forEach(function(sku) {
                        var li = document.createElement("li");
                        li.textContent = sku.nome + " (" + sku.prezzo + "€)";
                        ul.appendChild(li);
                    });

                    form.reset(); // svuotiamo solo se è andata a buon fine
                    self.loadSkus();
                    self.loadProdottiDisponibili();
                } else {
                    self.alertContainer.textContent = req.responseText;
                    self.alertContainer.style.color = "red";
                }
            }, false); // in caso di errore non svuota la form
        });

        // creazione prodotto composto, costruisce la radice e passa all'editor 
        document.getElementById("id_submitCreateComposto").addEventListener('click', function(e) {
            var form = e.target.closest("form");
            if (!form.checkValidity()) { form.reportValidity(); return; }

            // sottoprodotti scelti: cloniamo l'albero completo preso dalla cache
            var figli = [];
            Array.from(document.querySelectorAll("#id_menuProdottiDisponibili input:checked")).forEach(function(cb) {
                var prod = self.prodottiCache.find(function(p) { return p.codice === parseInt(cb.value); });
                if (prod) figli.push(JSON.parse(JSON.stringify(prod))); // deep clone
            });

            var root = {
                tipo: "COMPOSTO",
                isNuovo: true,
                codice: parseInt(form.codice.value),
                nome: form.nome.value,
                descrizione: form.descrizione.value,
                prezzoMin: parseFloat(form.prezzoMin.value),
                prezzoMax: parseFloat(form.prezzoMax.value),
                figli: figli
            };

            // nascondi le form e mostra il costruttore dell'albero
            document.getElementById("id_formCreaSku").closest("div").style.display = "none";
            document.getElementById("id_formCreaSemplice").closest("div").style.display = "none";
            form.closest("div").style.display = "none";
            document.getElementById("id_costruttoreAlbero").style.display = "block";

            self.albero.setRoot(root);
            self.albero.update();
        });

        // salva il prodotto finale nel db e ricarica tutto
        document.getElementById("id_btnSalvaAlberoFinale").addEventListener('click', function() {
            self.albero.salva(function() {
                self.hide();
                document.getElementById("id_formCreaComposto").reset();
                self.loadSkus();
                self.loadProdottiDisponibili();
            });
        });
    }


    // gestore del catalogo
    function GestoreCatalogo(containerId, formId, tableId, bodyRisultatiId, alertId) {
        this.container = document.getElementById(containerId);
        this.form = document.getElementById(formId);
        this.table = document.getElementById(tableId);
        this.bodyRisultati = document.getElementById(bodyRisultatiId);
        this.alertContainer = document.getElementById(alertId);
        this.alberoArea = document.getElementById("id_alberoRicercaArea");

        // usa anche lui albero perché uguale
        this.albero = new AlberoProdotto(document.getElementById("id_alberoRicercaDisplay"), this.alertContainer);

        var self = this;

        this.show = function() {
            this.container.style.display = "block";
            this.table.style.display = "none";
            this.alberoArea.style.display = "none";
            this.bodyRisultati.innerHTML = "";
            this.form.reset();
        };

        this.hide = function() {
            this.container.style.display = "none";
            this.alertContainer.textContent = "";
        };

        // barra di ricerca
        this.form.querySelector("input[type='button']").addEventListener('click', function() {
            if (!self.form.checkValidity()) { self.form.reportValidity(); return; }
            var keyword = self.form.keyword.value;
            makeCall("GET", "GetRicercaProdotti?keyword=" + encodeURIComponent(keyword), null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE) return;
                if (req.status === 200) {
                    self.mostraRisultati(JSON.parse(req.responseText));
                } else {
                    self.alertContainer.textContent = "Errore durante la ricerca.";
                    self.alertContainer.style.color = "red";
                }
            });
        });

		// preso il json dei risultati costruisce il testo
        this.mostraRisultati = function(risultati) {
            self.bodyRisultati.innerHTML = "";
            self.alberoArea.style.display = "none";

            if (risultati.length === 0) {
                var tr = document.createElement("tr");
                var td = document.createElement("td");
                td.colSpan = 4;
                td.textContent = "Nessun risultato trovato.";
                tr.appendChild(td);
                self.bodyRisultati.appendChild(tr);
            } else {
                risultati.forEach(function(r) {
                    var tr = document.createElement("tr");
                    [r.codice, "[" + r.tipo + "]", r.nome].forEach(function(val) {
                        var td = document.createElement("td");
                        td.textContent = val;
                        tr.appendChild(td);
                    });
                    var tdBtn = document.createElement("td");
                    var btn = document.createElement("button");
                    btn.textContent = "Vedi / Modifica";
                    btn.addEventListener("click", function() { self.caricaAlbero(r.codice, r.tipo); });
                    tdBtn.appendChild(btn);
                    tr.appendChild(tdBtn);
                    self.bodyRisultati.appendChild(tr);
                });
            }
            self.table.style.display = "table";
        };

        // carica e mostra l'oggetto cliccato
        this.caricaAlbero = function(codice, tipo) {
            makeCall("GET", "GetAlberoProdotto?codice=" + encodeURIComponent(codice) + "&tipo=" + encodeURIComponent(tipo), null, function(req) {
                if (req.readyState !== XMLHttpRequest.DONE || req.status !== 200) return;
                var root = JSON.parse(req.responseText);
                self.alberoArea.style.display = "block";

                if (root.tipo === "SKU") {
                    self.mostraSkuSingola(root); // SKU foglia: card con salvataggio immediato
                } else {
                    document.getElementById("id_btnSalvaModificheRicerca").style.display = "block";
                    self.albero.setRoot(root);
                    self.albero.update();
                }
            });
        };

        // sku singola si modifica con salvataggio immediato più ELIMINA
        this.mostraSkuSingola = function(sku) {
            document.getElementById("id_btnSalvaModificheRicerca").style.display = "none";
            var disp = self.albero.display;
            disp.innerHTML = "";

            var titolo = document.createElement("p");
            titolo.innerHTML = "<strong>[SKU] " + sku.codice + "</strong> — le modifiche si salvano automaticamente";
            disp.appendChild(titolo);

            var riga = document.createElement("div");
            riga.appendChild(document.createTextNode("Nome: "));
            riga.appendChild(campoSkuAutosave(sku.codice, "nome", sku.nome, false));
            riga.appendChild(document.createTextNode(" | Prezzo: "));
            riga.appendChild(campoSkuAutosave(sku.codice, "prezzo", sku.prezzo, true));
            riga.appendChild(document.createTextNode(" | Foto: "));
            riga.appendChild(campoSkuAutosave(sku.codice, "foto", sku.foto, false));
            riga.appendChild(document.createTextNode(" | Desc: "));
            riga.appendChild(campoSkuAutosave(sku.codice, "descrizione", sku.descrizione, false));
            disp.appendChild(riga);

            var btnElim = document.createElement("button");
            btnElim.textContent = "ELIMINA SKU dal catalogo";
            btnElim.className = "btn-elimina";
            btnElim.addEventListener("click", function() {
                if (!confirm("Eliminare definitivamente questa SKU dalla base di dati?")) return;
                var fd = new FormData();
                fd.append("daEliminareJSON", JSON.stringify([{ tipo: "SKU", codice: sku.codice }]));
                makeCall("POST", "SaveAlberoComposto", fd, function(req) {
                    if (req.readyState !== XMLHttpRequest.DONE) return;
                    if (req.status === 200) {
                        self.alertContainer.textContent = "SKU eliminata.";
                        self.alertContainer.style.color = "green";
                        self.alberoArea.style.display = "none";
                    } else {
                        self.alertContainer.textContent = "Errore: " + req.responseText;
                        self.alertContainer.style.color = "red";
                    }
                });
            });
            disp.appendChild(btnElim);
        };

        // salva modifiche al catalogo 
        document.getElementById("id_btnSalvaModificheRicerca").addEventListener('click', function() {
            self.albero.salva(function() {
                self.alberoArea.style.display = "none";
            });
        });
    }


    // orchestrator
    function PageOrchestrator() {
        var alertGlobalId = "id_alertGlobal";

        this.catalogo = new GestoreCatalogo("sezioneCatalogo", "id_formRicerca", "id_tableRisultatiRicerca", "id_bodyRisultatiRicerca", alertGlobalId);
        this.creazione = new GestoreCreazione("sezioneCreazione", alertGlobalId);

        var self = this;

        this.start = function() {
            // controllo di sicurezza lato client
            var sessionUtente = sessionStorage.getItem('utente');
            if (!sessionUtente) { window.location.href = "index.html"; return; }
            utenteLoggato = JSON.parse(sessionUtente);
            if (utenteLoggato.ruolo !== "FORNITORE") { window.location.href = "index.html"; return; }

			// saluto con nome e cognome
            document.getElementById("id_welcomeMessage").textContent =
                "Ciao, " + utenteLoggato.nome + " " + utenteLoggato.cognome;

            document.getElementById("id_btnShowCatalogo").addEventListener('click', function() {
                self.nascondiTutto();
                self.catalogo.show();
            });

            document.getElementById("id_btnShowCreazione").addEventListener('click', function() {
                self.nascondiTutto();
                self.creazione.show();
            });

            document.getElementById("id_logoutBtn").addEventListener('click', function() {
                makeCall("GET", "Logout", null, function(req) {
                    if (req.readyState === XMLHttpRequest.DONE) {
                        sessionStorage.removeItem('utente');
                        window.location.href = "index.html";
                    }
                });
            });

            // di default mostra l'area di creazione
            this.creazione.show();
        };

        this.nascondiTutto = function() {
            this.catalogo.hide();
            this.creazione.hide();
            document.getElementById("id_alertGlobal").textContent = "";
        };
    }

    window.addEventListener("load", function() {
        orchestrator = new PageOrchestrator();
        orchestrator.start();
    });

})();
