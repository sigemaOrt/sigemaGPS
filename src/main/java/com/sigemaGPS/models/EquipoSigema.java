package com.sigemaGPS.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipoSigema {
    private Long id;
    private double latitud;
    private double longitud;
    private String unidadMedida;

}