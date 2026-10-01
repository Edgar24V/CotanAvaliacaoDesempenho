# COTAN • Avaliação e Desempenho

Sistema desktop académico desenvolvido em Java 21, JavaFX e AtlantaFX, com persistência local em SQLite.

## Funcionalidades

- Dashboard executivo com indicadores de alunos, professores, turmas, disciplinas, avaliações e média geral.
- Gestão completa de alunos, professores, turmas e disciplinas.
- Gestão de avaliações: prova, teste, trabalho, projeto, exame e participação.
- Lançamento e edição de notas por avaliação.
- Validação pelo limite máximo configurado.
- Cálculo automático de média ponderada.
- Estado académico com referência de aprovação em 10 valores.
- Relatório de desempenho com exportação CSV UTF-8.
- Pesquisa rápida nos módulos.
- Backup da base SQLite.
- Tema claro/escuro com AtlantaFX Primer Light / Primer Dark.

## Banco de dados

A base é criada automaticamente em:

`%USERPROFILE%\\.CotanAvaliacaoDesempenho\\data\\avaliacao.db`

Tabelas principais:

`classes`, `teachers`, `subjects`, `students`, `assessments`, `grades`.

## Tecnologias

- Java 21
- JavaFX 21
- AtlantaFX 2.1.0
- Spring Boot 3.5.16
- SQLite JDBC
- Maven

## Executar no Windows

```powershell
cd "C:\Users\HP\Documents\Antigravity\CotanAvaliacaoDesempenho"
git pull origin master
mvn clean
mvn javafx:run
```

## Fluxo académico

1. Ajuste as turmas.
2. Registe professores.
3. Registe disciplinas e pesos.
4. Registe alunos e associe-os às turmas.
5. Crie avaliações.
6. Em "Lançar notas", carregue a avaliação e grave os resultados.
7. Em "Relatórios", consulte as médias ponderadas e estados.
8. Em "Configurações", crie backups ou altere o tema.

## Build

O repositório possui GitHub Actions para executar `mvn clean compile` com Java 21 em cada push/PR para `master`.
