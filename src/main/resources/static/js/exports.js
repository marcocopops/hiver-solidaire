// Page des exports du back-office. Chargé après htmx (defer, dans l'ordre), donc avant son initialisation au
// DOMContentLoaded : les réglages ci-dessous s'appliquent dès le départ. Fichier statique : la CSP interdit le
// JavaScript inline.
(function () {
    'use strict';

    // Avec JavaScript, le tableau suit les dates : le bouton « Afficher » ne sert plus (CSS .js .sans-js).
    document.documentElement.classList.add('js');

    if (!window.htmx) {
        return;
    }
    // CSP (cahier des charges §8) : pas de <style> injecté, pas d'évaluation de code, pas de script dans les réponses.
    htmx.config.includeIndicatorStyles = false;
    htmx.config.allowEval = false;
    htmx.config.allowScriptTags = false;
    // Le tableau contient des coordonnées : aucune copie de page dans le stockage du navigateur (historique htmx).
    htmx.config.historyEnabled = false;
    htmx.config.historyCacheSize = 0;
    // Requêtes vers l'appli seulement (réglage par défaut, rendu explicite).
    htmx.config.selfRequestsOnly = true;
}());
