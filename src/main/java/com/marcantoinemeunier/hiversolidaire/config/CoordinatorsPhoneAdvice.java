package com.marcantoinemeunier.hiversolidaire.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Expose aux pages le téléphone des coordinateurs, ou rien s'il n'est pas configuré (message générique). */
@ControllerAdvice
class CoordinatorsPhoneAdvice {

    private final CoordinatorsPhone phone;

    CoordinatorsPhoneAdvice(@Value("${app.coordinators.phone:}") String phone) {
        this.phone = CoordinatorsPhone.parse(phone).orElse(null);
    }

    @ModelAttribute("coordinatorsPhone")
    CoordinatorsPhone coordinatorsPhone() {
        return phone;
    }
}
