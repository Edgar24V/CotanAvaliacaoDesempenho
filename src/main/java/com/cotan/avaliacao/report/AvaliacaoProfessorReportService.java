package com.cotan.avaliacao.report;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.view.JasperViewer;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.SwingUtilities;

public class AvaliacaoProfessorReportService {

    public void generateDocx(Path outputPath, AvaliacaoProfessorRelatorio relatorio) throws IOException, JRException {
        Objects.requireNonNull(outputPath, "outputPath");
        JasperPrint jasperPrint = buildJasperPrint(relatorio);

        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
            JRDocxExporter exporter = new JRDocxExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
            exporter.exportReport();
        }
    }

    public byte[] generatePdfBytes(AvaliacaoProfessorRelatorio relatorio) throws JRException, IOException {
        return JasperExportManager.exportReportToPdf(buildJasperPrint(relatorio));
    }

    public void showViewer(AvaliacaoProfessorRelatorio relatorio, String title) throws JRException, IOException {
        Objects.requireNonNull(relatorio, "relatorio");

        JasperPrint jasperPrint = buildJasperPrint(relatorio);
        String viewerTitle = (title == null || title.isBlank())
                ? "COTAN — Ficha de Avaliação de Desempenho Anual"
                : title;

        SwingUtilities.invokeLater(() -> {
            JasperViewer viewer = new JasperViewer(jasperPrint, false);
            viewer.setTitle(viewerTitle);
            viewer.setLocationByPlatform(true);
            viewer.setVisible(true);
        });
    }

    public JasperPrint buildJasperPrint(AvaliacaoProfessorRelatorio relatorio) throws JRException, IOException {
        Objects.requireNonNull(relatorio, "relatorio");

        try (InputStream templateStream = getClass().getResourceAsStream("/reports/ficha-avaliacao-professor-anual.jrxml");
             InputStream brasaoStream = getClass().getResourceAsStream("/reports/brasao-angola.png")) {
            if (templateStream == null) {
                throw new IllegalStateException("O template do relatório não foi encontrado em /reports/ficha-avaliacao-professor-anual.jrxml");
            }
            if (brasaoStream == null) {
                throw new IllegalStateException("O brasão não foi encontrado em /reports/brasao-angola.png");
            }

            JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);
            Map<String, Object> params = new HashMap<>();
            putCommonParameters(params, relatorio, ImageIO.read(brasaoStream));
            return JasperFillManager.fillReport(jasperReport, params, new JREmptyDataSource(1));
        }
    }

    private void putCommonParameters(Map<String, Object> params,
                                      AvaliacaoProfessorRelatorio relatorio,
                                      java.awt.Image brasao) {
        params.put("gabineteProvincial", relatorio.gabineteProvincial());
        params.put("direccaoMunicipal", relatorio.direccaoMunicipal());
        params.put("escola", relatorio.escola());
        params.put("professorNome", relatorio.professorNome());
        params.put("categoria", relatorio.categoria());
        params.put("agenteNumero", relatorio.agenteNumero());
        params.put("dataAvaliacao", relatorio.getDataAvaliacaoFormatada());
        params.put("periodoInicio", relatorio.getPeriodoInicioFormatado());
        params.put("periodoFim", relatorio.getPeriodoFimFormatado());
        params.put("classificacaoFinalQuantitativa", relatorio.classificacaoFinalQuantitativa());
        params.put("classificacaoFinalQualitativa", relatorio.classificacaoFinalQualitativa());
        params.put("classificacaoQuantitativa1", trimesterQuantitative(relatorio.indicadores(), AvaliacaoProfessorRelatorio.Indicador::getTrim1));
        params.put("classificacaoQuantitativa2", trimesterQuantitative(relatorio.indicadores(), AvaliacaoProfessorRelatorio.Indicador::getTrim2));
        params.put("classificacaoQuantitativa3", trimesterQuantitative(relatorio.indicadores(), AvaliacaoProfessorRelatorio.Indicador::getTrim3));
        params.put("classificacaoQualitativa1", qualitative(params.get("classificacaoQuantitativa1")));
        params.put("classificacaoQualitativa2", qualitative(params.get("classificacaoQuantitativa2")));
        params.put("classificacaoQualitativa3", qualitative(params.get("classificacaoQuantitativa3")));
        params.put("apreciacaoGeral", relatorio.apreciacaoGeral());
        params.put("comentario1", relatorio.comentario1());
        params.put("comentario2", relatorio.comentario2());
        params.put("comentario3", relatorio.comentario3());
        params.put("nomeAvaliador", relatorio.nomeAvaliador());
        params.put("funcaoAvaliador", relatorio.funcaoAvaliador());
        params.put("dataAvaliacaoAvaliador", relatorio.getDataAvaliacaoAvaliadorFormatada());
        params.put("nomeAvaliado", relatorio.nomeAvaliado());
        params.put("concordancia", relatorio.concordancia());
        params.put("nomeHomologante", relatorio.nomeHomologante());
        // Cada tabela recebe o seu próprio datasource: JasperReports consome o cursor
        // durante a renderização, portanto não reutilizamos a mesma instância nas duas tabelas.
        params.put("indicadoresDataSource1", (JRDataSource) new JRBeanCollectionDataSource(relatorio.indicadores()));
        params.put("indicadoresDataSource2", (JRDataSource) new JRBeanCollectionDataSource(relatorio.indicadores()));
        params.put("brasao", brasao);
    }

    private String trimesterQuantitative(List<AvaliacaoProfessorRelatorio.Indicador> indicadores,
                                         Function<AvaliacaoProfessorRelatorio.Indicador, String> valueExtractor) {
        double sum = 0.0;
        boolean found = false;

        for (AvaliacaoProfessorRelatorio.Indicador indicador : indicadores) {
            Double value = parseValue(valueExtractor.apply(indicador));
            if (value != null) {
                sum += value;
                found = true;
            }
        }

        if (!found) {
            return "";
        }

        return String.format(Locale.US, "%.0f", sum);
    }

    private Double parseValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String qualitative(Object value) {
        Double number = value instanceof Number n ? n.doubleValue() : parseValue(String.valueOf(value));
        if (number == null) {
            return "";
        }
        if (number < 10) return "Mau";
        if (number < 14) return "Suficiente";
        if (number < 18) return "Bom";
        return "Muito bom";
    }


}
