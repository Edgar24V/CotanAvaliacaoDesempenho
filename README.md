# Cotan — Sistema de Avaliação de Desempenho

Sistema independente para transformar o mapa Excel de avaliação de desempenho em uma aplicação desktop JavaFX/AtlantaFX + Spring Boot + Spring Data JPA + SQLite.

## Banco de dados
SQLite é o banco oficial deste projeto.

Arquivo padrão:
`%USER_HOME%/.CotanFx/data/avaliacao.db`

Não depende de MySQL, servidor, IP, porta, utilizador ou password de banco.

## Objetivo funcional
- Professores
- Administrativos
- 1.º, 2.º e 3.º trimestre
- Mapas finais
- Critérios e pesos
- Médias e totais
- Classificação automática
- Histórico
- Auditoria
- JasperReports/PDF
- Importação e exportação controladas
- Testes de equivalência Excel x Java

## Stack
- Java 25
- JavaFX 25
- AtlantaFX 2.1.x para a combinação compatível com JavaFX 25
- Spring Boot
- Spring Data JPA
- Hibernate
- SQLite

> AtlantaFX 3.0.0 usa Java 25 + JavaFX 27. Portanto, para manter JavaFX 25, este projeto começa com a linha AtlantaFX 2.1.x. Caso AtlantaFX 3 seja obrigatório, a linha JavaFX deverá ser elevada para 27.

## Origem das regras
O comportamento será reconstruído a partir do documento Excel de avaliação de desempenho 2023-2024. As fórmulas com referências `#REF!` serão validadas antes de virarem regras definitivas.
