# Execução de verificação

Esta pasta guarda uma execução completa do `benchmark.py` (15 problemas, 53 configurações,
318 execuções) feita em 23/11/2026 para conferir a **corretude** das três versões.

## O que ela comprova
Em todos os 15 problemas, as versões sequencial, paralela e distribuída produziram as mesmas
gerações executadas, as mesmas contagens de estados e o mesmo checksum, com 1, 2, 4, 8 e 12
threads/workers. Para refazer a conferência:

    python analise.py verificacao/resultados.csv

Os valores que não dependem da máquina também conferem com os da apresentação: checksum
20800729510 e 216.798 inativos na grade padrão; 513.371, 469.438 e 429.858 inativos com
`prob` 1,0, 0,7 e 0,4; parada em 3 e 5 gerações com 5% e 20% de espalhadores iniciais.

## O que ela não é
**Os tempos deste arquivo não devem ser usados como resultado de desempenho.** A execução foi
feita em outra máquina (notebook com Intel Core i7-1255U, 2 núcleos P + 8 núcleos E), ligada
na bateria, e os tempos oscilaram bastante: o mesmo cenário sequencial variou de cerca de
1,0 s a 2,0 s conforme o momento da execução. Os tempos, speedups e eficiências da
apresentação vêm de outra execução, feita na máquina descrita nos slides
(Intel Core i5-13450HX).

## Arquivos
- `resultados.csv`: saída bruta do `benchmark.py`;
- `info_sistema.txt`: máquina, sistema e versões usadas nesta execução.
