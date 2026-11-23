# Propagação de Fake News – Sequencial, Paralelo (Threads) e Distribuído (Sockets)

Trabalho prático da disciplina de Sistemas Distribuídos.

Repositório: https://github.com/LunnaFM/fake-news-paralelo-distribuido

## Integrantes
- Ilanna Corado
- Kaique Tavares Zambrano
- Luana Monteiro Ferreira
- Matheus Henrique Furtado

Requisitos: JDK 17+ (testado com 21) e Python 3 (só para o script de benchmark).

## Compilar
    javac *.java

## Executar (parâmetros opcionais no formato chave=valor)
Parâmetros: linhas, colunas, geracoes, pct, limiar, prob, parada, semente
(padrão: 2000, =linhas, 50, 0.05, 3, 1.0, 0, 42)

    java FakeNewsSequencial linhas=2000 geracoes=50 pct=0.05 curva=1
    java FakeNewsParallel threads=4 linhas=2000 geracoes=50

Distribuído (um terminal para cada processo; o mestre primeiro):

    java FakeNewsMaster workers=2 porta=5000 linhas=2000 geracoes=50
    java FakeNewsWorker host=localhost porta=5000     (repetir 2 vezes)

Demonstração rápida (mestre + 2 workers na mesma JVM, não usar para medir):

    java Main linhas=500 geracoes=30

## Benchmark completo
    python benchmark.py --teste     # verificação rápida
    python benchmark.py             # gera resultados.csv e info_sistema.txt

## Arquivos
- Simulacao.java: kernel de cálculo e utilidades compartilhados pelas 3 versões
- FakeNewsSequencial / FakeNewsParallel / FakeNewsMaster / FakeNewsWorker / Main
- benchmark.py: executa todos os experimentos e valida checksums
