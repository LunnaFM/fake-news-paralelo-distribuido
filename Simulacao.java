import java.util.Locale;
import java.util.Random;

/**
 * Código comum às três versões (sequencial, paralela, distribuída).
 * Todas usam EXATAMENTE o mesmo kernel de cálculo (passo), o que garante
 * que o comportamento lógico seja idêntico.
 *
 * Grade com "linhas fantasma": o índice 0 e o índice linhas+1 são linhas
 * extras. Na versão sequencial/paralela ficam sempre zeradas (borda do mundo);
 * na distribuída são preenchidas com as bordas dos workers vizinhos.
 */
public class Simulacao {

    public static final byte IGNORANTE = 0;
    public static final byte ESPALHADOR = 1;
    public static final byte INATIVO = 2;

    public static final String CSV_CAB = "versao,linhas,colunas,geracoes,pct,limiar,prob,parada,"
            + "paralelismo,t_total,t_calc,t_sync,geracoes_exec,ignorantes,espalhadores,inativos,checksum";

    // ---------- Parâmetros (linha de comando no formato chave=valor) ----------
    public static class Params {
        public int linhas, colunas, geracoes, limiar;
        public double pct, prob;
        public boolean parada;
        public long semente;

        public static Params ler(String[] a) {
            Params p = new Params();
            p.linhas = Integer.parseInt(arg(a, "linhas", "2000"));
            p.colunas = Integer.parseInt(arg(a, "colunas", String.valueOf(p.linhas)));
            p.geracoes = Integer.parseInt(arg(a, "geracoes", "50"));
            p.pct = Double.parseDouble(arg(a, "pct", "0.05"));
            p.limiar = Integer.parseInt(arg(a, "limiar", "3"));
            p.prob = Double.parseDouble(arg(a, "prob", "1.0"));      // prob. de convencimento
            p.parada = arg(a, "parada", "0").equals("1");             // parada antecipada
            p.semente = Long.parseLong(arg(a, "semente", "42"));
            return p;
        }
    }

    public static String arg(String[] a, String chave, String padrao) {
        String prefixo = chave + "=";
        for (String s : a) if (s.startsWith(prefixo)) return s.substring(prefixo.length());
        return padrao;
    }

    // ---------- Grade ----------
    public static byte[][] criarGrade(int linhas, int colunas, double pct, long semente) {
        byte[][] g = new byte[linhas + 2][colunas];
        Random rand = new Random(semente);
        int total = (int) ((long) linhas * colunas * pct);
        for (int k = 0; k < total; k++) {
            int i = rand.nextInt(linhas);
            int j = rand.nextInt(colunas);
            g[i + 1][j] = ESPALHADOR;
        }
        return g;
    }

    /** Divide n linhas em 'partes' faixas balanceadas. Retorna {inicio, quantidade}. */
    public static int[] faixa(int n, int partes, int k) {
        int base = n / partes, resto = n % partes;
        return new int[]{k * base + Math.min(k, resto), base + (k < resto ? 1 : 0)};
    }

    public static long contarEspalhadores(byte[][] g, int rIni, int rFim) {
        long c = 0;
        for (int r = rIni; r < rFim; r++)
            for (byte v : g[r]) if (v == ESPALHADOR) c++;
        return c;
    }

    // ---------- Kernel: calcula a próxima geração das linhas [rIni, rFim) ----------
    /**
     * Regras: Ignorante vira Espalhador se tiver >= limiar vizinhos espalhadores
     * (Moore, 8 vizinhos) e, se prob < 1, com probabilidade prob.
     * Espalhador e Inativo viram Inativo.
     * Lê somente de 'atual' e escreve somente em 'prox' (nenhuma condição de corrida
     * entre threads que processam faixas diferentes).
     * @return número de espalhadores gerados nessas linhas
     */
    public static long passo(byte[][] atual, byte[][] prox, int rIni, int rFim, int colunas,
                             int limiar, double prob, long semente, int gen, int offsetGlobal) {
        long esp = 0;
        for (int r = rIni; r < rFim; r++) {
            byte[] cima = atual[r - 1], meio = atual[r], baixo = atual[r + 1], saida = prox[r];
            for (int j = 0; j < colunas; j++) {
                byte novo;
                if (meio[j] == IGNORANTE) {
                    int c0 = (j > 0) ? j - 1 : 0;
                    int c1 = (j < colunas - 1) ? j + 1 : colunas - 1;
                    int viz = 0;
                    for (int c = c0; c <= c1; c++) {
                        if (cima[c] == ESPALHADOR) viz++;
                        if (baixo[c] == ESPALHADOR) viz++;
                        if (c != j && meio[c] == ESPALHADOR) viz++;
                    }
                    novo = IGNORANTE;
                    if (viz >= limiar
                            && (prob >= 1.0 || aleatorio(semente, gen, offsetGlobal + r - 1, j) < prob)) {
                        novo = ESPALHADOR;
                    }
                } else {
                    novo = INATIVO;
                }
                saida[j] = novo;
                if (novo == ESPALHADOR) esp++;
            }
        }
        return esp;
    }

    /** Número pseudoaleatório em [0,1) determinístico por (semente, geração, i, j) (SplitMix64).
     *  Não depende da ordem de execução, então sequencial/paralelo/distribuído dão o mesmo resultado. */
    public static double aleatorio(long semente, int gen, int i, int j) {
        long z = semente;
        z += (gen + 1L) * 0x9E3779B97F4A7C15L;
        z ^= (i + 1L) * 0xBF58476D1CE4E5B9L;
        z ^= (j + 1L) * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    // ---------- Estatísticas e checksum (somáveis entre faixas) ----------
    /** @return {ignorantes, espalhadores, inativos, checksum} das linhas [rIni, rFim) */
    public static long[] estatisticas(byte[][] g, int rIni, int rFim, int offsetGlobal, int colunas) {
        long[] e = new long[4];
        for (int r = rIni; r < rFim; r++) {
            long gi = offsetGlobal + r - 1;
            for (int j = 0; j < colunas; j++) {
                int s = g[r][j];
                e[s]++;
                if (s != 0) e[3] += s * ((gi * 31L + j * 17L) % 1000003L + 1);
            }
        }
        return e;
    }

    // ---------- Saída ----------
    public static void imprimirResultado(String versao, Params p, int paralelismo, double tTotal,
                                         double tCalc, double tSync, int gensExec, long[] e) {
        long total = (long) p.linhas * p.colunas;
        System.out.printf(Locale.US, "=== %s | %dx%d | paralelismo=%d ===%n", versao, p.linhas, p.colunas, paralelismo);
        System.out.printf(Locale.US, "Geracoes executadas: %d de %d%n", gensExec, p.geracoes);
        System.out.printf(Locale.US, "Tempo total: %.4f s (calculo %.4f s, sincronizacao/comunicacao %.4f s)%n", tTotal, tCalc, tSync);
        System.out.printf(Locale.US, "Ignorantes: %d (%.2f%%)%n", e[0], e[0] * 100.0 / total);
        System.out.printf(Locale.US, "Espalhadores: %d (%.2f%%)%n", e[1], e[1] * 100.0 / total);
        System.out.printf(Locale.US, "Inativos: %d (%.2f%%)%n", e[2], e[2] * 100.0 / total);
        System.out.printf(Locale.US, "Checksum: %d%n", e[3]);
        System.out.println(String.format(Locale.US, "CSV,%s,%d,%d,%d,%s,%d,%s,%d,%d,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d",
                versao, p.linhas, p.colunas, p.geracoes, p.pct, p.limiar, p.prob, p.parada ? 1 : 0,
                paralelismo, tTotal, tCalc, tSync, gensExec, e[0], e[1], e[2], e[3]));
    }
}
