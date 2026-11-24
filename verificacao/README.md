# Execução de verificação

Esta pasta guarda uma execução completa do `benchmark.py` (15 problemas, 53 configurações,
318 execuções) feita em 23/11/2026 para conferir a **corretude** das três versões em uma
segunda máquina.

## O que ela comprova
Em todos os 15 problemas, as versões sequencial, paralela e distribuída produziram as mesmas
gerações executadas, as mesmas contagens de estados e o mesmo checksum, com 1, 2, 4, 8 e 12
threads/workers. Para refazer a conferência:

    python analise.py verificacao/resultados.csv

Os valores que não dependem da máquina também conferem com os da apresentação: checksum
20800729510 e 216.798 inativos na grade padrão; 513.371, 469.438 e 429.858 inativos com
`prob` 1,0, 0,7 e 0,4; parada em 3 e 5 gerações com 5% e 20% de espalhadores iniciais.

## Sobre os tempos
**Os tempos desta pasta não são os da apresentação.** Esta execução foi feita em outra
máquina, um notebook com Intel Core i7-1255U (2 núcleos P + 8 núcleos E, 12 lógicos), ligado
na tomada. Os tempos, speedups e eficiências dos slides vêm da execução feita na máquina
descrita neles (Intel Core i5-13450HX, 6 núcleos P + 4 núcleos E).

Nesta máquina o ganho é menor: com 4 threads ou workers o speedup fica em torno de 1,7 a 2,4
vezes nas grades de 2000 × 2000 em diante, e não melhora com 8 ou 12. Isso é coerente com o
processador, que tem apenas 2 núcleos de desempenho, e reforça a análise da apresentação
sobre processadores híbridos. Há ainda alguma oscilação entre cenários de mesma carga
(o sequencial de 2000 × 2000 com 50 gerações variou de cerca de 1,1 s a 1,4 s).

## Arquivos
- `resultados.csv`: saída bruta do `benchmark.py`;
- `tabela_resultados.csv`: mediana, speedup e eficiência de cada configuração, gerada pelo
  `analise.py`;
- `info_sistema.txt`: máquina, sistema e versões usadas nesta execução.
