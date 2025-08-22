package com.sigemaGPS.Dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class PosicionClienteDTO {
    private Double latitud;
    private Double longitud;
    private List<String> emails;
}