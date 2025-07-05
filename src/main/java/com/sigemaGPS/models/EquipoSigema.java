package com.sigemaGPS.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sigemaGPS.models.enums.UnidadMedida;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipoSigema {
    private Long id;
    private String matricula;
    private String observaciones;
    private Double cantidadUnidadMedida;
    private Double latitud;
    private Double longitud;
    private Date fechaUltimaPosicion;
    private ModeloEquipo modeloEquipo;
    private String estado;
    private Unidad unidad;

}




