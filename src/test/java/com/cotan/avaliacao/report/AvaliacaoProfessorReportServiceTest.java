package com.cotan.avaliacao.report;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvaliacaoProfessorReportServiceTest {

    @Test
    void shouldGenerateDocxReport() throws Exception {
        Path output = Files.createTempFile("ficha-avaliacao-professor", ".docx");

        AvaliacaoProfessorRelatorio relatorio = new AvaliacaoProfessorRelatorio(
                "ADELINA PINTO M. DA COSTA",
                "Prof. Do Ens. Prim. E Sec. Do 6º Grau",
                "88014346",
                LocalDate.of(2023, 1, 5),
                LocalDate.of(2022, 9, 6),
                LocalDate.of(2022, 12, 30),
                "12",
                "Suficiente",
                "Bom professor.",
                "Orlanda Carlos Cahungo",
                "Directora",
                LocalDate.of(2023, 1, 5),
                "Adelina Pinto M. Da Costa",
                "Concordo",
                "Orlanda Carlos Cahungo",
                List.of(
                        new AvaliacaoProfessorRelatorio.Indicador(1, "Qualidade do Processo de Ensino e Aprendizagem", 2, 2, 2, 2),
                        new AvaliacaoProfessorRelatorio.Indicador(2, "Progresso do Aluno ou Desenvolvimento do Aluno", 1.5, 4.5, 3, 3),
                        new AvaliacaoProfessorRelatorio.Indicador(3, "Responsabilidade", 2, 4, 3, 3),
                        new AvaliacaoProfessorRelatorio.Indicador(4, "Aperfeiçoamento Profissional e Inovação Pedagógica", 1.5, 1.5, 1, 1.3),
                        new AvaliacaoProfessorRelatorio.Indicador(5, "Relações Humanas", 4, 2, 3, 3)
                )
        );

        AvaliacaoProfessorReportService service = new AvaliacaoProfessorReportService();
        var jasperPrint = service.buildJasperPrint(relatorio);
        assertEquals(2, jasperPrint.getPages().size(), "A ficha anual deve ocupar exatamente 2 páginas.");
        service.generateDocx(output, relatorio);

        assertTrue(Files.size(output) > 0);
        assertTrue(service.generatePdfBytes(relatorio).length > 0);
        try (ZipFile docx = new ZipFile(output.toFile())) {
            var document = docx.getEntry("word/document.xml");
            assertTrue(document != null);
            String content;
            try (InputStream stream = docx.getInputStream(document)) {
                content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(content.contains("REPÚBLICA DE ANGOLA"));
            assertTrue(content.contains("ADELINA PINTO M. DA COSTA"));
            assertTrue(docx.stream().anyMatch(entry -> entry.getName().startsWith("word/media/")));
        }
        Files.deleteIfExists(output);
    }
}
