// appsigemagps/src/main/java/com/sigemaGPS/models/dto/ReporteSigemaDTO.java
package com.sigemaGPS.Dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReporteSigemaDTO {
    private Long idEquipo;
    private double latitud;
    private double longitud;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Montevideo") // Ajusta el formato si es diferente en sigema
    private Date fecha;
    private double horasDeTrabajo;
    private double kilometros;


    public ReporteSigemaDTO() {}


    public ReporteSigemaDTO(Long idEquipo, double latitud, double longitud, Date fecha, double horasDeTrabajo, double kilometros) {
        this.idEquipo = idEquipo;
        this.latitud = latitud;
        this.longitud = longitud;
        this.fecha = fecha;
        this.horasDeTrabajo = horasDeTrabajo;
        this.kilometros = kilometros;
    }

    public Long getIdEquipo() {
        return idEquipo;
    }

    public void setIdEquipo(Long idEquipo) {
        this.idEquipo = idEquipo;
    }
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
    public Date getFecha() {
        return fecha;
    }
    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }
    public double getHorasDeTrabajo() {
        return horasDeTrabajo;
    }
    public void setHorasDeTrabajo(double horasDeTrabajo) {
        this.horasDeTrabajo = horasDeTrabajo;
    }
    public double getKilometros() {
        return kilometros;
    }
    public void setKilometros(double kilometros) {
        this.kilometros = kilometros;
    }


}