package com.sigemaGPS.models;

import java.io.Serializable;
import java.util.Date;

public class Posicion implements Serializable, Comparable<Posicion> {

    private Long id;
    private Long idEquipo;
    private double latitud;
    private double longitud;
    private Date fecha;
    private boolean fin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isFin() {
        return fin;
    }

    public void setFin(boolean fin) {
        this.fin = fin;
    }

    @Override
    public int compareTo(Posicion o) {
        return this.fecha.compareTo(o.getFecha());
    }
}
