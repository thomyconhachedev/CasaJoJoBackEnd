package com.casajojoapp.backend.source;

import com.casajojoapp.backend.exception.SincronizacionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class GoogleSheetsMenuCsvSource implements MenuCsvSource {

    private static final String BOM = "\uFEFF";

    private final RestClient restClient;
    private final String csvUrl;

    public GoogleSheetsMenuCsvSource(@Value("${menu.sheet.csv-url}") String csvUrl) {
        this.csvUrl = csvUrl;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String descargar() {
        byte[] bytes;
        try {
            bytes = restClient.get()
                    .uri(csvUrl)
                    .accept(MediaType.parseMediaType("text/csv"), MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new SincronizacionException(
                    "Google Sheets respondió HTTP " + e.getStatusCode().value() + " al descargar el CSV.", e);
        } catch (RestClientException e) {
            throw new SincronizacionException(
                    "No se pudo conectar con Google Sheets para descargar el CSV: " + e.getMessage(), e);
        }

        if (bytes == null || bytes.length == 0) {
            throw new SincronizacionException("El CSV llegó vacío desde Google Sheets.");
        }

        String contenido = new String(bytes, StandardCharsets.UTF_8);
        return contenido.startsWith(BOM) ? contenido.substring(1) : contenido;
    }
}
