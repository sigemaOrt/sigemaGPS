package com.sigemaGPS.services.implementations;

import com.sigemaGPS.Dto.PosicionClienteDTO;
import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.models.enums.UnidadMedida;
import com.sigemaGPS.services.IJsonStorageService;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final IJsonStorageService jsonStorageService;
    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersEquipos = new ConcurrentHashMap<>();
    private final Map<Long, List<Posicion>> posicionesPorEquipo = new ConcurrentHashMap<>();

    @Value("${sigema.main.backend.url}")
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

        return new ReporteSigemaDTO(idEquipo, lat, lon, posicion.getFecha(), 0.0, 0.0, null, null);
    }

    @Override
    public ReporteSigemaDTO finalizarTrabajo(Long idEquipo, String jwtToken, PosicionClienteDTO ubicacion) throws Exception {
        System.out.println("Finalizando trabajo para equipo: " + idEquipo);

        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        double lat = ubicacion.getLatitud();
        double lon = ubicacion.getLongitud();

        Posicion posicionFinal = new Posicion();
        posicionFinal.setIdEquipo(idEquipo);
        posicionFinal.setLatitud(lat);
        posicionFinal.setLongitud(lon);
        posicionFinal.setFecha(new Date());
        posicionFinal.setFin(true);

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicionFinal);

        // Obtener equipo con información completa
        EquipoSigema equipo = null;
        try {
            String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);
            equipo = response.getBody();

            if (equipo != null && equipo.getModeloEquipo() != null && equipo.getModeloEquipo().getUnidadMedida() != null) {
                System.out.println("Equipo obtenido con unidad de medida: " + equipo.getModeloEquipo().getUnidadMedida());
            }

            // Log adicional para debugging
            if (equipo != null) {
                System.out.println("Equipo obtenido - ID: " + equipo.getId());
                System.out.println("Unidad del equipo: " + (equipo.getUnidad() != null ? equipo.getUnidad().getId() : "null"));
            }

        } catch (Exception e) {
            System.err.println("Error al obtener información del equipo para finalización: " + e.getMessage());
            logger.error("Error detallado al obtener equipo: ", e);
            throw new SigemaException("Error al obtener información del equipo: " + e.getMessage());
        }

        jsonStorageService.agregarPosicionAViaje(idEquipo, posicionFinal, equipo);
        setEnUso(idEquipo, false);
        jsonStorageService.finalizarViaje(idEquipo);

        ReporteFinViaje reporte = obtenerReporteViaje(idEquipo, LocalDate.now());

        // Extraer información del equipo
        UnidadMedida unidadMedida = null;
        Long idUnidad = null;
        if (equipo != null) {
            unidadMedida = equipo.getModeloEquipo() != null ? equipo.getModeloEquipo().getUnidadMedida() : null;
            idUnidad = equipo.getUnidad() != null ? equipo.getUnidad().getId() : null;
        }

        // Validar que tenemos idUnidad antes de crear el DTO
        if (idUnidad == null || idUnidad == 0) {
            throw new SigemaException("No se pudo obtener el ID de unidad del equipo. Verifique que el equipo tenga una unidad asignada.");
        }

        ReporteSigemaDTO dto;
        if (reporte != null && reporte.getUltimaPosicion() != null) {
            dto = new ReporteSigemaDTO(
                    idEquipo,
                    lat,
                    lon,
                    posicionFinal.getFecha(),
                    reporte.getTotalHoras(),
                    reporte.getTotalKMs(),
                    unidadMedida,
                    idUnidad  // Usar idUnidad validado
            );
        } else {
            dto = new ReporteSigemaDTO(
                    idEquipo,
                    lat,
                    lon,
                    posicionFinal.getFecha(),
                    0.0,
                    0.0,
                    unidadMedida,
                    idUnidad  // Usar idUnidad validado
            );
        }

        // Asegurar que el DTO tenga el idUnidad correcto
        dto.setUnidad(idUnidad);

        enviarReporteAlBackendPrincipal(dto, jwtToken, equipo);

        return dto;
    }


    private static final Logger logger = LoggerFactory.getLogger(PosicionService.class);

    private void enviarReporteAlBackendPrincipal(ReporteSigemaDTO dto, String jwtToken, EquipoSigema equipo) {
        try {
            logger.info("Datos enviados al backend principal:");
            logger.info("idEquipo: {}", dto.getIdEquipo());
            logger.info("latitud: {}", dto.getLatitud());
            logger.info("unidadMedida: {}", dto.getUnidadMedida());
            logger.info("idUnidad antes de validación: {}", dto.getUnidad());

            // Validar y establecer idUnidad ANTES de crear el reporte
            Long idUnidadFinal = dto.getUnidad();

            // Si idUnidad es nulo o cero, intentar obtenerlo del equipo
            if (idUnidadFinal == null || idUnidadFinal == 0) {
                if (equipo != null && equipo.getUnidad() != null && equipo.getUnidad().getId() != null) {
                    idUnidadFinal = equipo.getUnidad().getId();
                    logger.info("idUnidad obtenido del equipo: {}", idUnidadFinal);
                } else {
                    // Log detallado para debugging
                    logger.error("ERROR: No se pudo obtener idUnidad válido");
                    logger.error("dto.getUnidad(): {}", dto.getUnidad());
                    logger.error("equipo: {}", equipo);
                    if (equipo != null) {
                        logger.error("equipo.getUnidad(): {}", equipo.getUnidad());
                        if (equipo.getUnidad() != null) {
                            logger.error("equipo.getUnidad().getId(): {}", equipo.getUnidad().getId());
                        }
                    }
                    throw new SigemaException("El idUnidad no puede ser nulo o cero y no se pudo obtener del equipo");
                }
            }

            // Validación final
            if (idUnidadFinal == null || idUnidadFinal == 0) {
                throw new SigemaException("Debe asociar una unidad válida al equipo");
            }

            // Crear el reporte con el idUnidad validado
            ReporteSigemaDTO reporte = new ReporteSigemaDTO();
            reporte.setIdEquipo(dto.getIdEquipo());
            reporte.setLatitud(dto.getLatitud());
            reporte.setLongitud(dto.getLongitud());
            reporte.setFecha(dto.getFecha());
            reporte.setHorasDeTrabajo(dto.getHorasDeTrabajo());
            reporte.setKilometros(dto.getKilometros());
            reporte.setIdUnidad(idUnidadFinal); // Usar idUnidadFinal validado

            logger.info("idUnidad final asignado: {}", idUnidadFinal);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ReporteSigemaDTO> requestEntity = new HttpEntity<>(reporte, headers);

            String url = sigemaBackendUrl + "/api/reporte";

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            logger.info("Reporte enviado al backend principal. Estado: {}", response.getStatusCode());

        } catch (Exception e) {
            logger.error("Error al enviar reporte al backend principal: {}", e.getMessage());
            logger.error("Error detallado al enviar reporte: ", e);

            // Si es un error de validación, re-lanzar para que el controlador lo maneje
            if (e instanceof SigemaException) {
                throw (SigemaException) e;
            }
        }
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

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicion);
        estadoEquipos.put(idEquipo, true);

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
