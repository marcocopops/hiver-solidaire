package com.marcantoinemeunier.hiversolidaire.benevole;

/** « Mon compte » : changement de pseudo, validé par {@link Pseudo#normalize(String)} comme au premier profil. */
public class PseudoForm {

    private String pseudo;

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }
}
