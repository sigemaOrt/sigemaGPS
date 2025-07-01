package com.sigemaGPS.services;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import org.springframework.http.ResponseEntity;

public interface IJsonStorageService {

    // Métodos originales
    void guardarPosicionEnJSON(Posicion posicion, EquipoSigema equipoInfo);
    void guardarReporteEnJSON(ReporteSigemaDTO reporte, String tipo);
    void guardarRequestEnJSON(ReporteSigemaDTO reporte, String url, String tipo);
    void guardarResponseEnJSON(ResponseEntity<String> response, String tipo);

    // Nuevos métodos para manejo de viajes
    void iniciarViaje(Long idEquipo, Posicion posicion, EquipoSigema equipoInfo);
    void agregarPosicionAViaje(Long idEquipo, Posicion posicion, EquipoSigema equipoInfo);
    void finalizarViaje(Long idEquipo);
}