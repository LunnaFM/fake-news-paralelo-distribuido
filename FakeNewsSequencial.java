/** Versão sequencial em Java (baseline para speedup).
 *  Uso: java FakeNewsSequencial linhas=2000 geracoes=50 pct=0.05 limiar=3 prob=1.0 parada=0 curva=0 */
public class FakeNewsSequencial {
    public static void main(String[] args) {
        Simulacao.Params p = Simulacao.Params.ler(args);
        boolean curva = Simulacao.arg(args, "curva", "0").equals("1"); // imprime espalhadores por geração

        byte[][] atual = Simulacao.criarGrade(p.linhas, p.colunas, p.pct, p.semente);
        byte[][] prox = new byte[p.linhas + 2][p.colunas];
        long esp = Simulacao.contarEspalhadores(atual, 1, p.linhas + 1);

        long t0 = System.nanoTime();
        int gen = 0;
        for (; gen < p.geracoes; gen++) {
            if (p.parada && esp == 0) break; // parada antecipada: nada mais muda
            esp = Simulacao.passo(atual, prox, 1, p.linhas + 1, p.colunas, p.limiar, p.prob, p.semente, gen, 0);
            byte[][] tmp = atual; atual = prox; prox = tmp; // double buffering
            if (curva) System.out.println("CURVA," + (gen + 1) + "," + esp);
        }
        double t = (System.nanoTime() - t0) / 1e9;

        long[] est = Simulacao.estatisticas(atual, 1, p.linhas + 1, 0, p.colunas);
        Simulacao.imprimirResultado("sequencial", p, 1, t, t, 0, gen, est);
    }
}
