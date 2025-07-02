package com.sigemaGPS.services.implementations;

import com.sigemaGPS.Dto.PosicionClienteDTO;
import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.services.IJsonStorageService;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PosicionService implements IPosicionService {

    private final RestTemplate restTemplate;
    private final IJsonStorageService jsonStorageService;
    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersEquipos = new ConcurrentHashMap<>();
    private final Map<Long, List<Posicion>> posicionesPorEquipo = new ConcurrentHashMap<>();

    @Value("${sigema.backend.url}")
    private String sigemaBackendUrl;

    @Autowired
    public PosicionService(RestTemplate restTemplate, IJsonStorageService jsonStorageService) {
        this.restTemplate = restTemplate;
        this.jsonStorageService = jsonStorageService;
    }

    @PostConstruct
    public void init() {
        System.out.println("=== CONFIGURACIÓN CARGADA ===");
        System.out.println("sigema.backend.url = " + sigemaBackendUrl);
        if (sigemaBackendUrl == null || sigemaBackendUrl.isEmpty()) {
            throw new RuntimeException("La propiedad sigema.backend.url no está configurada correctamente");
        }
    }

    @Override
    public ReporteSigemaDTO iniciarTrabajo(Long idEquipo, String jwtToken, PosicionClienteDTO ubicacion) throws Exception {
        System.out.println("Iniciando trabajo para equipo: " + idEquipo);

        double lat = ubicacion.getLatitud();
        double lon = ubicacion.getLongitud();

        Posicion posicion = new Posicion();
        posicion.setIdEquipo(idEquipo);
        posicion.setLatitud(lat);
        posicion.setLongitud(lon);
        posicion.setFecha(new Date());
        posicion.setFin(false);

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicion);
        setEnUso(idEquipo, true);

        jsonStorageService.iniciarViaje(idEquipo, posicion, null);

        Timer timer = new Timer(true);
        timersEquipos.put(idEquipo, timer);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    registrarPosicionConJSON(idEquipo, false, jwtToken, false);
                } catch (Exception e) {
                    System.err.println("Error al registrar posición periódica para equipo " + idEquipo + ": " + e.getMessage());
                }
            }
        }, 15 * 60 * 1000, 15 * 60 * 1000);

        return new ReporteSigemaDTO(idEquipo, lat, lon, posicion.getFecha(), 0.0, 0.0);
    }


    @Override
    public ReporteSigemaDTO finalizarTrabajo(Long idEquipo, String jwtToken, PosicionClienteDTO ubicacion) throws Exception {
        System.out.println("Finalizando trabajo para equipo: " + idEquipo);

        // Cancelar timer
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        // Crear la posición final con la ubicación del cliente
        double lat = ubicacion.getLatitud();
        double lon = ubicacion.getLongitud();

        Posicion posicionFinal = new Posicion();
        posicionFinal.setIdEquipo(idEquipo);
        posicionFinal.setLatitud(lat);
        posicionFinal.setLongitud(lon);
        posicionFinal.setFecha(new Date());
        posicionFinal.setFin(true);

        // Agregar la posición final a la lista en memoria
        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicionFinal);

        // Obtener información del equipo para el JSON
        EquipoSigema equipo = null;
        try {
            String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);
            equipo = response.getBody();
        } catch (Exception e) {
            System.err.println("Error al obtener información del equipo para finalización: " + e.getMessage());
            // Continuamos sin la información del equipo
        }

        // Guardar la posición final en el JSON
        jsonStorageService.agregarPosicionAViaje(idEquipo, posicionFinal, equipo);

        setEnUso(idEquipo, false);

        // Finalizar archivo JSON (renombrar de "enCurso" a "finalizado")
        jsonStorageService.finalizarViaje(idEquipo);

        // Generar reporte para devolver
        ReporteFinViaje reporte = obtenerReporteViaje(idEquipo, LocalDate.now());
        if (reporte != null && reporte.getUltimaPosicion() != null) {
            return new ReporteSigemaDTO(
                    idEquipo,
                    lat, // Usar la posición del cliente
                    lon, // Usar la posición del cliente
                    posicionFinal.getFecha(),
                    reporte.getTotalHoras(),
                    reporte.getTotalKMs()
            );
        }

        // Si no hay reporte, devolver al menos la posición final
        return new ReporteSigemaDTO(idEquipo, lat, lon, posicionFinal.getFecha(), 0.0, 0.0);
    }

    @Override
    public void eliminarTrabajo(Long idEquipo) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();
        setEnUso(idEquipo, false);
    }

    private void registrarPosicionConJSON(Long idEquipo, boolean fin, String jwtToken, boolean inicioViaje) throws Exception {
        String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
        System.out.println("Registrando posición en URL: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        double lat;
        double lon;
        EquipoSigema equipo = null;

        try {
            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);
            equipo = response.getBody();

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

        // Guardar en memoria
        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicion);
        estadoEquipos.put(idEquipo, true);

        // Guardar en JSON según la nomenclatura requerida
        if (inicioViaje) {
            jsonStorageService.iniciarViaje(idEquipo, posicion, equipo);
        } else {
            jsonStorageService.agregarPosicionAViaje(idEquipo, posicion, equipo);
        }
    }

    // Resto de métodos sin cambios...
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