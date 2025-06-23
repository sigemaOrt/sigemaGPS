// appsigemagps/src/main/java/com/sigemaGPS/services/implementations/PosicionService.java
package com.sigemaGPS.services.implementations;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.repositories.IPosicionRepository;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO; // Importar el nuevo DTO
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus; // Para verificar el estado HTTP de la respuesta
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class PosicionService implements IPosicionService {

    private final IPosicionRepository posicionRepository;
    private final RestTemplate restTemplate;

    @Value("${sigema.backend.url}")
    private String sigemaBackendUrl;

    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersEquipos = new ConcurrentHashMap<>();

    public PosicionService(IPosicionRepository posicionRepository, RestTemplate restTemplate){
        this.posicionRepository = posicionRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public void iniciarTrabajo(Long idEquipo, String jwtToken) throws Exception {
        registrarPosicion(idEquipo, false, jwtToken);
        setEnUso(idEquipo, true);

        Timer timer = new Timer(true);
        timersEquipos.put(idEquipo, timer);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    registrarPosicion(idEquipo, false, jwtToken);
                } catch (Exception e) {
                    System.err.println("Error al registrar posición periódica para equipo " + idEquipo + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 15 * 60 * 1000, 15 * 60 * 1000);
    }

    @Override
    public void finalizarTrabajo(Long idEquipo, String jwtToken) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        // Registramos la última posición
        registrarPosicion(idEquipo, true, jwtToken);
        setEnUso(idEquipo, false);

        try {
            // 1. Obtener el reporte de viaje calculado
            // Si la fecha del reporte es la actual o la fecha de inicio del trabajo.
            // Para simplicidad, usaremos la fecha actual para el cálculo.
            // En un sistema real, querrías calcular el reporte para el día en que inició el trabajo
            // o el período que abarcó.
            ReporteFinViaje reporteCalculado = obtenerReporteViaje(idEquipo, LocalDate.now());

            // 2. Mapear el ReporteFinViaje interno a un ReporteSigemaDTO
            if (reporteCalculado != null && reporteCalculado.getUltimaPosicion() != null) {
                ReporteSigemaDTO reporteParaSigema = new ReporteSigemaDTO(
                        reporteCalculado.getIdEquipo(),
                        reporteCalculado.getUltimaPosicion().getLatitud(),
                        reporteCalculado.getUltimaPosicion().getLongitud(),
                        reporteCalculado.getUltimaPosicion().getFecha(), // Usamos la fecha de la última posición
                        reporteCalculado.getTotalHoras(),
                        reporteCalculado.getTotalKMs()
                );

                // 3. Enviar el DTO del reporte a Sigema
                enviarReporteASigema(reporteParaSigema, jwtToken);
                System.out.println("Reporte de fin de viaje enviado a Sigema para equipo " + idEquipo);
            } else {
                System.out.println("No se generó un reporte válido para enviar a Sigema para el equipo " + idEquipo);
            }
        } catch (Exception e) {
            System.err.println("Error al enviar el reporte a Sigema al finalizar el trabajo para el equipo " + idEquipo + ": " + e.getMessage());
        }

    }

    @Override
    public void eliminarTrabajo(Long idEquipo) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        setEnUso(idEquipo, false);
    }

    private void registrarPosicion(Long idEquipo, boolean fin, String jwtToken) throws Exception {
        if (idEquipo == null || idEquipo <= 0) {
            throw new SigemaException("Debe ingresar un equipo válido (ID mayor a 0).");
        }

        double lat;
        double lon;

        try {
            String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                EquipoSigema equipoSigema = response.getBody();
                lat = equipoSigema.getLatitud();
                lon = equipoSigema.getLongitud();
            } else {
                throw new SigemaException("No se pudo obtener la posición del equipo " + idEquipo + " desde Sigema. Código de estado: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            String errorMessage = "Error al comunicarse con el backend Sigema para el equipo " + idEquipo + ". ";
            switch (e.getStatusCode().value()) {
                case 401:
                    errorMessage += "Acceso no autorizado. El token JWT podría ser inválido o expirar.";
                    break;
                case 404:
                    errorMessage += "Equipo no encontrado en Sigema.";
                    break;
                default:
                    errorMessage += "Estado HTTP: " + e.getStatusCode().value() + ". Mensaje: " + e.getMessage();
                    break;
            }
            throw new SigemaException(errorMessage);
        } catch (Exception e) {
            throw new SigemaException("Error inesperado al obtener la posición del equipo " + idEquipo + " de Sigema: " + e.getMessage());
        }

        if (lat == 0 && lon == 0) {
            throw new SigemaException("No se pudo obtener una latitud y longitud válidas para el equipo " + idEquipo + " de Sigema.");
        }

        Posicion posicion = new Posicion();
        posicion.setIdEquipo(idEquipo);
        posicion.setLatitud(lat);
        posicion.setLongitud(lon);
        posicion.setFin(fin);
        posicion.setFecha(new Date());

        posicionRepository.save(posicion);
        estadoEquipos.put(idEquipo, true);
    }

    /**
     * Envía un reporte de trabajo al backend Sigema.
     * @param reporte El DTO del reporte a enviar.
     * @param jwtToken El token JWT para autenticarse con el backend Sigema.
     * @throws SigemaException Si ocurre un error al enviar el reporte.
     */
    private void enviarReporteASigema(ReporteSigemaDTO reporte, String jwtToken) throws SigemaException {
        try {
            String url = sigemaBackendUrl + "/api/reporte"; // Endpoint de creación de reportes en Sigema

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json"); // Asegurar que el tipo de contenido sea JSON
            HttpEntity<ReporteSigemaDTO> requestEntity = new HttpEntity<>(reporte, headers);

            // Realizar la petición POST
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new SigemaException("Fallo al enviar el reporte a Sigema. Código de estado: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            String errorMessage = "Error de cliente HTTP al enviar reporte a Sigema: " + e.getStatusCode().value();
            if (e.getMessage() != null && !e.getMessage() .isEmpty()) {
                errorMessage += ". Detalles: " + e.getMessage() ;
            }
            throw new SigemaException(errorMessage);
        } catch (Exception e) {
            throw new SigemaException("Error inesperado al enviar el reporte a Sigema: " + e.getMessage());
        }
    }

    // --- Métodos existentes (obtenerTodasPorIdEquipo, obtenerReporteViaje, estaEnUso, setEnUso, calcularDistanciaKm) ---
    // No necesitan cambios, ya que los incluyes en tu pregunta.
    // Solo asegúrate de que el ReporteFinViaje se mapee correctamente al ReporteSigemaDTO.

    @Override
    public List<Posicion> obtenerTodasPorIdEquipo(Long idEquipo, LocalDate fecha) throws Exception {
        List<Posicion> posiciones = posicionRepository.findByIdEquipoAndFecha(idEquipo, fecha).orElse(new ArrayList<>());
        posiciones.sort(Comparator.naturalOrder());
        return posiciones;
    }


    @Override
    public ReporteFinViaje obtenerReporteViaje(Long idEquipo, LocalDate fecha) throws Exception {
        List<Posicion> posiciones = obtenerTodasPorIdEquipo(idEquipo, fecha);
        if (posiciones.size() < 2) {
            return new ReporteFinViaje(null, fecha, idEquipo, 0, 0);
        }

        double totalKm = 0;
        long tiempoUsoMillis = 0;

        Posicion anterior = posiciones.getFirst();

        for (int i = 1; i < posiciones.size(); i++) {
            Posicion actual = posiciones.get(i);
            double distancia = calcularDistanciaKm(
                    anterior.getLatitud(), anterior.getLongitud(),
                    actual.getLatitud(), actual.getLongitud());

            long tiempoEntrePuntos = actual.getFecha().getTime() - anterior.getFecha().getTime();

            if (distancia >= 0.01 || tiempoEntrePuntos < 30 * 60 * 1000) {
                totalKm += distancia;
                tiempoUsoMillis += tiempoEntrePuntos;
            }

            anterior = actual;
        }

        double horasUso = tiempoUsoMillis / (1000.0 * 60 * 60);

        return new ReporteFinViaje(
                posiciones.getLast(),
                fecha,
                idEquipo,
                horasUso,
                totalKm
        );
    }

    @Override
    public boolean estaEnUso(Long idEquipo) throws Exception {
        if (!estadoEquipos.getOrDefault(idEquipo, false)) {
            return false;
        }

        Posicion ultimaPosicion = posicionRepository.findFirstByIdEquipoOrderByFechaDesc(idEquipo).orElse(null);
        if (ultimaPosicion == null) {
            estadoEquipos.put(idEquipo, false);
            return false;
        }

        long diferenciaMillis = new Date().getTime() - ultimaPosicion.getFecha().getTime();
        boolean enUso = diferenciaMillis < 15 * 60 * 1000;

        estadoEquipos.put(idEquipo, enUso);
        return enUso;
    }

    @Override
    public void setEnUso(Long idEquipo, boolean enUso) throws Exception {
        estadoEquipos.put(idEquipo, enUso);
    }

    private double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}