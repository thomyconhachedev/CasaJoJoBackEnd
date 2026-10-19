package com.example.CasaJoJoBackEnd.parser;

import com.example.CasaJoJoBackEnd.dto.ProductoImportado;
import com.example.CasaJoJoBackEnd.exception.SincronizacionException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MenuCsvParser {

    private static final String ID = "id";
    private static final String CATEGORIA = "categoria";
    private static final String NOMBRE = "nombre";
    private static final String DESCRIPCION = "descripcion";
    private static final String PRECIO = "precioextra";
    private static final String DISPONIBLE = "disponible";

    public record Resultado(List<ProductoImportado> productos, int filasIgnoradas) {
    }

    public Resultado parsear(String csvContent) {
        List<String[]> filas = leerFilas(csvContent);
        if (filas.isEmpty()) {
            throw new SincronizacionException("El CSV no contenía filas.");
        }

        Map<String, Integer> columnas = detectarColumnas(filas.get(0));
        boolean conEncabezado = columnas.containsKey(NOMBRE);
        if (!conEncabezado) {
            columnas = columnasPorDefecto();
        }
        int primeraFilaDatos = conEncabezado ? 1 : 0;

        List<ProductoImportado> productos = new ArrayList<>();
        Set<String> clavesVistas = new HashSet<>();
        int filasIgnoradas = 0;

        for (int i = primeraFilaDatos; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            if (filaEstaVacia(fila)) {
                continue;
            }

            String nombre = valor(fila, columnas, NOMBRE);
            if (nombre == null || nombre.isBlank()) {
                filasIgnoradas++;
                continue;
            }
            nombre = nombre.trim();

            String categoria = valor(fila, columnas, CATEGORIA);
            categoria = (categoria == null || categoria.isBlank()) ? "GENERAL" : categoria.trim().toUpperCase();

            String descripcion = valor(fila, columnas, DESCRIPCION);
            descripcion = descripcion == null ? "" : descripcion.trim();

            Long codigoExterno = parsearId(valor(fila, columnas, ID));
            int precioExtra = parsearPrecio(valor(fila, columnas, PRECIO));
            boolean disponible = parsearDisponible(valor(fila, columnas, DISPONIBLE));

            String clave = codigoExterno != null
                    ? "id:" + codigoExterno
                    : "nombre:" + normalizar(categoria) + "|" + normalizar(nombre);
            if (!clavesVistas.add(clave)) {
                filasIgnoradas++;
                continue;
            }

            productos.add(new ProductoImportado(
                    codigoExterno,
                    truncar(categoria, 50),
                    truncar(nombre, 100),
                    truncar(descripcion, 255),
                    precioExtra,
                    disponible));
        }

        if (productos.isEmpty()) {
            throw new SincronizacionException(
                    "El CSV no contenía ningún producto válido (" + filasIgnoradas
                            + " filas ignoradas). No se modifica la base de datos.");
        }

        return new Resultado(productos, filasIgnoradas);
    }

    private List<String[]> leerFilas(String csvContent) {
        try (CSVReader reader = new CSVReader(new StringReader(csvContent))) {
            return reader.readAll();
        } catch (CsvException | IOException e) {
            throw new SincronizacionException("Error leyendo el CSV: " + e.getMessage(), e);
        }
    }

    private Map<String, Integer> detectarColumnas(String[] encabezado) {
        Map<String, Integer> columnas = new HashMap<>();
        for (int i = 0; i < encabezado.length; i++) {
            String titulo = normalizar(encabezado[i]);
            if (titulo.isEmpty()) {
                continue;
            }
            if (titulo.equals("id") || titulo.equals("codigo") || titulo.equals("codigoexterno")) {
                columnas.putIfAbsent(ID, i);
            } else if (titulo.equals("categoria")) {
                columnas.putIfAbsent(CATEGORIA, i);
            } else if (titulo.equals("nombre") || titulo.equals("producto")) {
                columnas.putIfAbsent(NOMBRE, i);
            } else if (titulo.equals("descripcion") || titulo.equals("detalle")) {
                columnas.putIfAbsent(DESCRIPCION, i);
            } else if (titulo.startsWith("precio")) {
                columnas.putIfAbsent(PRECIO, i);
            } else if (titulo.equals("disponible") || titulo.equals("estado") || titulo.equals("activo")) {
                columnas.putIfAbsent(DISPONIBLE, i);
            }
        }
        return columnas;
    }

    private Map<String, Integer> columnasPorDefecto() {
        Map<String, Integer> columnas = new HashMap<>();
        columnas.put(ID, 0);
        columnas.put(CATEGORIA, 1);
        columnas.put(NOMBRE, 2);
        columnas.put(DESCRIPCION, 3);
        columnas.put(PRECIO, 4);
        columnas.put(DISPONIBLE, 5);
        return columnas;
    }

    private String valor(String[] fila, Map<String, Integer> columnas, String columna) {
        Integer indice = columnas.get(columna);
        if (indice == null || indice >= fila.length) {
            return null;
        }
        return fila[indice];
    }

    private boolean filaEstaVacia(String[] fila) {
        for (String celda : fila) {
            if (celda != null && !celda.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Long parsearId(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String soloDigitos = valor.trim().replaceAll("[^0-9]", "");
        return soloDigitos.isEmpty() ? null : Long.parseLong(soloDigitos);
    }

    private int parsearPrecio(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0;
        }
        String soloDigitos = valor.trim().replaceAll("[^0-9]", "");
        return soloDigitos.isEmpty() ? 0 : Integer.parseInt(soloDigitos);
    }

    private boolean parsearDisponible(String valor) {
        if (valor == null || valor.isBlank()) {
            return true;
        }
        String v = valor.trim().toLowerCase();
        return !(v.equals("false") || v.equals("falso") || v.equals("no") || v.equals("0"));
    }

    private String truncar(String valor, int max) {
        if (valor == null) {
            return "";
        }
        return valor.length() > max ? valor.substring(0, max) : valor;
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
