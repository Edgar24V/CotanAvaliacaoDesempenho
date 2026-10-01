# Arquitetura

JavaFX/AtlantaFX -> Controllers/ViewModels -> Application Services -> Domain/Calculation Engine -> Spring Data JPA -> SQLite

O banco é exclusivamente local neste projeto.

## Camadas
- `domain`: entidades, enums e regras puras.
- `service`: casos de uso e cálculo.
- `config`: configuração do SQLite/Spring.
- `presentation`: JavaFX/AtlantaFX.
- `report`: JasperReports.

## SQLite
O SQLite será usado como arquivo local e preparado para:
- backup consistente;
- restauração;
- integridade referencial;
- funcionamento offline;
- instalação em um único computador sem infraestrutura adicional.
