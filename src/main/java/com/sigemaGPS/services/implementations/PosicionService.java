package com.sigemaGPS.services.implementations;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.repositories.IPosicionRepository;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class PosicionService implements IPosicionService {

    private final IPosicionRepository posicionRepository;

    // Estados y timers de equipos
    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersEquipos = new ConcurrentHashMap<>();

    public PosicionService(IPosicionRepository posicionRepository){
        this.posicionRepository = posicionRepository;
    }

    @Override
    public void iniciarTrabajo(Long idEquipo) throws Exception {
        registrarPosicion(idEquipo, false);
        setEnUso(idEquipo, true);

        Timer timer = new Timer(true);
        timersEquipos.put(idEquipo, timer);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    registrarPosicion(idEquipo, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 15 * 60 * 1000, 15 * 60 * 1000); // cada 15 minutos
    }

    @Override
    public void finalizarTrabajo(Long idEquipo) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        registrarPosicion(idEquipo, true);
        setEnUso(idEquipo, false);
    }

    @Override
    public void eliminarTrabajo(Long idEquipo) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        setEnUso(idEquipo, false);
    }

    private void registrarPosicion(Long idEquipo, boolean fin) throws Exception {
        if (idEquipo == null || idEquipo <= 0)
            throw new SigemaException("Debe ingresar un equipo");

        double lat = 0;
        double lon = 0;

        if (lat == 0 || lon == 0)
            throw new SigemaException("Debe ingresar la latitud y longitud");

        Posicion posicion = new Posicion();
        posicion.setIdEquipo(idEquipo);
        posicion.setLatitud(lat);
        posicion.setLongitud(lon);
        posicion.setFin(fin);
        posicion.setFecha(new Date());

        posicionRepository.save(posicion);
        estadoEquipos.put(idEquipo, true);
    }

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
