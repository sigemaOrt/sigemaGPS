package com.sigemaGPS.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PosicionClienteDTO {
    private double latitud;
    private double longitud;

    public String[] getEmails() {
        return emails;
    }
    public void setEmails(String[] emails) {
        this.emails = emails;
    }
    private String[] emails;
    public double getLatitud() {
        return latitud;
    }
    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }
    public double getLongitud() {
        return longitud;
    }
    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }
}