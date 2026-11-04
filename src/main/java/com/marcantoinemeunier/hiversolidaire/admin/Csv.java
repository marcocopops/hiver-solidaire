package com.marcantoinemeunier.hiversolidaire.admin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV des exports du back-office, pour un tableur : séparateur « ; » (réglage français d'Excel et de LibreOffice),
 * chaque cellule entre guillemets, guillemets doublés. Contre l'injection de formule (modèle de menaces), une cellule
 * qui commence par {@code =}, {@code +}, {@code -}, {@code @}, une tabulation ou un retour chariot est préfixée d'une
 * apostrophe : le tableur l'affiche comme du texte. Un téléphone {@code +33…} devient donc {@code '+33…}.
 */
final class Csv {

    static final String SEPARATOR = ";";
    /** Marque d'ordre des octets : Excel reconnaît l'UTF-8 (accents). */
    static final String BOM = "﻿";

    private Csv() {
    }

    static String cell(String value) {
        if (value == null) {
            return "\"\"";
        }
        var safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    static String row(List<String> values) {
        return values.stream().map(Csv::cell).collect(Collectors.joining(SEPARATOR)) + "\r\n";
    }
}
