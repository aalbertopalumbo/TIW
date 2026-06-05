/**
 * Login e Registrazione management
 */
(function() { // Evita che le variabili finiscano nello scope globale

    // gestione login
    document.getElementById("id_loginbutton").addEventListener('click', (e) => {
        var form = e.target.closest("form");
        
        if (form.checkValidity()) {
            makeCall("POST", 'CheckLogin', form, function(req) {
                if (req.readyState === XMLHttpRequest.DONE) {
                    var message = req.responseText;
                    var alertContainer = document.getElementById("id_alert");
                    
                    switch (req.status) {
                        case 200:
                            // salviamo in session storage l'intero JSON restituito (ruolo, username)
							var utente = JSON.parse(message);
							sessionStorage.setItem('utente', message);

							if (utente.ruolo === "FORNITORE") {
							    window.location.href = "homeFornitore.html";
							} else {
							    window.location.href = "homeCliente.html";
							}
                            break;
                        case 400: // bad request
                        case 401: // unauthorized
                        case 500: // server error
                            alertContainer.textContent = message;
                            alertContainer.style.color = "red";
                            break;
                    }
                }
            }, false); // se sbaglia password non resetta il form
        } else {
            form.reportValidity();
        }
    });

    // gestion registrazione
    document.getElementById("id_registerbutton").addEventListener('click', (e) => {
        var form = e.target.closest("form");
        
        if (form.checkValidity()) {
            makeCall("POST", 'CheckRegistrazione', form, function(req) {
                if (req.readyState === XMLHttpRequest.DONE) {
                    var message = req.responseText;
                    var alertContainer = document.getElementById("id_alert");
                    
                    switch (req.status) {
                        case 200: // ok
                            alertContainer.textContent = message;
                            alertContainer.style.color = "green";
                            break;
                        case 400: // dati mancanti o Utente esistente
                        case 500: // errore DB
                            alertContainer.textContent = message;
                            alertContainer.style.color = "red";
                            break;
                    }
                }
            }, true); // true: svuota il form di registrazione se va a buon fine
        } else {
            form.reportValidity();
        }
    });

})();