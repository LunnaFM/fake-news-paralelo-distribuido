import java.util.concurrent.CyclicBarrier;

/** Versão paralela com Threads Java.
 *  Cada thread processa uma faixa de linhas; CyclicBarrier sincroniza as gerações.
 *  Uso: java FakeNewsParallel threads=4 linhas=2000 geracoes=50 pct=0.05 */
public class FakeNewsParallel {
    private static byte[][] atual, prox;      // double buffering (trocadas na ação da barreira)
    private static volatile boolean parar;    // decidido na barreira, lido por todas as threads
    private static int gensExec = 0;
    private static long[] espThread;          // espalhadores contados por cada thread na geração
    private static long[] nsCalc, nsSync;     // tempo de cálculo e de espera na barreira, por thread

    public static void main(String[] args) throws Exception {
        final Simulacao.Params p = Simulacao.Params.ler(args);
        final int T = Integer.parseInt(Simulacao.arg(args, "threads", "4"));

        atual = Simulacao.criarGrade(p.linhas, p.colunas, p.pct, p.semente);
        prox = new byte[p.linhas + 2][p.colunas];
        parar = p.parada && Simulacao.contarEspalhadores(atual, 1, p.linhas + 1) == 0;
        espThread = new long[T];
        nsCalc = new long[T];
        nsSync = new long[T];

        // Executada pela ÚLTIMA thread a chegar, antes de liberar as demais:
        // troca as grades, soma os espalhadores e decide se para.
        CyclicBarrier barreira = new CyclicBarrier(T, () -> {
            byte[][] tmp = atual; atual = prox; prox = tmp;
            long soma = 0;
            for (long v : espThread) soma += v;
            gensExec++;
            parar = p.parada && soma == 0;
        });

        Thread[] threads = new Thread[T];
        long t0 = System.nanoTime();
        for (int t = 0; t < T; t++) {
            final int id = t;
            int[] f = Simulacao.faixa(p.linhas, T, t);
            final int rIni = 1 + f[0], rFim = 1 + f[0] + f[1]; // grade compartilhada: índices já são globais
            threads[t] = new Thread(() -> {
                try {
                    for (int gen = 0; gen < p.geracoes; gen++) {
                        if (parar) break;
                        long a = System.nanoTime();
                        espThread[id] = Simulacao.passo(atual, prox, rIni, rFim, p.colunas,
                                p.limiar, p.prob, p.semente, gen, 0);
                        long b = System.nanoTime();
                        barreira.await();
                        nsCalc[id] += b - a;
                        nsSync[id] += System.nanoTime() - b;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            });
            threads[t].start();
        }
        for (Thread th : threads) th.join();
        double tTotal = (System.nanoTime() - t0) / 1e9;

        double calc = 0, sync = 0;
        for (int t = 0; t < T; t++) { calc += nsCalc[t]; sync += nsSync[t]; }
        calc /= T * 1e9; sync /= T * 1e9; // média por thread

        long[] est = Simulacao.estatisticas(atual, 1, p.linhas + 1, 0, p.colunas);
        Simulacao.imprimirResultado("paralelo", p, T, tTotal, calc, sync, gensExec, est);
    }
}
