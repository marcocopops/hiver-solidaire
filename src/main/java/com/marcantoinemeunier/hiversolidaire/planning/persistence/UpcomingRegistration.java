package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import java.util.UUID;

/** Inscription à venir d'un bénévole, pour « Mes inscriptions ». */
public record UpcomingRegistration(UUID eveningId, LocalDate date, RegistrationType type, DishChoice dishChoice) {
}
