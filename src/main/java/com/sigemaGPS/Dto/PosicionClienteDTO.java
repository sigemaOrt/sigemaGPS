package com.sigemaGPS.Dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class PosicionClienteDTO {
    private double latitud;
    private double longitud;
    private String unidadMedida;
    private List<String> emails;
}