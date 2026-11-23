import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

/** Mestre da versão distribuída (Sockets TCP).
 *  Divide a grade em faixas, envia aos workers e, a cada geração, repassa as bordas (ghost rows).
 *  Uso: java FakeNewsMaster workers=2 porta=5000 linhas=2000 geracoes=50 pct=0.05
 *  Opcional: timeout=120 (segundos sem resposta de um worker até desistir). */
public class FakeNewsMaster {
    public static void main(String[] args) throws IOException {
        Simulacao.Params p = Simulacao.Params.ler(args);
        int W = Integer.parseInt(Simulacao.arg(args, "workers", "2"));
        int porta = Integer.parseInt(Simulacao.arg(args, "porta", "5000"));
        int timeoutS = Integer.parseInt(Simulacao.arg(args, "timeout", "120"));
        Simulacao.validarParalelismo("workers", W, p.linhas);

        byte[][] grade = Simulacao.criarGrade(p.linhas, p.colunas, p.pct, p.semente);

        Socket[] socks = new Socket[W];
        DataInputStream[] in = new DataInputStream[W];
        DataOutputStream[] out = new DataOutputStream[W];

        // Contadores de bytes trafegados (custo de comunicação medido, não estimado).
        long bytesIniciais = 0, bytesEnviados = 0, bytesRecebidos = 0;

        try (ServerSocket server = new ServerSocket(porta)) {
            System.out.println("PRONTO porta=" + porta + " (aguardando " + W + " workers)");
            System.out.flush();
            for (int w = 0; w < W; w++) {
                socks[w] = server.accept();
                socks[w].setTcpNoDelay(true);
                // Se um worker travar ou cair sem fechar a conexão, a leitura estoura o tempo
                // (SocketTimeoutException) em vez de bloquear o mestre para sempre.
                socks[w].setSoTimeout(timeoutS * 1000);
                out[w] = new DataOutputStream(new BufferedOutputStream(socks[w].getOutputStream(), 1 << 16));
                in[w] = new DataInputStream(new BufferedInputStream(socks[w].getInputStream(), 1 << 16));
            }

            long t0 = System.nanoTime();

            // 1) Envia configuração e a fatia inicial (bytes, uma linha por write)
            int[] qtd = new int[W], ini = new int[W];
            for (int w = 0; w < W; w++) {
                int[] f = Simulacao.faixa(p.linhas, W, w);
                ini[w] = f[0]; qtd[w] = f[1];
                out[w].writeInt(w);
                out[w].writeInt(qtd[w]);
                out[w].writeInt(p.colunas);
                out[w].writeInt(p.geracoes);
                out[w].writeInt(p.limiar);
                out[w].writeInt(ini[w]);        // deslocamento global (para o RNG por célula)
                out[w].writeLong(p.semente);
                out[w].writeDouble(p.prob);
                for (int i = 0; i < qtd[w]; i++) out[w].write(grade[1 + ini[w] + i]);
                out[w].flush();
                // 6 int (24 B) + 1 long (8 B) + 1 double (8 B) + a fatia
                bytesIniciais += 40L + (long) qtd[w] * p.colunas;
            }

            // 2) Loop de gerações: recebe bordas, decide parada, devolve ghost rows
            byte[][] sup = new byte[W][p.colunas], inf = new byte[W][p.colunas];
            byte[] vazia = new byte[p.colunas];
            int gensExec = 0;
            for (int gen = 0; gen < p.geracoes; gen++) {
                long totalEsp = 0;
                for (int w = 0; w < W; w++) {
                    in[w].readFully(sup[w]);
                    in[w].readFully(inf[w]);
                    totalEsp += in[w].readLong();
                    bytesRecebidos += 2L * p.colunas + 8;       // 2 bordas + 1 long
                }
                boolean continuar = !(p.parada && totalEsp == 0);
                for (int w = 0; w < W; w++) {
                    out[w].writeBoolean(continuar);
                    bytesEnviados += 1;                          // o boolean
                    if (continuar) {
                        out[w].write(w == 0 ? vazia : inf[w - 1]);          // ghost superior
                        out[w].write(w == W - 1 ? vazia : sup[w + 1]);      // ghost inferior
                        bytesEnviados += 2L * p.colunas;         // 2 ghost rows
                    }
                    out[w].flush();
                }
                if (!continuar) break;
                gensExec++;
            }

            // 3) Recebe só estatísticas finais (não a grade inteira): menos comunicação
            long[] est = new long[4];
            long maxCalcNs = 0;
            for (int w = 0; w < W; w++) {
                for (int k = 0; k < 4; k++) est[k] += in[w].readLong();
                maxCalcNs = Math.max(maxCalcNs, in[w].readLong());
                bytesRecebidos += 5L * 8;                        // 5 long
            }
            double tTotal = (System.nanoTime() - t0) / 1e9;
            double tCalc = maxCalcNs / 1e9;
            Simulacao.imprimirResultado("distribuido", p, W, tTotal, tCalc, tTotal - tCalc, gensExec, est);
            System.out.printf(Locale.US,
                    "Comunicacao: %d bytes na distribuicao inicial; nas geracoes, %d bytes enviados e %d recebidos pelo mestre%n",
                    bytesIniciais, bytesEnviados, bytesRecebidos);
        } catch (IOException e) {
            // Worker caiu, fechou a conexão ou ficou mais de 'timeout' segundos sem responder.
            System.err.println("ERRO: falha de comunicacao com um worker (" + e + "). Simulacao interrompida.");
            System.exit(1);
        } finally {
            // Fecha todas as conexões: os workers restantes percebem o fim e também encerram.
            for (Socket s : socks) {
                if (s != null) {
                    try { s.close(); } catch (IOException ignorada) { /* já estava fechada */ }
                }
            }
        }
    }
}
