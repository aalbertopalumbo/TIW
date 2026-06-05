(function() { // mettiamo tutto in una function per non mettere variabili globali e tenere in scope

    var orchestrator;
    var utenteLoggato;


	// catalogo prodotti
    function GestoreCatalogo(containerId, tbodyId, alertId) {
        this.container = document.getElementById(containerId);
        this.tbody = document.getElementById(tbodyId);
        this.alertContainer = document.getElementById(alertId);

        this.show = function() {
            this.container.style.display = "block";
            this.loadDati(); // quando mostro il catalogo prendo da db
        };

        this.hide = function() {
            this.container.style.display = "none";
            this.alertContainer.textContent = ""; // pulisce i dati
        };

        this.loadDati = function() {
            // Qui più avanti metteremo makeCall("GET", "GetCatalogoFornitore" ...)
            // e costruiremo i tag <tr> e <td>
            console.log("Simulazione: Scaricamento dati catalogo da DB...");
        };
    }

    // teniamo un gestore per tutte le form di creazione
	function GestoreCreazione(containerId, alertId) {
        this.container = document.getElementById(containerId);
        this.alertContainer = document.getElementById(alertId);
		this.menuSkus = document.getElementById("id_menuSkus");
		
		// variabile per tenere in memoria l'albero del prod composto
		this.alberoCorrente = null;
		this.skusCache = [];
		this.prodottiCache = [];
		this.nodiDaEliminare = [];
		this.relazioniDaEliminare = [];

        this.show = function() {
			this.container.style.display = "flex"; 
			            
            // rimettiamo le form
            document.getElementById("id_formCreaSku").closest("div").style.display = "block";
            document.getElementById("id_formCreaSemplice").closest("div").style.display = "block";
            document.getElementById("id_formCreaComposto").closest("div").style.display = "block";
            
            // togliamo i dettagli 
            document.getElementById("id_costruttoreAlbero").style.display = "none";
            document.getElementById("id_dettaglioNuovaSku").style.display = "none";
            document.getElementById("id_dettaglioNuovoSemplice").style.display = "none";
            
            this.loadSkus();
			this.loadProdottiDisponibili();
        };

        this.hide = function() {
            this.container.style.display = "none";
			document.getElementById("id_costruttoreAlbero").style.display = "none";
        };

        // evento di creazione sku
		document.getElementById("id_submitCreateSku").addEventListener('click', (e) => {
            var form = e.target.closest("form");
            
            if (form.checkValidity()) {
                var self = this; 
                
                makeCall("POST", "CreateSku", form, function(req) {
                    if (req.readyState === XMLHttpRequest.DONE) {
                        if (req.status === 200) {
                            // parsing json
                            var skuCreata = JSON.parse(req.responseText);
                            
                            // successo
                            self.alertContainer.textContent = "SKU " + skuCreata.nome + " creata.";
                            self.alertContainer.style.color = "green";
                            
                            // mostriamo i dati della sku
                            document.getElementById("id_dettaglioNuovaSku").style.display = "block";
                            document.getElementById("id_skuCodiceDisplay").textContent = skuCreata.codice;
                            document.getElementById("id_skuNomeDisplay").textContent = skuCreata.nome;
                            document.getElementById("id_skuFotoDisplay").textContent = skuCreata.foto;
                            document.getElementById("id_skuDescrizioneDisplay").textContent = skuCreata.descrizione;
						    document.getElementById("id_skuPrezzoDisplay").textContent = skuCreata.prezzo;

                            // attiviamo l'inline editing
                            attivaInlineEditing(skuCreata.codice);

                        } else {
                            self.alertContainer.textContent = req.responseText;
                            self.alertContainer.style.color = "red";
                        }
                    }
                });
            } else {
                form.reportValidity();
            }
        });


        // inline editing per sku
        function attivaInlineEditing(codiceSku) {
            // prendiamo tutti gli span con la classe editable
            var campi = document.querySelectorAll("#id_dettaglioNuovaSku .editable");
            
            campi.forEach(function(spanElement) {
                // cloniamo lo span per rimuovere eventuali listener precedenti e evitare conflitti
                var nuovoSpan = spanElement.cloneNode(true);
                spanElement.parentNode.replaceChild(nuovoSpan, spanElement);

                nuovoSpan.addEventListener('click', function() {
                    // Se c'è già un input dentro, non fare nulla
                    if (this.querySelector('input')) return;

                    var nomeCampo = this.getAttribute('data-campo');
                    var valoreVecchio = this.textContent; // salviamo il vecchio valore per eventuale rollback

                    // svuotiamo lo span e ci mettiamo dentro un input
                    this.textContent = '';
                    var inputElement = document.createElement('input');
                    inputElement.type = (nomeCampo === 'prezzo') ? 'number' : 'text';
                    inputElement.value = valoreVecchio;
                    
                    this.appendChild(inputElement);
                    inputElement.focus(); // focus sull'input per comodità

                    // evento mouseleave
                    inputElement.addEventListener('mouseleave', function() {
                        var valoreNuovo = inputElement.value.trim();

                        // se non ci sono modifiche non facciamo niente
                        if (valoreNuovo === valoreVecchio) {
                            nuovoSpan.textContent = valoreVecchio;
                            return;
                        }

                        // impediamo valori sbagliati
                        if (valoreNuovo === "" || (nomeCampo === 'prezzo' && parseFloat(valoreNuovo) <= 0)) {
                            nuovoSpan.textContent = valoreVecchio; // ripristino
                            alert("Valore inserito non valido. Modifica annullata.");
                            return;
                        }

                        // altrimenti salviamo
                        var formData = new FormData();
                        formData.append("codice", codiceSku);
                        formData.append("campo", nomeCampo);
                        formData.append("valore", valoreNuovo);

                        // makecall asincrona
                        makeCall("POST", "UpdateSkuInline", formData, function(req) {
                            if (req.readyState === XMLHttpRequest.DONE) {
                                if (req.status === 200) {
                                    // ok
                                    nuovoSpan.textContent = valoreNuovo;
                                    nuovoSpan.style.color = "blue";
                                    setTimeout(() => nuovoSpan.style.color = "", 1000);
                                } else {
                                    // rollback
                                    nuovoSpan.textContent = valoreVecchio;
                                    alert("Errore di rete: " + req.responseText + ". Ripristino valore originale.");
                                }
                            }
                        }, false);
                    });
                });
            });
        }
		
		// carichiamo le sku per il menu a tendina dei prod semplici
		this.loadSkus = function() {
            var self = this;
            makeCall("GET", "GetSkus", null, function(req) {
                if (req.readyState === XMLHttpRequest.DONE && req.status === 200) {
                    var listaSkus = JSON.parse(req.responseText);
					self.skusCache = listaSkus;
                    self.menuSkus.innerHTML = ""; // svuotiamo 
                    
                    listaSkus.forEach(function(sku) {
                        // facciamo un label per sku
                        var label = document.createElement("label");
                        label.style.display = "block";
                        label.style.cursor = "pointer";
                        label.style.marginBottom = "5px";

                        // creiamo la checkbox
                        var checkbox = document.createElement("input");
                        checkbox.type = "checkbox";
                        checkbox.name = "skuScelti";
                        checkbox.value = sku.codice;
                        
                        // <label><input type="checkbox"> [Codice] Nome</label>
                        label.appendChild(checkbox);
                        label.appendChild(document.createTextNode(" [" + sku.codice + "] " + sku.nome + " - " + sku.prezzo + "€"));
                        
                        // aggiungiamo la riga al div
                        self.menuSkus.appendChild(label);
                    });
                }
            });
        };
		
		this.loadProdottiDisponibili = function() {
            var self = this;
            makeCall("GET", "GetProdottiDisponibili", null, function(req) {
                if (req.readyState === XMLHttpRequest.DONE && req.status === 200) {
                    var listaProdotti = JSON.parse(req.responseText);
                    self.prodottiCache = listaProdotti; 
                    
                    var menuProdotti = document.getElementById("id_menuProdottiDisponibili");
                    menuProdotti.innerHTML = ""; 
                    
                    listaProdotti.forEach(function(prod) {
                        var label = document.createElement("label");
                        label.style.display = "block"; 
                        label.style.cursor = "pointer"; 
                        
                        var checkbox = document.createElement("input");
                        checkbox.type = "checkbox";
                        checkbox.name = "prodottiScelti"; 
                        checkbox.value = prod.codice;
                        checkbox.dataset.tipo = prod.tipo; // SALVIAMO IL TIPO SULLA CHECKBOX!
                        
                        label.appendChild(checkbox);
                        label.appendChild(document.createTextNode(` [${prod.codice}] ${prod.nome} (${prod.tipo})`));
                        menuProdotti.appendChild(label);
                    });
                }
            });
        };

        // creazione prodotto semplice
		document.getElementById("id_submitCreateSemplice").addEventListener('click', (e) => {
	        var form = e.target.closest("form");
	        if (form.checkValidity()) {
	            var self = this;
	            
	            makeCall("POST", "CreateProdottoSemplice", form, function(req) {
	                if (req.readyState === XMLHttpRequest.DONE) {
	                    if (req.status === 200) {
	                        var prodSemplice = JSON.parse(req.responseText);
	                        
	                        self.alertContainer.textContent = "Prodotto Semplice creato con successo!";
	                        self.alertContainer.style.color = "green";
	                        
	                        // mostriamo lo schermo aggiornato
	                        document.getElementById("id_dettaglioNuovoSemplice").style.display = "block";
	                        document.getElementById("id_sempliceCodiceDisplay").textContent = prodSemplice.codice;
	                        document.getElementById("id_sempliceNomeDisplay").textContent = prodSemplice.nome;
	                        
	                        // mettiamo la lista delle sku collegate
	                        var ulSkus = document.getElementById("id_sempliceSkusDisplay");
	                        ulSkus.innerHTML = "";
	                        prodSemplice.skus.forEach(function(sku) {
	                            var li = document.createElement("li");
	                            li.textContent = sku.nome + " (" + sku.prezzo + "€)";
	                            ulSkus.appendChild(li);
	                        });
	
	                        // ricarichiamo le SKU nella tendina
	                        self.loadSkus(); 
	                    } else {
	                        self.alertContainer.textContent = req.responseText;
	                        self.alertContainer.style.color = "red";
	                    }
	                }
	            });
	        } else {
	            form.reportValidity();
	        }
	    });
	
	    // creazione prodotto composto
		document.getElementById("id_submitCreateComposto").addEventListener('click', (e) => {
            var form = e.target.closest("form");
            if (form.checkValidity()) {
                let selectedProdotti = Array.from(document.querySelectorAll("input[name='prodottiScelti']:checked"));
                let figliIniziali = [];
                
				selectedProdotti.forEach(cb => {
                    let prodDati = this.prodottiCache.find(p => p.codice === parseInt(cb.value));
                    if(prodDati) {
                        // LA MAGIA: Cloniamo profondamente l'intero albero prelevato dal DB!
                        // Così possiamo modificarlo, eliminare i suoi figli ecc. senza corrompere la cache originale.
                        let cloneAlbero = JSON.parse(JSON.stringify(prodDati));
                        figliIniziali.push(cloneAlbero);
                    }
                });

                this.alberoCorrente = {
                    tipo: "COMPOSTO",
                    isNuovo: true, // la radice è sempre nuova
                    codice: form.codice.value,
                    nome: form.nome.value,
                    prezzoMin: parseFloat(form.prezzoMin.value),
                    prezzoMax: parseFloat(form.prezzoMax.value),
                    descrizione: form.descrizione.value,
                    figli: figliIniziali
                };

                // nascondi form e mostra albero come prima
                document.getElementById("id_formCreaSku").closest("div").style.display = "none";
                document.getElementById("id_formCreaSemplice").closest("div").style.display = "none";
                form.closest("div").style.display = "none";
                document.getElementById("id_costruttoreAlbero").style.display = "block";
                
                this.nodiDaEliminare = []; // resetta la lista ad ogni nuova creazione
				this.relazioniDaEliminare = [];
                this.disegnaAlberoVisivo();
            } else { form.reportValidity(); }
        });


        // disegna l'albero 
        this.disegnaAlberoVisivo = function() {
            var container = document.getElementById("id_alberoDisplay");
            container.innerHTML = ""; // puliamo 
            if (!this.alberoCorrente) return;
            
            // facciamo partire la ricorsione partendo dal nodo radice
            this.renderNodo(this.alberoCorrente, container, 1, null, -1);
        };

        // modo ricorsivo che disegna nodo e richiama sè stesso sui figli
		this.renderNodo = function(nodo, container, livello, nodoPadre, indiceNelPadre) {
            var nodoDiv = document.createElement("div");
            nodoDiv.style.marginLeft = (livello === 1) ? "0px" : "30px"; 
            nodoDiv.style.borderLeft = (livello === 1) ? "none" : "2px dashed #ccc";
            nodoDiv.style.padding = "5px 10px";
            nodoDiv.style.marginTop = "5px";

            var rigaTop = document.createElement("div");
            rigaTop.innerHTML = `<strong>[${nodo.tipo}] ${nodo.codice}</strong> - <span class="editableComposto">${nodo.nome}</span>`;
            nodoDiv.appendChild(rigaTop);

            var btnContainer = document.createElement("span");
            btnContainer.style.marginLeft = "15px";

            // bottone + per i composti max liv 4 e per i semplici per aggiungere le SKU
            if ((nodo.tipo === "COMPOSTO" && livello < 4) || nodo.tipo === "SEMPLICE") {
                var btnAdd = document.createElement("button");
                btnAdd.textContent = "+";
                btnAdd.style.cursor = "pointer";
                btnAdd.onclick = () => this.mostraMenuAggiunta(nodo, formArea);
                btnContainer.appendChild(btnAdd);
            }
            
            // i bottoni - e -* appaiono solo dai figli in giù
            if (livello > 1) {
                var btnRemoveRel = document.createElement("button");
                btnRemoveRel.textContent = "-";
                btnRemoveRel.style.marginLeft = "5px";
                btnRemoveRel.onclick = () => {
					var nodoDaRimuovere = nodoPadre.figli[indiceNelPadre];
                    // Se non è nuovo, diciamo al DB di spezzare il legame!
                    if (nodoDaRimuovere.isNuovo === false) {
                        this.relazioniDaEliminare.push({ tipoRel: "COMPOSIZIONE", padre: nodoPadre.codice, figlio: nodoDaRimuovere.codice });
                    }
                    nodoPadre.figli.splice(indiceNelPadre, 1); // rimuove dalla memoria
                    this.disegnaAlberoVisivo(); // ridisegna 
                };
                btnContainer.appendChild(btnRemoveRel);

				var btnDelete = document.createElement("button");
                btnDelete.textContent = "-*";
                btnDelete.style.marginLeft = "5px";
                btnDelete.onclick = () => {
                    var nodoDaRimuovere = nodoPadre.figli[indiceNelPadre];
                    // se non è nuovo mettiamo in lista di eliminazione
                    if (nodoDaRimuovere.isNuovo === false) {
                        this.nodiDaEliminare.push({ tipo: nodoDaRimuovere.tipo, codice: nodoDaRimuovere.codice });
                    }
                    nodoPadre.figli.splice(indiceNelPadre, 1);
                    this.disegnaAlberoVisivo();
                };
                btnContainer.appendChild(btnDelete);
            }
            rigaTop.appendChild(btnContainer);

            var formArea = document.createElement("div");
            formArea.style.display = "none";
            formArea.style.border = "1px solid #FF9800";
            formArea.style.padding = "10px";
            formArea.style.marginTop = "5px";
            formArea.style.backgroundColor = "#fff";
            nodoDiv.appendChild(formArea);

            // ricorsione sui figli dei composti
            if (nodo.tipo === "COMPOSTO" && nodo.figli) {
                nodo.figli.forEach((figlio, idx) => {
                    this.renderNodo(figlio, nodoDiv, livello + 1, nodo, idx);
                });
            }

            // visualizzazione delle sku per i semplici
            if (nodo.tipo === "SEMPLICE") {
                var ulSkus = document.createElement("ul");
                ulSkus.style.margin = "5px 0 10px 20px";
                ulSkus.style.fontSize = "13px";
                
                // Funzione di sicurezza condivisa per l'eliminazione
                const tentaEliminazioneSku = (arrayDiAppartenenza, indice) => {
                    let totaleSkus = (nodo.skus ? nodo.skus.length : 0) + (nodo.nuoveSkus ? nodo.nuoveSkus.length : 0);
                    if (totaleSkus <= 1) {
                        alert("Azione negata: Un prodotto semplice deve avere almeno una SKU associata");
                        return;
                    }
                    arrayDiAppartenenza.splice(indice, 1);
                    this.disegnaAlberoVisivo();
                };

                // Disegna SKU esistenti
				if (nodo.skus) {
                    nodo.skus.forEach((codiceSku, idx) => {
                        let skuDati = this.skusCache.find(x => x.codice == codiceSku);
                        let nomeMostrato = skuDati ? skuDati.nome : "SKU " + codiceSku;
                        let li = document.createElement("li");
                        
                        // ORA ABBIAMO ENTRAMBI I BOTTONI (- e *)
                        li.innerHTML = `[${codiceSku}] ${nomeMostrato} (Esistente) 
                                        <button class="btn-minus" style="margin-left:5px;" title="Rimuovi legame">-</button>
                                        <button class="btn-star" style="margin-left:5px;" title="Elimina dal DB">*</button>`;
                        
                        // TASTO MENO: Spezza il legame
                        li.querySelector(".btn-minus").onclick = () => { 
                            this.relazioniDaEliminare.push({ tipoRel: "REALIZZAZIONE", padre: nodo.codice, figlio: codiceSku });
                            tentaEliminazioneSku(nodo.skus, idx); 
                        };

                        // TASTO STELLA: Cancella dal DB!
                        li.querySelector(".btn-star").onclick = () => { 
                            // Controllo frontend preventivo: non ti faccio nemmeno cliccare se è l'ultima!
                            let totaleSkus = (nodo.skus ? nodo.skus.length : 0) + (nodo.nuoveSkus ? nodo.nuoveSkus.length : 0);
                            if (totaleSkus <= 1) {
                                alert("Azione negata: Questa è l'unica SKU in questa schermata. Se la elimini dal DB il prodotto semplice rimarrà vuoto!");
                                return;
                            }
                            this.nodiDaEliminare.push({ tipo: "SKU", codice: codiceSku });
                            tentaEliminazioneSku(nodo.skus, idx); 
                        };
                        ulSkus.appendChild(li);
                    });
                };
                
                // Disegna Nuove SKU
                if (nodo.nuoveSkus) {
                    nodo.nuoveSkus.forEach((skuObj, idx) => {
                        let li = document.createElement("li");
                        li.innerHTML = `[${skuObj.codice}] ${skuObj.nome} - ${skuObj.prezzo}€ <strong style="color:green;">(NUOVA)</strong> <button style="margin-left:5px;">-</button>`;
                        li.querySelector("button").onclick = () => tentaEliminazioneSku(nodo.nuoveSkus, idx);
                        ulSkus.appendChild(li);
                    });
                }
                nodoDiv.appendChild(ulSkus);
            }

            container.appendChild(nodoDiv);
        };

        // 3. Il menu di aggiunta
        this.mostraMenuAggiunta = function(nodoPadre, formArea) {
            formArea.style.display = "block";
            var self = this;

            if (nodoPadre.tipo === "COMPOSTO") {
                // logica del composto
                formArea.innerHTML = `
                    <p style="margin: 0 0 5px 0;"><strong>Aggiungi figlio a: ${nodoPadre.nome}</strong></p>
                    <button id="btnSceltaComposto">Sottoprodotto Composto</button>
                    <button id="btnSceltaSemplice">Sottoprodotto Semplice</button>
                    <button onclick="this.parentNode.style.display='none'">Annulla</button>
                    <div id="divDettagliAggiunta" style="margin-top: 10px;"></div>
                `;
                var areaDettagli = formArea.querySelector("#divDettagliAggiunta");

                formArea.querySelector("#btnSceltaComposto").onclick = () => {
                    areaDettagli.innerHTML = `
                        <input type="text" id="tmp_codice" placeholder="Codice" size="5" required>
                        <input type="text" id="tmp_nome" placeholder="Nome" required>
                        <input type="number" id="tmp_pmin" placeholder="P.Min" size="5">
                        <input type="number" id="tmp_pmax" placeholder="P.Max" size="5">
                        <button id="btnConfermaComposto" style="background:green; color:white;">OK</button>
                    `;
                    areaDettagli.querySelector("#btnConfermaComposto").onclick = () => {
                        nodoPadre.figli.push({
                            tipo: "COMPOSTO",
                            codice: areaDettagli.querySelector("#tmp_codice").value,
                            nome: areaDettagli.querySelector("#tmp_nome").value,
                            prezzoMin: parseFloat(areaDettagli.querySelector("#tmp_pmin").value || 0),
                            prezzoMax: parseFloat(areaDettagli.querySelector("#tmp_pmax").value || 0),
                            figli: []
                        });
                        self.disegnaAlberoVisivo();
                    };
                };

				formArea.querySelector("#btnSceltaSemplice").onclick = () => {
                    // Creiamo le checkbox prendendole dalla cache!
                    let checkboxesHTML = self.skusCache.map(sku => 
                        `<label style="display:block;"><input type="checkbox" name="tmp_sku_nuovosemplice" value="${sku.codice}"> [${sku.codice}] ${sku.nome}</label>`
                    ).join("");

                    areaDettagli.innerHTML = `
                        <input type="text" id="tmp_codice_s" placeholder="Codice" size="5" required>
                        <input type="text" id="tmp_nome_s" placeholder="Nome" required>
                        <p style="margin: 5px 0 2px 0; font-size: 12px;">Associa SKU:</p>
                        <div style="height:80px; overflow-y:auto; border:1px solid #ccc; margin-bottom:5px; padding:3px; font-size:12px;">
                            ${checkboxesHTML}
                        </div>
                        <button id="btnConfermaSemplice" style="background:green; color:white;">OK</button>
                    `;
                    areaDettagli.querySelector("#btnConfermaSemplice").onclick = () => {
                        // Leggiamo le SKU spuntate
                        let selectedSkus = Array.from(areaDettagli.querySelectorAll("input[name='tmp_sku_nuovosemplice']:checked")).map(cb => parseInt(cb.value));
                        if(selectedSkus.length === 0) {
                            alert("Devi selezionare almeno una SKU per il prodotto semplice!");
                            return;
                        }

                        nodoPadre.figli.push({
                            tipo: "SEMPLICE",
                            isNuovo: true,
                            codice: areaDettagli.querySelector("#tmp_codice_s").value,
                            nome: areaDettagli.querySelector("#tmp_nome_s").value,
                            skus: selectedSkus, // Le inseriamo subito!
                            nuoveSkus: []
                        });
                        self.disegnaAlberoVisivo();
                    };
                };

            } else if (nodoPadre.tipo === "SEMPLICE") {
                // se nodo padre è semplice facciamo scegliere se sku nuova o esistente
                formArea.innerHTML = `
                    <p style="margin: 0 0 5px 0;"><strong>Aggiungi SKU a: ${nodoPadre.nome}</strong></p>
                    <button id="btnSceltaNuovaSku">Nuova SKU</button>
                    <button id="btnSceltaSkuEsistente">SKU Esistente</button>
                    <button onclick="this.parentNode.style.display='none'">Annulla</button>
                    <div id="divDettagliAggiunta" style="margin-top: 10px;"></div>
                `;
                var areaDettagli = formArea.querySelector("#divDettagliAggiunta");

                // sku esistente dalla cache NASCONDE QUELLE GIA' PRESENTI)
                formArea.querySelector("#btnSceltaSkuEsistente").onclick = () => {
                    // FILTRO: Prendi dalla cache solo le sku che NON sono in nodoPadre.skus
                    let skuDisponibili = self.skusCache.filter(sku => !nodoPadre.skus.includes(sku.codice));

                    if (skuDisponibili.length === 0) {
                        areaDettagli.innerHTML = `<p style="color:red;">Tutte le SKU disponibili sono già associate a questo prodotto!</p>`;
                        return;
                    }

                    let checkboxesHTML = skuDisponibili.map(sku => 
                        `<label style="display:block;"><input type="checkbox" name="tmp_sku_add" value="${sku.codice}"> [${sku.codice}] ${sku.nome}</label>`
                    ).join("");

                    areaDettagli.innerHTML = `
                        <div style="height:80px; overflow-y:auto; border:1px solid #ccc; margin:5px 0; padding:3px;">
                            ${checkboxesHTML}
                        </div>
                        <button id="btnConfermaEsistente" style="background:green; color:white;">Aggiungi Selezionate</button>
                    `;
                    areaDettagli.querySelector("#btnConfermaEsistente").onclick = () => {
                        let selectedSkus = Array.from(areaDettagli.querySelectorAll("input[name='tmp_sku_add']:checked")).map(cb => parseInt(cb.value));
                        if(selectedSkus.length === 0) return;
                        
                        selectedSkus.forEach(s => { 
                            if(!nodoPadre.skus.includes(s)) nodoPadre.skus.push(s); 
                        });
                        self.disegnaAlberoVisivo();
                    };
                };

                // Nuova SKU (Controllo duplicati prima di crearla!)
                formArea.querySelector("#btnSceltaNuovaSku").onclick = () => {
                    areaDettagli.innerHTML = `
                        <input type="number" id="tmp_sku_codice" placeholder="Codice" style="width: 60px;" required>
                        <input type="text" id="tmp_sku_nome" placeholder="Nome" required>
                        <input type="text" id="tmp_sku_foto" placeholder="Foto URL" required>
                        <input type="number" id="tmp_sku_prezzo" placeholder="Prezzo" style="width: 60px;" required>
                        <input type="text" id="tmp_sku_desc" placeholder="Descrizione" required>
                        <button id="btnConfermaNuova" style="background:green; color:white;">Crea</button>
                    `;
                    areaDettagli.querySelector("#btnConfermaNuova").onclick = () => {
                        let nuovoCodice = parseInt(areaDettagli.querySelector("#tmp_sku_codice").value);
                        
                        // CONTROLLO DI SICUREZZA 1: Esiste già nel DB?
                        if (self.skusCache.some(s => s.codice === nuovoCodice)) {
                            alert("Errore: Esiste già una SKU nel catalogo con questo codice.");
                            return;
                        }
                        // CONTROLLO DI SICUREZZA 2: L'ho appena creata in questo nodo?
                        if (nodoPadre.nuoveSkus.some(s => s.codice === nuovoCodice)) {
                            alert("Errore: Hai già inserito una Nuova SKU con questo codice.");
                            return;
                        }

                        nodoPadre.nuoveSkus.push({
                            codice: nuovoCodice,
                            nome: areaDettagli.querySelector("#tmp_sku_nome").value,
                            foto: areaDettagli.querySelector("#tmp_sku_foto").value,
                            prezzo: areaDettagli.querySelector("#tmp_sku_prezzo").value,
                            descrizione: areaDettagli.querySelector("#tmp_sku_desc").value
                        });
                        self.disegnaAlberoVisivo();
                    };
                };
            }
        };

        // salva e manda al server
		// 4. L'EVENTO FINALE DEL BOTTONE ARANCIONE: SPEDIZIONE AL SERVER!
        document.getElementById("id_btnSalvaAlberoFinale").addEventListener('click', () => {
            if (!this.alberoCorrente) return;

            // VALIDAZIONE RIGOROSA DELLE SPECIFICHE PRIMA DI SPEDIRE
            const validaAlbero = (nodo) => {
                if (nodo.tipo === "COMPOSTO") {
                    if (!nodo.figli || nodo.figli.length < 1) {
                        alert(`ERRORE: Il Prodotto Composto [${nodo.codice}] "${nodo.nome}" deve avere almeno 1 sottoprodotto. Aggiungine altri o rimuovi questo nodo.`);
                        return false;
                    }
                    for (let figlio of nodo.figli) {
                        if (!validaAlbero(figlio)) return false;
                    }
                } else if (nodo.tipo === "SEMPLICE") {
                    let totaleSkus = (nodo.skus ? nodo.skus.length : 0) + (nodo.nuoveSkus ? nodo.nuoveSkus.length : 0);
                    if (totaleSkus < 1) {
                        alert(`ERRORE: Il Prodotto Semplice [${nodo.codice}] "${nodo.nome}" deve avere almeno 1 SKU.`);
                        return false;
                    }
                }
                return true;
            };

            // Se la validazione fallisce, fermiamo tutto!
            if (!validaAlbero(this.alberoCorrente)) {
                return;
            }
            
            // Impacchettiamo l'intero mostro in JSON
            var formData = new FormData();
            formData.append("alberoJSON", JSON.stringify(this.alberoCorrente));
            formData.append("daEliminareJSON", JSON.stringify(this.nodiDaEliminare));
			formData.append("relazioniDaEliminareJSON", JSON.stringify(this.relazioniDaEliminare)); 

            makeCall("POST", "SaveAlberoComposto", formData, (req) => {
                if (req.readyState === XMLHttpRequest.DONE) {
                    if (req.status === 200) {
                        this.alertContainer.textContent = "ALBERO SALVATO NEL DATABASE CON SUCCESSO!";
                        this.alertContainer.style.color = "green";
                        this.hide(); 
                        
                        // Per pulizia, ordiniamo anche un reset delle cache forzando un ricaricamento la prossima volta
                        this.loadProdottiDisponibili();
                    } else {
                        this.alertContainer.textContent = "Errore salvataggio: " + req.responseText;
                        this.alertContainer.style.color = "red";
                    }
                }
            });
        });
 	}   

    // orchestrator che coordina le varie cose
    function PageOrchestrator() {
        var alertGlobalId = "id_alertGlobal";

        // inizializziamo le varie sezioni della pag
        this.catalogo = new GestoreCatalogo("sezioneCatalogo", "id_tabellaProdottiBody", alertGlobalId);
        this.creazione = new GestoreCreazione("sezioneCreazione", alertGlobalId);

        this.start = function() {
			
            // controllo sicurezza lato client
            let sessionUtente = sessionStorage.getItem('utente');
            if (!sessionUtente) {
                window.location.href = "index.html"; // mandiamo a homepage
                return;
            }
            utenteLoggato = JSON.parse(sessionUtente);
            if (utenteLoggato.ruolo !== "FORNITORE") {
                window.location.href = "index.html"; // idem
                return;
            }

            // welcome message
            document.getElementById("id_welcomeMessage").textContent = "Utente: " + utenteLoggato.username;

            // mostrare il catalogo e nascondo il resto
            document.getElementById("id_btnShowCatalogo").addEventListener('click', () => {
                this.nascondiTutto();
                this.catalogo.show();
            });

			// idem per form creazione
			document.getElementById("id_btnShowCreazione").addEventListener('click', () => {
                this.nascondiTutto();
                this.creazione.show();
            });

			// qui direttamente toglie il valore da sessione e manda a home
			document.getElementById("id_logoutBtn").addEventListener('click', () => {
                makeCall("GET", "Logout", null, function(req) {
                    if (req.readyState === XMLHttpRequest.DONE) {
                        // puliamo sessione prima lato server con la call e poi lato client
                        sessionStorage.removeItem('utente');
                        window.location.href = "index.html";
                    }
                });
            });

			// di default mostriamo i form
            this.creazione.show();
        };

        // Funzione di servizio per spegnere tutte le viste
        this.nascondiTutto = function() {
            this.catalogo.hide();
            this.creazione.hide();
			document.getElementById("id_alertGlobal").textContent = ""; // pulisce eventuali messaggi di alert
        };
    }

    // appena carica la pagina inizializziamo l'orch e lo startiamo
    window.addEventListener("load", () => {
        orchestrator = new PageOrchestrator();
        orchestrator.start();
    });

})();