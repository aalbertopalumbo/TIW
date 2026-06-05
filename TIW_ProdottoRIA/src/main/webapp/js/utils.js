
function makeCall(method, url, formElement, cback, reset = true) {
    var req = new XMLHttpRequest(); // visible by closure
    
    req.onreadystatechange = function() {
      cback(req)
    }; // closure
    
    req.open(method, url);
    
    if (formElement == null) {
      req.send();
    } else if (formElement instanceof FormData) {
      // NOVITÀ: Se gli passiamo un FormData già pronto (come nell'inline editing), lo spedisce direttamente!
      req.send(formElement);
    } else {
      // ALTRIMENTI: Lo estrae dal tag <form> come ha sempre fatto
      req.send(new FormData(formElement));
    }
    
    // Attenzione: svuotiamo il form solo se è un vero tag HTML
    if (formElement !== null && reset === true && !(formElement instanceof FormData)) {
      formElement.reset();
    }
}