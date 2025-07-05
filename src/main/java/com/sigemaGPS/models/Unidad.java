package com.sigemaGPS.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Unidad {
    private Long id;
    private String nombre;
    private Double latitud;
    private Double longitud;
    // getters y setters
}