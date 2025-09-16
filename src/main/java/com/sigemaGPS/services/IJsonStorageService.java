package com.sigemaGPS.services;

import com.sigemaGPS.Dto.ReporteSigemaDTO;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface IJsonStorageService {
    void iniciarViaje(Long idEquipo, Posicion posicion);
    void agregarPosicionAViaje(Long idEquipo, Posicion posicion);
    void finalizarViaje(Long idEquipo);
    void finalizarViajeConCalculo(Long idEquipo, double valorCalculado, ReporteFinViaje reporte);
    Map<String, Object> obtenerDatosViajeParaReporte(Long idEquipo, String fechaHora);
    void guardarPosicionEnJSON(Posicion posicion);
    void guardarReporteEnJSON(ReporteSigemaDTO reporte, String tipo);
    void guardarRequestEnJSON(ReporteSigemaDTO reporte, String url, String tipo);
    void guardarResponseEnJSON(ResponseEntity<String> response, String tipo);
}