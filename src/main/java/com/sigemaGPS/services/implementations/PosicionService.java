package com.sigemaGPS.services.implementations;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PosicionService implements IPosicionService {

    private final RestTemplate restTemplate;
    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersEquipos = new ConcurrentHashMap<>();
    private final Map<Long, List<Posicion>> posicionesPorEquipo = new ConcurrentHashMap<>();

    //  Verificar que la propiedad se inyecte correctamente
    @Value("${sigema.backend.url}")
    private String sigemaBackendUrl;

    public PosicionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Agregar método para verificar que la propiedad se cargó
    @PostConstruct
    public void init() {
        System.out.println("=== CONFIGURACIÓN CARGADA ===");
        System.out.println("sigema.backend.url = " + sigemaBackendUrl);
        if (sigemaBackendUrl == null || sigemaBackendUrl.isEmpty()) {
            throw new RuntimeException("La propiedad sigema.backend.url no está configurada correctamente");
        }
    }

    @Override
    public void iniciarTrabajo(Long idEquipo, String jwtToken) throws Exception {
        System.out.println("Iniciando trabajo para equipo: " + idEquipo + " con token: " +
                (jwtToken != null ? jwtToken.substring(0, Math.min(10, jwtToken.length())) + "..." : "null"));

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
                }
            }
        }, 15 * 60 * 1000, 15 * 60 * 1000);
    }

    @Override
    public void finalizarTrabajo(Long idEquipo, String jwtToken) throws Exception {
        System.out.println("Finalizando trabajo para equipo: " + idEquipo);

        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        registrarPosicion(idEquipo, true, jwtToken);
        setEnUso(idEquipo, false);

        ReporteFinViaje reporte = obtenerReporteViaje(idEquipo, LocalDate.now());
        if (reporte != null && reporte.getUltimaPosicion() != null) {
            ReporteSigemaDTO dto = new ReporteSigemaDTO(
                    idEquipo,
                    reporte.getUltimaPosicion().getLatitud(),
                    reporte.getUltimaPosicion().getLongitud(),
                    reporte.getUltimaPosicion().getFecha(),
                    reporte.getTotalHoras(),
                    reporte.getTotalKMs()
            );
            enviarReporteASigema(dto, jwtToken);
        }
    }

    @Override
    public void eliminarTrabajo(Long idEquipo) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();
        setEnUso(idEquipo, false);
    }

    private void registrarPosicion(Long idEquipo, boolean fin, String jwtToken) throws Exception {
        String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
        System.out.println("Registrando posición en URL: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        double lat;
        double lon;

        try {
            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);
            EquipoSigema equipo = response.getBody();

            if (equipo == null) throw new SigemaException("Equipo no encontrado");
            lat = equipo.getLatitud();
            lon = equipo.getLongitud();

            System.out.println("Posición obtenida - Lat: " + lat + ", Lon: " + lon);
        } catch (Exception e) {
            System.err.println("Error al obtener posición del equipo: " + e.getMessage());
            throw new SigemaException("Error al obtener la posición del equipo: " + e.getMessage());
        }

        Posicion posicion = new Posicion();
        posicion.setIdEquipo(idEquipo);
        posicion.setLatitud(lat);
        posicion.setLongitud(lon);
        posicion.setFecha(new Date());
        posicion.setFin(fin);

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicion);
        estadoEquipos.put(idEquipo, true);
    }

    private void enviarReporteASigema(ReporteSigemaDTO reporte, String jwtToken) throws SigemaException {
        try {
            String url = sigemaBackendUrl + "/api/reporte";
            System.out.println("Enviando reporte a URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ReporteSigemaDTO> request = new HttpEntity<>(reporte, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);

            System.out.println("Reporte enviado exitosamente");
        } catch (Exception e) {
            System.err.println("Error al enviar reporte: " + e.getMessage());
            throw new SigemaException("Error al enviar reporte: " + e.getMessage());
        }
    }

    @Override
    public List<Posicion> obtenerTodasPorIdEquipo(Long idEquipo, LocalDate fecha) {
        List<Posicion> todas = posicionesPorEquipo.getOrDefault(idEquipo, new ArrayList<>());
        List<Posicion> filtradas = new ArrayList<>();

        for (Posicion p : todas) {
            if (p.getFecha().toInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate().equals(fecha)) {
                filtradas.add(p);
            }
        }

        filtradas.sort(Comparator.naturalOrder());
        return filtradas;
    }

    @Override
    public ReporteFinViaje obtenerReporteViaje(Long idEquipo, LocalDate fecha) throws Exception {
        List<Posicion> posiciones = obtenerTodasPorIdEquipo(idEquipo, fecha);
        if (posiciones.size() < 2) return new ReporteFinViaje(null, fecha, idEquipo, 0, 0);

        double totalKm = 0;
        long tiempoUso = 0;
        Posicion anterior = posiciones.get(0);

        for (int i = 1; i < posiciones.size(); i++) {
            Posicion actual = posiciones.get(i);
            double distancia = calcularDistanciaKm(anterior.getLatitud(), anterior.getLongitud(), actual.getLatitud(), actual.getLongitud());
            long tiempo = actual.getFecha().getTime() - anterior.getFecha().getTime();

            if (distancia >= 0.01 || tiempo < 30 * 60 * 1000) {
                totalKm += distancia;
                tiempoUso += tiempo;
            }

            anterior = actual;
        }

        double horas = tiempoUso / (1000.0 * 60 * 60);
        return new ReporteFinViaje(posiciones.get(posiciones.size() - 1), fecha, idEquipo, horas, totalKm);
    }

    @Override
    public boolean estaEnUso(Long idEquipo) {
        if (!estadoEquipos.getOrDefault(idEquipo, false)) return false;
        List<Posicion> posiciones = posicionesPorEquipo.getOrDefault(idEquipo, new ArrayList<>());
        if (posiciones.isEmpty()) return false;

        Posicion ultima = posiciones.get(posiciones.size() - 1);
        long diff = new Date().getTime() - ultima.getFecha().getTime();
        boolean enUso = diff < 15 * 60 * 1000;
        estadoEquipos.put(idEquipo, enUso);
        return enUso;
    }

    @Override
    public void setEnUso(Long idEquipo, boolean enUso) {
        estadoEquipos.put(idEquipo, enUso);
    }

    private double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}