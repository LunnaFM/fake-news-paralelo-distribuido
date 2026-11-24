# Propagação de Fake News – Sequencial, Paralelo (Threads) e Distribuído (Sockets)

Trabalho prático da disciplina de Sistemas Distribuídos: evolução de uma simulação
sequencial de propagação de fake news (autômato celular 2D) para uma versão paralela
com Threads e uma versão distribuída com Sockets TCP, todas em Java.

Repositório: https://github.com/LunnaFM/fake-news-paralelo-distribuido

## Integrantes
- Ilanna Corado
- Kaique Tavares Zambrano
- Luana Monteiro Ferreira
- Matheus Henrique Furtado

## Modelo
A população é uma grade `linhas x colunas`. Cada célula é uma pessoa em um de três estados:

| Estado | Valor | Significado |
|--------|-------|-------------|
| Ignorante | 0 | ainda não recebeu/acredita na informação |
| Espalhador | 1 | acredita e compartilha |
| Inativo | 2 | já recebeu, mas não compartilha mais |

Regras de uma geração (vizinhança de Moore, até 8 vizinhos):
- Ignorante vira Espalhador se tiver pelo menos `limiar` vizinhos espalhadores
  (e, se `prob < 1`, apenas com probabilidade `prob`);
- Espalhador vira Inativo na geração seguinte;
- Inativo permanece Inativo.

A geração nova é sempre calculada a partir da grade atual e gravada em outra grade
(*double buffering*), como no código sequencial original em Python.

## Versões
- **Sequencial** (`FakeNewsSequencial`): referência para o cálculo de speedup. É o código
  Python fornecido portado para Java, com as mesmas regras. A grade inicial não é idêntica
  à do Python porque o gerador aleatório do Java é diferente do gerador do Python.
- **Paralela** (`FakeNewsParallel`): a grade é dividida em faixas horizontais, uma por
  thread. Todas leem a grade atual e cada uma escreve apenas nas suas linhas da grade
  nova, então não há condição de corrida. Uma `CyclicBarrier` sincroniza o fim de cada
  geração: troca as grades, soma os espalhadores e decide a parada.
- **Distribuída** (`FakeNewsMaster` + `FakeNewsWorker`): o mestre divide a grade em faixas
  e envia uma para cada worker por TCP. A cada geração os workers enviam suas linhas de
  borda ao mestre, que as repassa aos vizinhos como linhas fantasma (*ghost rows*). No
  final, cada worker devolve só as estatísticas, não a grade.

As três versões usam o mesmo kernel (`Simulacao.passo`) e devem produzir o mesmo
resultado (mesmas contagens e mesmo checksum) para os mesmos parâmetros.

## Requisitos
- JDK 17 ou superior (testado com OpenJDK 21);
- Python 3, apenas para o script de benchmark.

## Compilar
    javac *.java

## Parâmetros
Todos são opcionais, no formato `chave=valor`.

| Parâmetro | Padrão | Descrição |
|-----------|--------|-----------|
| `linhas` | 2000 | linhas da grade |
| `colunas` | igual a `linhas` | colunas da grade |
| `geracoes` | 50 | número máximo de gerações |
| `pct` | 0.05 | fração inicial de espalhadores |
| `limiar` | 3 | vizinhos espalhadores necessários para convencer |
| `prob` | 1.0 | probabilidade de convencimento quando o limiar é atingido |
| `parada` | 0 | `1` encerra quando não há mais espalhadores (como no código original) |
| `semente` | 42 | semente da grade inicial e dos sorteios |
| `threads` | 4 | número de threads (só na versão paralela) |
| `workers` | 2 | número de workers esperados (só no mestre) |
| `porta` | 5000 | porta TCP (mestre e workers) |
| `host` | localhost | endereço do mestre (só no worker) |
| `timeout` | 120 | segundos sem resposta de um worker até o mestre desistir (só no mestre) |
| `curva` | 0 | `1` imprime os espalhadores por geração (só na versão sequencial) |

O código original em Python sempre para quando não há mais espalhadores. Aqui o padrão é
`parada=0` para que todas as gerações sejam executadas e a carga de trabalho seja fixa
nas medições de desempenho; use `parada=1` para reproduzir o comportamento original.

Valores inválidos (grade vazia, `pct` ou `prob` fora de 0 a 1, mais threads/workers do
que linhas) são recusados com uma mensagem de erro.

## Executar
Sequencial e paralela:

    java FakeNewsSequencial linhas=2000 geracoes=50 pct=0.05 curva=1
    java FakeNewsParallel threads=4 linhas=2000 geracoes=50

Distribuída (um terminal para cada processo; o mestre primeiro):

    java FakeNewsMaster workers=2 porta=5000 linhas=2000 geracoes=50
    java FakeNewsWorker host=localhost porta=5000
    java FakeNewsWorker host=localhost porta=5000

O mestre imprime `PRONTO` quando a porta está aberta e só começa a simulação depois que
todos os workers se conectam. Para usar mais de uma máquina, rode os workers nas outras
máquinas com `host=` apontando para o IP do mestre e libere a porta no firewall.

Demonstração rápida (mestre + 2 workers na mesma JVM; não usar para medir desempenho):

    java Main linhas=500 geracoes=30

## Saída
Cada versão imprime as gerações executadas, o tempo total (separado em cálculo e
sincronização/comunicação), a contagem final de cada estado e um checksum da grade
final. A última linha, iniciada por `CSV,`, repete os mesmos dados em formato tabular e
é a que o script de benchmark lê.

O mestre da versão distribuída imprime ainda uma linha `Comunicacao:` com os bytes
trafegados: os da distribuição inicial da grade e os enviados e recebidos durante as
gerações.

## Benchmark
    python benchmark.py --teste     # verificação rápida (grades pequenas, 2 repetições)
    python benchmark.py             # experimento completo (6 repetições por configuração)

O script compila o código, executa as três versões variando tamanho da grade, número de
gerações, percentual inicial, número de threads/workers, parada antecipada e
probabilidade, e grava `resultados.csv` e `info_sistema.txt`. Cada execução usa uma JVM
nova. Ao final ele confere se, para cada problema, as três versões produziram as mesmas
gerações executadas, as mesmas contagens e o mesmo checksum.

O limiar dos experimentos é 3 por padrão; com ele a propagação se extingue em poucas
gerações. Para um cenário com propagação mais longa:

    python benchmark.py --limiar 2

Para refazer a validação e calcular mediana, speedup e eficiência a partir de um
`resultados.csv` já existente (descarta a repetição 0 e grava `tabela_resultados.csv`):

    python analise.py

## Melhorias em relação ao código original
- probabilidade de convencimento (`prob`) com sorteio determinístico por célula
  (hash de semente, geração, linha e coluna), independente da ordem de execução;
- grade em `byte[][]` e troca de referências entre gerações, sem cópia da grade;
- comunicação reduzida na versão distribuída: só as bordas trafegam a cada geração e só
  as estatísticas no final;
- parada antecipada coordenada na barreira (paralela) e pelo mestre (distribuída);
- medição separada do tempo de cálculo e do tempo de sincronização/comunicação, e
  contagem dos bytes trafegados na versão distribuída;
- curva de espalhadores por geração (`curva=1`).

## Limitações
- em todas as medições, mestre e workers rodaram na mesma máquina (*loopback*); nenhuma
  usou rede real entre máquinas diferentes;
- os tempos dependem da máquina: os da apresentação foram medidos em um Intel Core
  i5-13450HX (6 núcleos P + 4 E); uma segunda execução completa, em um Intel Core i7-1255U
  (2 núcleos P + 8 E, pasta `verificacao/`), deu os mesmos resultados lógicos, mas speedup
  menor, cerca de 2 vezes com 4 threads ou workers, sem ganho adicional com 8 ou 12;
- as bordas passam pelo mestre (topologia em estrela), não diretamente entre workers;
- não há recuperação de falha: se um worker cair ou ficar sem responder por `timeout`
  segundos, o mestre encerra a simulação com uma mensagem de erro e fecha as conexões;
- o tempo total da versão distribuída inclui o envio inicial da grade e o cálculo das
  estatísticas finais nos workers; o das versões sequencial e paralela não inclui a
  criação da grade nem as estatísticas;
- cada execução do benchmark usa uma JVM nova, então os tempos incluem o aquecimento do
  compilador JIT, o que pesa mais nas grades pequenas.

## Arquivos
- `Simulacao.java`: parâmetros, criação da grade, kernel de cálculo, sorteio e estatísticas
  compartilhados pelas três versões;
- `FakeNewsSequencial.java`: versão sequencial;
- `FakeNewsParallel.java`: versão paralela com Threads;
- `FakeNewsMaster.java` e `FakeNewsWorker.java`: versão distribuída com Sockets;
- `Main.java`: demonstração da versão distribuída em uma só JVM;
- `benchmark.py`: executa os experimentos e valida os resultados;
- `analise.py`: revalida um `resultados.csv` e calcula mediana, speedup e eficiência;
- `verificacao/`: execução completa do benchmark usada para conferir a corretude das três
  versões (os tempos dela não são os da apresentação; ver o README da pasta);
- `apresentacao_revisado.pdf`: slides com resultados, análise e referências bibliográficas.
