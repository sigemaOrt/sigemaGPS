package com.sigemaGPS.services;


public interface IEmailService {
    void enviarCorreoFinalizacionTrabajo(Long idEquipo, String destinatario, String asunto, String cuerpo);
}
