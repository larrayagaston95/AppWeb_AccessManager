package com.atomg.accessmanager.dto;

/**
 * DTO de respuesta para el endpoint GET /api/dashboard/resumen.
 * Contiene los indicadores del dia actual para la vista Home del Dashboard.
 */
public record DashboardResumenDTO(
    long totalEmpleados,
    long presentes,
    long ausentes,
    long llegadasTarde
) {}