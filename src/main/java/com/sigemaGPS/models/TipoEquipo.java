package com.sigemaGPS.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TipoEquipo {
    private Long id;
    private String codigo;
    private String nombre;
    private Boolean activo;
    private String tarea;
    // getters y setters
}
