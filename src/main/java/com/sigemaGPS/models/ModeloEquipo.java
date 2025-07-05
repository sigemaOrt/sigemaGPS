package com.sigemaGPS.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sigemaGPS.models.enums.UnidadMedida;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModeloEquipo {
    private Long id;
    private Integer anio;
    private String modelo;
    private Double capacidad;
    private Marca marca;
    private TipoEquipo tipoEquipo;
    private UnidadMedida unidadMedida;  // aquí está la unidad de medida

    // getters y setters
}