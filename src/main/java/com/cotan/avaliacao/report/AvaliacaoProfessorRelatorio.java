package com.cotan.avaliacao.report;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public record AvaliacaoProfessorRelatorio(
        String professorNome,
        String categoria,
        String agenteNumero,
        LocalDate dataAvaliacao,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        String classificacaoFinalQuantitativa,
        String classificacaoFinalQualitativa,
        String apreciacaoGeral,
        String nomeAvaliador,
        String funcaoAvaliador,
        LocalDate dataAvaliacaoAvaliador,
        String nomeAvaliado,
        String concordancia,
        String nomeHomologante,
        List<Indicador> indicadores
) {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "PT"));

    public AvaliacaoProfessorRelatorio(
            String professorNome,
            String categoria,
            String agenteNumero,
            LocalDate dataAvaliacao,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            String classificacaoFinalQuantitativa,
            String classificacaoFinalQualitativa,
            String apreciacaoGeral,
            String nomeAvaliador,
            String funcaoAvaliador,
            LocalDate dataAvaliacaoAvaliador,
            String nomeAvaliado,
            String concordancia,
            String nomeHomologante,
            List<Indicador> indicadores
    ) {
        this.professorNome = professorNome == null ? "" : professorNome;
        this.categoria = categoria == null ? "" : categoria;
        this.agenteNumero = agenteNumero == null ? "" : agenteNumero;
        this.dataAvaliacao = dataAvaliacao == null ? LocalDate.now() : dataAvaliacao;
        this.periodoInicio = periodoInicio == null ? LocalDate.now() : periodoInicio;
        this.periodoFim = periodoFim == null ? LocalDate.now() : periodoFim;
        this.classificacaoFinalQuantitativa = classificacaoFinalQuantitativa == null ? "" : classificacaoFinalQuantitativa;
        this.classificacaoFinalQualitativa = classificacaoFinalQualitativa == null ? "" : classificacaoFinalQualitativa;
        this.apreciacaoGeral = apreciacaoGeral == null ? "" : apreciacaoGeral;
        this.nomeAvaliador = nomeAvaliador == null ? "" : nomeAvaliador;
        this.funcaoAvaliador = funcaoAvaliador == null ? "" : funcaoAvaliador;
        this.dataAvaliacaoAvaliador = dataAvaliacaoAvaliador == null ? LocalDate.now() : dataAvaliacaoAvaliador;
        this.nomeAvaliado = nomeAvaliado == null ? "" : nomeAvaliado;
        this.concordancia = concordancia == null ? "" : concordancia;
        this.nomeHomologante = nomeHomologante == null ? "" : nomeHomologante;
        this.indicadores = indicadores == null ? List.of() : indicadores;
    }

    public String getDataAvaliacaoFormatada() {
        return dataAvaliacao.format(DATE_FORMAT);
    }

    public String getPeriodoInicioFormatado() {
        return periodoInicio.format(DATE_FORMAT);
    }

    public String getPeriodoFimFormatado() {
        return periodoFim.format(DATE_FORMAT);
    }

    public String getDataAvaliacaoAvaliadorFormatada() {
        return dataAvaliacaoAvaliador.format(DATE_FORMAT);
    }

    public record Indicador(
            int numero,
            String nome,
            String trim1,
            String trim2,
            String trim3,
            String media
    ) {
        public int getNumero() { return numero; }
        public String getNome() { return nome; }
        public String getTrim1() { return trim1; }
        public String getTrim2() { return trim2; }
        public String getTrim3() { return trim3; }
        public String getMedia() { return media; }

        public Indicador(int numero, String nome, Number trim1, Number trim2, Number trim3, Number media) {
            this(numero, nome, formatValue(trim1), formatValue(trim2), formatValue(trim3), formatValue(media));
        }

        private static String formatValue(Number value) {
            if (value == null) {
                return "";
            }
            double numero = value.doubleValue();
            if (Math.rint(numero) == numero) {
                return String.format(Locale.US, "%.0f", numero).replace('.', ',');
            }
            return String.format(Locale.US, "%.1f", numero).replace('.', ',');
        }
    }
}
