import java.io.*;
import java.net.Socket;

/** Worker da versão distribuída. Uso: java FakeNewsWorker host=localhost porta=5000 */
public class FakeNewsWorker {
    public static void main(String[] args) {
        String host = Simulacao.arg(args, "host", "localhost");
        int porta = Integer.parseInt(Simulacao.arg(args, "porta", "5000"));

        try (Socket s = new Socket(host, porta)) {
            s.setTcpNoDelay(true);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 1 << 16));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));

            int id = in.readInt();
            int n = in.readInt();           // linhas deste worker
            int cols = in.readInt();
            int geracoes = in.readInt();
            int limiar = in.readInt();
            int offset = in.readInt();
            long semente = in.readLong();
            double prob = in.readDouble();

            // Linha 0 e n+1 = ghost rows; 1..n = linhas reais
            byte[][] atual = new byte[n + 2][cols], prox = new byte[n + 2][cols];
            for (int i = 1; i <= n; i++) in.readFully(atual[i]);

            long esp = Simulacao.contarEspalhadores(atual, 1, n + 1);
            long calcNs = 0;

            for (int gen = 0; gen < geracoes; gen++) {
                // envia bordas reais + nº de espalhadores (para o mestre decidir a parada)
                out.write(atual[1]);
                out.write(atual[n]);
                out.writeLong(esp);
                out.flush();

                if (!in.readBoolean()) break;
                in.readFully(atual[0]);
                in.readFully(atual[n + 1]);

                long a = System.nanoTime();
                esp = Simulacao.passo(atual, prox, 1, n + 1, cols, limiar, prob, semente, gen, offset);
                calcNs += System.nanoTime() - a;

                byte[][] tmp = atual; atual = prox; prox = tmp;
            }

            long[] est = Simulacao.estatisticas(atual, 1, n + 1, offset, cols);
            for (long v : est) out.writeLong(v);
            out.writeLong(calcNs);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
