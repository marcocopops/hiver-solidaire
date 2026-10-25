// Formulaire de première connexion : propose un pseudo « prénom + initiale du nom » pendant la saisie, tant que
// le bénévole ne l'a pas modifié lui-même. Même calcul que benevole.Pseudo.suggest côté serveur, qui reste seul
// juge de la validité et de la disponibilité du pseudo. Fichier statique : la CSP interdit le JavaScript inline.
(function () {
    'use strict';

    var MIN_LENGTH = 3;
    var MAX_LENGTH = 20;
    // Même liste que benevole.Pseudo : sans ı, ĸ, Ŀ, ŀ, ŉ, ſ, qui imitent une lettre ASCII.
    var NOT_ALLOWED = /[^A-Za-zÀ-ÖØ-öø-ÿĀ-İĲ-ķĹ-ľŁ-ňŊ-ž0-9_-]/g;
    var FALLBACK = 'Benevole';

    var firstName = document.getElementById('firstName');
    var lastName = document.getElementById('lastName');
    var pseudo = document.getElementById('pseudo');
    if (!firstName || !lastName || !pseudo) {
        return;
    }

    function clean(value) {
        return (value || '').normalize('NFC').replace(NOT_ALLOWED, '').replace(/^[_-]+/, '');
    }

    function suggest(first, last) {
        var cleanFirst = clean(first) || FALLBACK;
        var cleanLast = clean(last);
        var initial = cleanLast ? cleanLast.charAt(0).toUpperCase() : '';
        if (cleanFirst.length + initial.length > MAX_LENGTH) {
            cleanFirst = cleanFirst.substring(0, MAX_LENGTH - initial.length);
        }
        var suggestion = cleanFirst + initial;
        while (suggestion.length < MIN_LENGTH) {
            suggestion += '1';
        }
        return suggestion;
    }

    function namesTyped() {
        return firstName.value.trim() !== '' || lastName.value.trim() !== '';
    }

    // Un pseudo déjà présent et différent de la suggestion (choisi, ou variante proposée par le serveur) est gardé.
    var editedByHand = pseudo.value !== '' && pseudo.value !== suggest(firstName.value, lastName.value);

    function update() {
        if (!editedByHand) {
            pseudo.value = namesTyped() ? suggest(firstName.value, lastName.value) : '';
        }
    }

    pseudo.addEventListener('input', function () {
        // Champ vidé : la suggestion automatique reprend.
        editedByHand = pseudo.value !== '';
    });
    firstName.addEventListener('input', update);
    lastName.addEventListener('input', update);
    update();
}());
