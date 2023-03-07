
window.addEventListener("load", function () {
    var buttons = this.document.querySelectorAll(".panelbutton");
    buttons.forEach(button => {
        button.addEventListener("click", function () {
            display(this.id);
        });
    });
    document.getElementById("vectorize-sentence").addEventListener("input", function () {
        vectorize();
    });
    document.getElementById("vectorize-locale").addEventListener("input", function () {
        vectorize();
    });
    document.getElementById("nearest-locale").addEventListener("input", function () {
        nearest();
    });
    document.getElementById("nearest-term").addEventListener("input", function () {
        nearest();
    });
    fetch("./list").then((response) => response.json()).then((data) => {
        data.forEach(locale => {
            var node = document.createElement("li");
            var textnode = document.createTextNode(locale);
            node.appendChild(textnode);
            document.getElementById("locale-list").appendChild(node);

            node = document.createElement("option");
            textnode = document.createTextNode(locale);
            node.appendChild(textnode);
            document.getElementById("vectorize-locale").appendChild(node);

            node = document.createElement("option");
            textnode = document.createTextNode(locale);
            node.appendChild(textnode);
            document.getElementById("nearest-locale").appendChild(node);

        });
    });
});

function vectorize() {
    document.getElementById("vectorize-list").innerHTML = "";
    var locale = document.getElementById("vectorize-locale").value;
    var sentence = document.getElementById("vectorize-sentence").value;
    fetch("./" + locale + "/vectorize?text=" + sentence).then((response) => response.json()).then((data) => {
        console.log(data);
        document.getElementById("vectorize-list").innerHTML = "";
        data.forEach(vector => {
            var node = document.createElement("li");
            var textnode = document.createTextNode(vector);
            node.appendChild(textnode);
            document.getElementById("vectorize-list").appendChild(node);
        });
    });
}

function nearest() {
    document.getElementById("nearest-list").innerHTML = "";
    var locale = document.getElementById("nearest-locale").value;
    var term = document.getElementById("nearest-term").value;
    fetch("./" + locale + "/nearest/" + term).then((response) => response.json()).then((data) => {
        console.log(data);
        document.getElementById("nearest-list").innerHTML = "";
        data.forEach(term => {
            var node = document.createElement("li");
            var textnode = document.createTextNode(term);
            node.appendChild(textnode);
            document.getElementById("nearest-list").appendChild(node);
        });
    });
}

function display(panelName) {
    var panels = this.document.querySelectorAll(".middle");
    panels.forEach(panel => {
        panel.style.display = "none";
    });
    this.document.querySelector("#" + panelName + "-panel").style.display = "block";
}